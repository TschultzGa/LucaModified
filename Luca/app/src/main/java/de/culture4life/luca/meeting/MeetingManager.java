package de.culture4life.luca.meeting;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.Manager;
import de.culture4life.luca.archive.Archiver;
import de.culture4life.luca.crypto.AsymmetricCipherProvider;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.crypto.EciesResult;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.TracesResponseData;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import timber.log.Timber;

public class MeetingManager extends Manager {

    public static final String KEY_CURRENT_MEETING_DATA = "current_meeting_data";
    public static final String KEY_ARCHIVED_MEETING_DATA = "archived_meeting_data";
    public static final String ALIAS_MEETING_EPHEMERAL_KEY_PAIR = "meeting_ephemeral_key_pair";

    private final PreferencesManager preferencesManager;
    private final NetworkManager networkManager;
    private final LocationManager locationManager;
    private final CryptoManager cryptoManager; // initialization deferred to first use
    private final HistoryManager historyManager;
    private final Archiver<MeetingData> archiver;

    @Nullable
    private MeetingData currentMeetingData;

    public MeetingManager(@NonNull PreferencesManager preferencesManager, @NonNull NetworkManager networkManager, @NonNull LocationManager locationManager, @NonNull HistoryManager historyManager, @NonNull CryptoManager cryptoManager) {
        this.preferencesManager = preferencesManager;
        this.networkManager = networkManager;
        this.locationManager = locationManager;
        this.historyManager = historyManager;
        this.cryptoManager = cryptoManager;
        archiver = new Archiver<>(preferencesManager, KEY_ARCHIVED_MEETING_DATA, ArchivedMeetingData.class, MeetingData::getCreationTimestamp);
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.mergeArray(
                preferencesManager.initialize(context),
                networkManager.initialize(context),
                locationManager.initialize(context),
                historyManager.initialize(context)
        ).andThen(invokeDeleteOldArchivedMeetingData());
    }

    @Override
    public void dispose() {
        archiver.clearCachedData();
        super.dispose();
    }

    public Observable<Boolean> getMeetingHostStateChanges() {
        return Observable.interval(1, TimeUnit.SECONDS)
                .flatMapSingle(tick -> isCurrentlyHostingMeeting())
                .distinctUntilChanged();
    }

    public Single<Boolean> isCurrentlyHostingMeeting() {
        return getCurrentMeetingDataIfAvailable()
                .switchIfEmpty(restoreCurrentMeetingDataIfAvailable())
                .isEmpty()
                .map(noMeetingDataAvailable -> !noMeetingDataAvailable);
    }

    public Maybe<MeetingData> getCurrentMeetingDataIfAvailable() {
        return Maybe.fromCallable(() -> currentMeetingData);
    }

    public Maybe<MeetingData> restoreCurrentMeetingDataIfAvailable() {
        return preferencesManager.restoreIfAvailable(KEY_CURRENT_MEETING_DATA, MeetingData.class)
                .doOnSuccess(meetingData -> this.currentMeetingData = meetingData);
    }

    public Completable persistCurrentMeetingData(@NonNull MeetingData meetingData) {
        return preferencesManager.persist(KEY_CURRENT_MEETING_DATA, meetingData);
    }

    /*
        Start
     */

    public Completable createPrivateMeeting() {
        return cryptoManager.initialize(context)
                .andThen(generateMeetingEphemeralKeyPair())
                .flatMapCompletable(keyPair -> createPrivateLocation((ECPublicKey) keyPair.getPublic())
                        .doOnSuccess(meetingData -> {
                            Timber.i("Created meeting data: %s", meetingData);
                            this.currentMeetingData = meetingData;
                        })
                        .flatMapCompletable(meetingData -> Completable.mergeArray(
                                persistCurrentMeetingData(meetingData),
                                persistMeetingEphemeralKeyPair(meetingData.getLocationId(), keyPair)
                        ).andThen(historyManager.addMeetingStartedItem(meetingData))));
    }

    private Single<MeetingData> createPrivateLocation(@NonNull ECPublicKey publicKey) {
        return AsymmetricCipherProvider.encode(publicKey)
                .flatMap(SerializationUtil::toBase64)
                .map(serializedPubKey -> {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("publicKey", serializedPubKey);
                    return jsonObject;
                })
                .flatMap(requestData -> networkManager.getLucaEndpointsV3()
                        .flatMap(lucaEndpointsV3 -> lucaEndpointsV3.createPrivateLocation(requestData)))
                .map(MeetingData::new);
    }

    /*
        End
     */

    public Completable closePrivateLocation() {
        return cryptoManager.initialize(context)
                .andThen(restoreCurrentMeetingDataIfAvailable())
                .flatMapCompletable(meetingData -> networkManager.getLucaEndpointsV3()
                        .flatMapCompletable(lucaEndpointsV3 -> lucaEndpointsV3.closePrivateLocation(meetingData.getAccessId().toString()))
                        .onErrorResumeNext(throwable -> {
                            if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                                // meeting has already ended
                                return Completable.complete();
                            }
                            return Completable.error(throwable);
                        })
                        .andThen(addMeetingToHistory(meetingData))
                        .andThen(addCurrentMeetingDataToArchive())
                        .andThen(deleteMeetingEphemeralKeyPair(meetingData.getLocationId())));
    }

    private Completable addMeetingToHistory(@NonNull MeetingData meetingData) {
        return historyManager.addMeetingEndedItem(meetingData);
    }

    /*
        Status
     */

    public Completable updateMeetingGuestData() {
        return cryptoManager.initialize(context)
                .andThen(fetchGuestData())
                .flatMapSingle(this::getMeetingGuestData)
                .toList()
                .flatMapCompletable(meetingGuestData -> getCurrentMeetingDataIfAvailable()
                        .doOnSuccess(meetingData -> meetingData.setGuestData(meetingGuestData))
                        .flatMapCompletable(this::persistCurrentMeetingData));
    }

    public Observable<TracesResponseData> fetchGuestData() {
        return restoreCurrentMeetingDataIfAvailable()
                .map(MeetingData::getAccessId)
                .flatMapSingle(accessUuid -> networkManager.getLucaEndpointsV3()
                        .flatMap(lucaEndpointsV3 -> lucaEndpointsV3.fetchGuestList(accessUuid.toString())))
                .doOnSuccess(tracesResponseData -> Timber.d("Location traces: %s", tracesResponseData))
                .flatMapObservable(Observable::fromIterable);
    }

    private Single<MeetingGuestData> getMeetingGuestData(@NonNull TracesResponseData tracesResponseData) {
        return Single.fromCallable(() -> {
            MeetingGuestData meetingGuestData = new MeetingGuestData();
            meetingGuestData.setTraceId(tracesResponseData.getTraceId());

            try {
                TracesResponseData.AdditionalData additionalData = tracesResponseData.getAdditionalData();
                if (additionalData == null) {
                    throw new IllegalStateException("No additional data available for " + tracesResponseData.getTraceId());
                }

                PrivateKey meetingPrivateKey = getCurrentMeetingDataIfAvailable()
                        .toSingle()
                        .map(MeetingData::getLocationId)
                        .flatMap(this::getMeetingEphemeralKeyPair)
                        .map(KeyPair::getPrivate)
                        .blockingGet();

                ECPublicKey guestPublicKey = SerializationUtil.fromBase64(additionalData.getPublicKey())
                        .flatMap(AsymmetricCipherProvider::decodePublicKey)
                        .blockingGet();

                EciesResult eciesResult = new EciesResult(
                        SerializationUtil.fromBase64(additionalData.getData()).blockingGet(),
                        SerializationUtil.fromBase64(additionalData.getIv()).blockingGet(),
                        SerializationUtil.fromBase64(additionalData.getMac()).blockingGet(),
                        guestPublicKey
                );

                byte[] decryptedData = cryptoManager.eciesDecrypt(eciesResult, meetingPrivateKey).blockingGet();

                MeetingAdditionalData meetingAdditionalData = Single.fromCallable(() -> new String(decryptedData, StandardCharsets.UTF_8))
                        .doOnSuccess(json -> Timber.d("Additional data JSON: %s", json))
                        .map(json -> new Gson().fromJson(json, MeetingAdditionalData.class))
                        .blockingGet();

                meetingGuestData.setFirstName(meetingAdditionalData.getFirstName());
                meetingGuestData.setLastName(meetingAdditionalData.getLastName());
            } catch (Exception e) {
                Timber.w("Unable to extract guest names from additional data: %s", e.toString());
            }

            long checkInTimestamp = TimeUtil.convertFromUnixTimestamp(tracesResponseData.getCheckInTimestamp()).blockingGet();
            meetingGuestData.setCheckInTimestamp(checkInTimestamp);

            long checkOutTimestamp = TimeUtil.convertFromUnixTimestamp(tracesResponseData.getCheckOutTimestampOrZero()).blockingGet();
            meetingGuestData.setCheckOutTimestamp(checkOutTimestamp);

            return meetingGuestData;
        });
    }

    /*
        Archive
     */

    public Completable addCurrentMeetingDataToArchive() {
        return restoreCurrentMeetingDataIfAvailable()
                .flatMapCompletable(this::addMeetingDataToArchive)
                .andThen(preferencesManager.delete(KEY_CURRENT_MEETING_DATA))
                .doOnComplete(() -> this.currentMeetingData = null);
    }

    public Completable addMeetingDataToArchive(@NonNull MeetingData meetingData) {
        return archiver.addData(meetingData);
    }

    private Completable invokeDeleteOldArchivedMeetingData() {
        return invokeDelayed(deleteOldArchivedMeetingData(), TimeUnit.SECONDS.toMillis(3));
    }

    public Completable deleteOldArchivedMeetingData() {
        return archiver.deleteDataOlderThan()
                .doOnComplete(() -> Timber.d("Deleted old archived meeting data"));
    }

    /*
        Meeting ephemeral key pair
     */

    protected Single<KeyPair> getMeetingEphemeralKeyPair(@NonNull UUID meetingId) {
        return getMeetingEphemeralKeyPairAlias(meetingId)
                .flatMap(cryptoManager::getKeyPair);
    }

    protected Single<KeyPair> generateMeetingEphemeralKeyPair() {
        return cryptoManager.generateKeyPair(ALIAS_MEETING_EPHEMERAL_KEY_PAIR)
                .doOnSuccess(keyPair -> Timber.d("Generated new meeting ephemeral key pair: %s", keyPair.getPublic()));
    }

    protected Completable persistMeetingEphemeralKeyPair(@NonNull UUID meetingId, @NonNull KeyPair keyPair) {
        return getMeetingEphemeralKeyPairAlias(meetingId)
                .flatMapCompletable(alias -> cryptoManager.persistKeyPair(alias, keyPair));
    }

    protected Completable deleteMeetingEphemeralKeyPair(@NonNull UUID meetingId) {
        return getMeetingEphemeralKeyPairAlias(meetingId)
                .flatMapCompletable(cryptoManager::deleteKeyPair);
    }

    protected static Single<String> getMeetingEphemeralKeyPairAlias(@NonNull UUID meetingId) {
        return Single.just(ALIAS_MEETING_EPHEMERAL_KEY_PAIR + "-" + meetingId.toString());
    }

    /*
        Utilities
     */

    public static String getReadableGuestName(@NonNull MeetingGuestData guestData) {
        String name;
        if (guestData.getFirstName() != null) {
            name = guestData.getFirstName();
        } else {
            // this can happen if the guest already checked in but
            // hasn't uploaded the additional data (containing the name) yet
            name = "Trace ID " + guestData.getTraceId().substring(0, 8);
        }
        return name;
    }

    public static boolean isPrivateMeeting(@NonNull String url) {
        return url.contains("luca-app.de/webapp/meeting");
    }

}
