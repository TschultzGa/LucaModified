package de.culture4life.luca.checkin;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static de.culture4life.luca.crypto.HashProvider.TRIMMED_HASH_LENGTH;
import static de.culture4life.luca.location.GeofenceManager.MAXIMUM_GEOFENCE_RADIUS;
import static de.culture4life.luca.location.GeofenceManager.MINIMUM_GEOFENCE_RADIUS;
import static de.culture4life.luca.location.GeofenceManager.UPDATE_INTERVAL_DEFAULT;
import static de.culture4life.luca.notification.LucaNotificationManager.NOTIFICATION_ID_CHECKOUT_TRIGGERED;
import static de.culture4life.luca.util.SerializationUtil.toBase64;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.Manager;
import de.culture4life.luca.R;
import de.culture4life.luca.archive.Archiver;
import de.culture4life.luca.crypto.AsymmetricCipherProvider;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.crypto.EciesResult;
import de.culture4life.luca.crypto.TraceIdWrapper;
import de.culture4life.luca.crypto.TraceIdWrapperList;
import de.culture4life.luca.crypto.WrappedSecret;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.location.GeofenceException;
import de.culture4life.luca.location.GeofenceManager;
import de.culture4life.luca.location.LocationManager;
import de.culture4life.luca.meeting.MeetingAdditionalData;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.AdditionalCheckInPropertiesRequestData;
import de.culture4life.luca.network.pojo.CheckInRequestData;
import de.culture4life.luca.network.pojo.CheckOutRequestData;
import de.culture4life.luca.network.pojo.LocationResponseData;
import de.culture4life.luca.network.pojo.TraceData;
import de.culture4life.luca.network.pojo.TraceDeletionRequestData;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.ui.checkin.QrCodeData;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Facilitates check-in to a venues either by having a shown barcode scanned or scanning a printed
 * QR-code.
 *
 * @see <a href="https://www.luca-app.de/securityoverview/processes/guest_app_checkin.html">Security
 * Overview: Check-In via Mobile Phone App</a>
 * @see <a href="https://www.luca-app.de/securityoverview/processes/guest_self_checkin.html">Security
 * Overview: Check-In via a Printed QR Code</a>
 */
public class CheckInManager extends Manager {

    private static final String KEY_CHECK_IN_DATA = "check_in_data_2";
    public static final String KEY_ARCHIVED_CHECK_IN_DATA = "archived_check_in_data";
    private static final String KEY_ADDITIONAL_CHECK_IN_PROPERTIES_DATA = "additional_check_in_properties";
    private static final String KEY_LAST_CHECK_IN_DATA_UPDATE_TIMESTAMP = "last_check_in_data_update_timestamp";
    private static final String KEY_TRACE_ID_WRAPPERS = "tracing_id_wrappers";
    private static final String KEY_PREFIX_TRACING_SECRET = "tracing_secret_";
    private static final String ALIAS_GUEST_EPHEMERAL_KEY_PAIR = "user_ephemeral_key_pair";
    private static final String ALIAS_SCANNER_EPHEMERAL_KEY_PAIR = "scanner_ephemeral_key_pair";
    public static final String KEY_INCLUDE_ENTRY_POLICY = "include_entry_policy";
    public static final String KEY_ENABLE_AUTOMATIC_CHECK_OUT = "enable_automatic_check_out";

    private static final String CHECK_IN_DATA_UPDATE_TAG = "check_in_update";
    private static final long CHECK_IN_DATA_UPDATE_INTERVAL = BuildConfig.DEBUG ? PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS : TimeUnit.HOURS.toMillis(6);
    private static final long CHECK_IN_DATA_UPDATE_FLEX_PERIOD = BuildConfig.DEBUG ? PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS : TimeUnit.HOURS.toMillis(1);
    private static final long CHECK_IN_DATA_UPDATE_INITIAL_DELAY = TimeUnit.SECONDS.toMillis(10);

    private static final long MINIMUM_CHECK_IN_DURATION = TimeUnit.MINUTES.toMillis(2);
    private static final long LOCATION_REQUEST_TIMEOUT = TimeUnit.SECONDS.toMillis(3);
    private static final int RECENT_TRACE_IDS_LIMIT = (int) TimeUnit.HOURS.toMinutes(6);
    private static final long MAXIMUM_YOUNGER_TRACE_ID_AGE = TimeUnit.MINUTES.toMillis(2);
    private static final long CHECK_OUT_POLLING_INTERVAL = TimeUnit.MINUTES.toMillis(1);
    private static final long AUTOMATIC_CHECK_OUT_RETRY_DELAY = BuildConfig.DEBUG ? TimeUnit.SECONDS.toMillis(15) : TimeUnit.MINUTES.toMillis(2);

    private final PreferencesManager preferencesManager;
    private final NetworkManager networkManager;
    private final GeofenceManager geofenceManager;
    private final LocationManager locationManager;
    private final CryptoManager cryptoManager; // initialization deferred to first use
    private final HistoryManager historyManager;
    private final LucaNotificationManager notificationManager;
    private final GenuinityManager genuinityManager;
    private final Archiver<CheckInData> archiver;

    private boolean skipMinimumCheckInDurationAssertion;
    private boolean skipMinimumDistanceAssertion;

    @Nullable
    private MeetingAdditionalData meetingAdditionalData;

    @Nullable
    private JsonObject additionalCheckInProperties;

    @Nullable
    private CheckInData checkInData;

    @Nullable
    private GeofencingRequest autoCheckoutGeofenceRequest;

    @Nullable
    private Disposable automaticCheckoutDisposable;

    @Nullable
    private Disposable checkOutReminderDisposable;

    private WorkManager workManager;

    public CheckInManager(
            @NonNull PreferencesManager preferencesManager,
            @NonNull NetworkManager networkManager,
            @NonNull GeofenceManager geofenceManager,
            @NonNull LocationManager locationManager,
            @NonNull HistoryManager historyManager,
            @NonNull CryptoManager cryptoManager,
            @NonNull LucaNotificationManager notificationManager,
            @NonNull GenuinityManager genuinityManager
    ) {
        this.preferencesManager = preferencesManager;
        this.networkManager = networkManager;
        this.geofenceManager = geofenceManager;
        this.locationManager = locationManager;
        this.historyManager = historyManager;
        this.cryptoManager = cryptoManager;
        this.notificationManager = notificationManager;
        this.genuinityManager = genuinityManager;
        archiver = new Archiver<>(preferencesManager, KEY_ARCHIVED_CHECK_IN_DATA, ArchivedCheckInData.class, CheckInData::getTimestamp);

        skipMinimumDistanceAssertion = true;
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.mergeArray(
                preferencesManager.initialize(context),
                networkManager.initialize(context),
                geofenceManager.initialize(context),
                locationManager.initialize(context),
                historyManager.initialize(context),
                notificationManager.initialize(context)
        ).andThen(Completable.fromAction(() -> {
            if (!LucaApplication.isRunningUnitTests()) {
                this.workManager = WorkManager.getInstance(context);
            }
        })).andThen(Completable.mergeArray(
                invokeInitializeCheckInData(),
                invokeStartUpdatingCheckInDataInRegularIntervals(),
                invokeDeleteOldArchivedCheckInData()
        ));
    }

    @Override
    public void dispose() {
        archiver.clearCachedData();
        super.dispose();
    }

    /*
        Check-in data updates
     */

    private Completable invokeInitializeCheckInData() {
        return invoke(preferencesManager.restoreIfAvailable(KEY_CHECK_IN_DATA, CheckInData.class)
                .doOnSuccess(restoredCheckInData -> this.checkInData = restoredCheckInData)
                .ignoreElement()
                .andThen(enableCheckOutReminderNotification()));
    }

    private Completable invokeStartUpdatingCheckInDataInRegularIntervals() {
        return invokeDelayed(startUpdatingCheckInDataInRegularIntervals(), CHECK_IN_DATA_UPDATE_INITIAL_DELAY);
    }

    private Completable startUpdatingCheckInDataInRegularIntervals() {
        return getNextRecommendedCheckInDataUpdateDelay()
                .flatMapCompletable(initialDelay -> Completable.fromAction(() -> {
                    if (workManager == null) {
                        managerDisposable.add(updateCheckInDataIfNecessary(CHECK_IN_DATA_UPDATE_INTERVAL, true)
                                .delaySubscription(initialDelay, TimeUnit.MILLISECONDS)
                                .subscribeOn(Schedulers.io())
                                .subscribe());
                    } else {
                        Constraints constraints = new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build();

                        WorkRequest updateWorkRequest = new PeriodicWorkRequest.Builder(
                                CheckInUpdateWorker.class,
                                CHECK_IN_DATA_UPDATE_INTERVAL, TimeUnit.MILLISECONDS,
                                CHECK_IN_DATA_UPDATE_FLEX_PERIOD, TimeUnit.MILLISECONDS
                        ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                                .setConstraints(constraints)
                                .addTag(CHECK_IN_DATA_UPDATE_TAG)
                                .build();

                        workManager.cancelAllWorkByTag(CHECK_IN_DATA_UPDATE_TAG);
                        workManager.enqueue(updateWorkRequest);
                        Timber.d("Update work request submitted to work manager");
                    }
                }));
    }

    /**
     * Checking in using a scanner doesn't require the device to be online, nevertheless the backend
     * is polled regularly in an attempt to provide visual feedback of a successful check-in.
     *
     * @param interval to poll backend at (millis)
     * @see <a href="https://luca-app.de/securityoverview/processes/guest_app_checkin.html#qr-code-scanning-feedback">Security
     * Overview: QR Code Scanning Feedback</a>
     */
    public Completable updateCheckInDataIfNecessary(long interval, boolean useOlderTraceIds) {
        return Observable.interval(0, interval, TimeUnit.MILLISECONDS, Schedulers.io())
                .flatMapCompletable(tick -> updateCheckInDataIfNecessary(useOlderTraceIds)
                        .onErrorComplete())
                .doOnSubscribe(disposable -> Timber.d(
                        "Starting to request check-in data updates. interval = [%d], useOlderTraceIds = [%b]",
                        interval, useOlderTraceIds
                ))
                .doFinally(() -> Timber.d(
                        "Stopped requesting check-in data updates. interval = [%d], useOlderTraceIds = [%b]",
                        interval, useOlderTraceIds
                ));
    }

    public Completable updateCheckInDataIfNecessary(boolean useOlderTraceIds) {
        return hasRecentTraceIds(useOlderTraceIds)
                .flatMapCompletable(hasRecentTraceIds ->
                        hasRecentTraceIds ? updateCheckInData(useOlderTraceIds) : Completable.complete());
    }

    public Completable updateCheckInData(boolean useOlderTraceIds) {
        return fetchCheckInDataFromBackend(useOlderTraceIds)
                .filter(current -> {
                    if (checkInData != null) {
                        return !checkInData.getTraceId().equals(current.getTraceId());
                    } else {
                        return true;
                    }
                })
                .flatMapCompletable(this::processCheckIn)
                .andThen(preferencesManager.persist(KEY_LAST_CHECK_IN_DATA_UPDATE_TIMESTAMP, TimeUtil.getCurrentMillis()))
                .doOnSubscribe(disposable -> Timber.v("Updating check-in data. useOlderTraceIds = [%b]", useOlderTraceIds))
                .doOnComplete(() -> Timber.v("Check-in data update complete"))
                .doOnError(throwable -> Timber.w("Check-in data update failed: %s", throwable.toString()));
    }

    public Single<Long> getDurationSinceLastCheckInDataUpdate() {
        return preferencesManager.restoreOrDefault(KEY_LAST_CHECK_IN_DATA_UPDATE_TIMESTAMP, 0L)
                .map(lastUpdateTimestamp -> TimeUtil.getCurrentMillis() - lastUpdateTimestamp);
    }

    public Single<Long> getNextRecommendedCheckInDataUpdateDelay() {
        return getDurationSinceLastCheckInDataUpdate()
                .map(durationSinceLastUpdate -> CHECK_IN_DATA_UPDATE_INTERVAL - durationSinceLastUpdate)
                .map(recommendedDelay -> Math.max(0, recommendedDelay))
                .doOnSuccess(recommendedDelay -> {
                    String readableDelay = TimeUtil.getReadableDurationWithPlural(recommendedDelay, context).blockingGet();
                    Timber.v("Recommended update delay: %s", readableDelay);
                });
    }

    /*
        Check-in
     */

    /**
     * Perform self check-in, generating the check-in data locally and uploading it to the luca
     * server.
     *
     * @see <a href="https://www.luca-app.de/securityoverview/processes/guest_self_checkin.html">Security
     * Overview: Check-In via a Printed QR Code</a>
     */
    public Completable checkIn(@NonNull UUID scannerId, @NonNull QrCodeData qrCodeData) {
        Timber.i("checkIn %s", scannerId.toString());
        return assertNotCheckedIn()
                .andThen(generateCheckInData(qrCodeData, scannerId)
                        .flatMapCompletable(checkInRequestData -> networkManager.getLucaEndpointsV3()
                                .flatMapCompletable(lucaEndpointsV3 -> lucaEndpointsV3.checkIn(checkInRequestData))))
                .andThen(updateCheckInData(false));
    }

    /**
     * Should be called after a check-in occurred (either triggered by the user or in the backend).
     */
    private Completable processCheckIn(@NonNull CheckInData checkInData) {
        return Completable.fromAction(() -> this.checkInData = checkInData)
                .andThen(preferencesManager.containsKey(KEY_CHECK_IN_DATA))
                .flatMap(oldCheckInDataAvailable -> Single.defer(() -> {
                    if (oldCheckInDataAvailable) {
                        return preferencesManager.restore(KEY_CHECK_IN_DATA, CheckInData.class)
                                .map(oldCheckInData -> !oldCheckInData.getTraceId().equals(checkInData.getTraceId()));
                    } else {
                        return Single.just(true);
                    }
                }))
                .flatMapCompletable(isNewCheckIn -> Completable.defer(() -> {
                    if (isNewCheckIn) {
                        return addCheckInDataToArchive(checkInData)
                                .andThen(historyManager.addCheckInItem(checkInData));
                    } else {
                        return Completable.complete();
                    }
                }))
                .andThen(persistCheckInData(checkInData))
                .andThen(enableCheckOutReminderNotification());
    }

    public Single<ECPublicKey> getLocationPublicKey(@NonNull UUID scannerId) {
        return networkManager.getLucaEndpointsV3()
                .flatMap(lucaEndpointsV3 -> lucaEndpointsV3.getScanner(scannerId.toString()))
                .map(jsonObject -> jsonObject.get("publicKey").getAsString())
                .flatMap(SerializationUtil::fromBase64)
                .flatMap(AsymmetricCipherProvider::decodePublicKey);
    }

    private Single<CheckInRequestData> generateCheckInData(@NonNull QrCodeData qrCodeData, @NonNull UUID scannerId) {
        Timber.i("GenerateCheckInData for %s", scannerId.toString());
        return getLocationPublicKey(scannerId)
                .flatMap(locationPublicKey -> generateCheckInData(qrCodeData, locationPublicKey))
                .doOnSuccess(checkInRequestData -> checkInRequestData.setScannerId(scannerId.toString()));
    }

    /**
     * Generate data required for checking in. The encrypted contact data is encrypted a second time
     * using the venue's key.
     *
     * @see <a href="https://www.luca-app.de/securityoverview/processes/guest_self_checkin.html">Security
     * Overview: Check-In via a Printed QR Code</a>
     */
    private Single<CheckInRequestData> generateCheckInData(@NonNull QrCodeData qrCodeData, @NonNull PublicKey locationPublicKey) {
        Timber.i("GeneratecheckInData %s", locationPublicKey.toString());
        return cryptoManager.initialize(context)
                .andThen(Single.fromCallable(() -> {
                    CheckInRequestData requestData = new CheckInRequestData();

                    requestData.setDeviceType(qrCodeData.getDeviceType());

                    long timestamp = TimeUtil.decodeUnixTimestamp(qrCodeData.getTimestamp())
                            .flatMap(TimeUtil::roundUnixTimestampDownToMinute).blockingGet();
                    requestData.setUnixTimestamp(timestamp);

                    String serialisedTraceId = toBase64(qrCodeData.getTraceId()).blockingGet();
                    requestData.setTraceId(serialisedTraceId);

                    KeyPair scannerEphemeralKeyPair = cryptoManager.generateKeyPair(ALIAS_SCANNER_EPHEMERAL_KEY_PAIR).blockingGet();

                    String serializedScannerPublicKey = AsymmetricCipherProvider.encode((ECPublicKey) scannerEphemeralKeyPair.getPublic())
                            .flatMap(SerializationUtil::toBase64)
                            .blockingGet();
                    requestData.setScannerEphemeralPublicKey(serializedScannerPublicKey);

                    String serializedGuestPublicKey = getGuestEphemeralKeyPairAlias(qrCodeData.getTraceId())
                            .flatMap(cryptoManager::getKeyPairPublicKey)
                            .flatMap(AsymmetricCipherProvider::encode)
                            .flatMap(SerializationUtil::toBase64)
                            .blockingGet();
                    requestData.setGuestEphemeralPublicKey(serializedGuestPublicKey);

                    byte[] encodedQrCodeData = ByteBuffer.allocate(75)
                            .put((byte) 3)
                            .put(qrCodeData.getKeyId())
                            .put(qrCodeData.getUserEphemeralPublicKey())
                            .put(qrCodeData.getVerificationTag())
                            .put(qrCodeData.getEncryptedData())
                            .array();

                    EciesResult eciesResult = cryptoManager.eciesEncrypt(encodedQrCodeData, scannerEphemeralKeyPair, locationPublicKey).blockingGet();
                    requestData.setReEncryptedQrCodeData(toBase64(eciesResult.getEncryptedData()).blockingGet());
                    requestData.setMac(toBase64(eciesResult.getMac()).blockingGet());
                    requestData.setIv(toBase64(eciesResult.getIv()).blockingGet());

                    return requestData;
                }));
    }

    public Single<Boolean> isCheckedIn() {
        // TODO: 06.01.22 refactor to subject
        return getCheckInDataIfAvailable()
                .isEmpty()
                .map(notCheckedIn -> !notCheckedIn);
    }

    public Completable assertCheckedIn() {
        return isCheckedIn()
                .flatMapCompletable(isCheckedIn -> {
                    if (isCheckedIn) {
                        return Completable.complete();
                    } else {
                        return Completable.error(new IllegalStateException("Not currently checked in, need to check in first"));
                    }
                });
    }

    public Completable assertNotCheckedIn() {
        return isCheckedIn()
                .flatMapCompletable(isCheckedIn -> {
                    if (isCheckedIn) {
                        return Completable.error(new IllegalStateException("Already checked in, need to checkout first"));
                    } else {
                        return Completable.complete();
                    }
                });
    }

    public Single<Boolean> isCheckedInToPrivateMeeting() {
        return getCheckInDataIfAvailable()
                .map(CheckInData::isPrivateMeeting)
                .defaultIfEmpty(false);
    }

    public Completable assertCheckedInToPrivateMeeting() {
        return assertCheckedIn()
                .andThen(isCheckedInToPrivateMeeting())
                .flatMapCompletable(inPrivateMeeting -> {
                    if (inPrivateMeeting) {
                        return Completable.complete();
                    } else {
                        return Completable.error(new IllegalStateException("Check-in data does not belong to a private meeting"));
                    }
                });
    }

    public Observable<Boolean> getCheckedInStateChanges() {
        return Observable.interval(1, TimeUnit.SECONDS)
                .flatMapSingle(tick -> isCheckedIn())
                .distinctUntilChanged();
    }

    public Single<Boolean> isCheckedInAtBackend(boolean useOlderTraceIds) {
        return getTraceDataFromBackend(useOlderTraceIds)
                .flatMap(traceData -> TimeUtil.convertFromUnixTimestamp(traceData.getCheckOutTimestamp())
                        .toMaybe()
                        .map(checkoutTimestamp -> checkoutTimestamp == 0 || checkoutTimestamp > TimeUtil.getCurrentMillis()))
                .defaultIfEmpty(false)
                .doOnSubscribe(disposable -> Timber.d("Requesting check-in status from backend"));
    }

    /*
        Additional check-in properties
     */

    public Maybe<JsonObject> getAdditionalCheckInPropertiesIfAvailable() {
        return Maybe.fromCallable(() -> additionalCheckInProperties);
    }

    public Observable<JsonObject> getAdditionalCheckInPropertiesAndChanges() {
        return preferencesManager.restoreIfAvailableAndGetChanges(KEY_ADDITIONAL_CHECK_IN_PROPERTIES_DATA, JsonObject.class)
                .doOnNext(properties -> {
                    Timber.v("Additional check-in properties updated from preferences: %s", properties);
                    this.additionalCheckInProperties = properties;
                });
    }

    public Completable persistAdditionalCheckInProperties(@NonNull JsonObject properties) {
        return preferencesManager.persist(KEY_ADDITIONAL_CHECK_IN_PROPERTIES_DATA, properties);
    }

    public Completable removeAdditionalCheckInProperties() {
        return preferencesManager.delete(KEY_ADDITIONAL_CHECK_IN_PROPERTIES_DATA)
                .doOnComplete(() -> this.additionalCheckInProperties = null);
    }

    public Completable addAdditionalCheckInProperties(@NonNull JsonObject properties, @NonNull PublicKey locationPublicKey) {
        return assertCheckedIn()
                .andThen(getCheckedInTraceId())
                .toSingle()
                .flatMap(traceId -> generateAdditionalCheckInProperties(properties, traceId, locationPublicKey))
                .flatMapCompletable(requestData -> networkManager.getLucaEndpointsV3()
                        .flatMapCompletable(lucaEndpointsV3 -> lucaEndpointsV3.addAdditionalCheckInProperties(requestData)))
                .andThen(persistAdditionalCheckInProperties(properties));
    }

    private Single<AdditionalCheckInPropertiesRequestData> generateAdditionalCheckInProperties(@NonNull JsonObject properties, @NonNull byte[] traceId, @NonNull PublicKey locationPublicKey) {
        return cryptoManager.initialize(context)
                .andThen(Single.fromCallable(() -> {
                    AdditionalCheckInPropertiesRequestData requestData = new AdditionalCheckInPropertiesRequestData();

                    String serializedTraceId = toBase64(traceId).blockingGet();
                    requestData.setTraceId(serializedTraceId);

                    KeyPair scannerEphemeralKeyPair = cryptoManager.getKeyPair(ALIAS_SCANNER_EPHEMERAL_KEY_PAIR).blockingGet();
                    String serializedScannerPublicKey = AsymmetricCipherProvider.encode((ECPublicKey) scannerEphemeralKeyPair.getPublic())
                            .flatMap(SerializationUtil::toBase64).blockingGet();
                    requestData.setScannerPublicKey(serializedScannerPublicKey);

                    byte[] encodedProperties = new Gson().toJson(properties).getBytes(StandardCharsets.UTF_8);
                    EciesResult eciesResult = cryptoManager.eciesEncrypt(encodedProperties, scannerEphemeralKeyPair, locationPublicKey).blockingGet();
                    requestData.setEncryptedProperties(toBase64(eciesResult.getEncryptedData()).blockingGet());
                    requestData.setMac(toBase64(eciesResult.getMac()).blockingGet());
                    requestData.setIv(toBase64(eciesResult.getIv()).blockingGet());

                    return requestData;
                }));
    }

    /*
        Check-out
     */

    /**
     * Perform check-out, uploading trace ID and checkout time to the luca server.
     *
     * @see <a href="https://www.luca-app.de/securityoverview/processes/guest_checkout.html#checkout-process">Security
     * Overview: Checkout Process</a>
     */
    @SuppressLint("MissingPermission")
    public Completable checkOut() {
        return assertCheckedIn()
                .andThen(assertMinimumCheckInDuration())
                .andThen(assertMinimumDistanceToLocation())
                .andThen(networkManager.assertNetworkConnected())
                .andThen(generateCheckOutData()
                        .doOnSuccess(checkOutRequestData -> Timber.i("Generated checkout data: %s", checkOutRequestData))
                        .flatMapCompletable(checkOutRequestData -> networkManager.getLucaEndpointsV3()
                                .flatMapCompletable(lucaEndpointsV3 -> lucaEndpointsV3.checkOut(checkOutRequestData))
                                .onErrorResumeNext(throwable -> {
                                    if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                                        // user is currently not checked-in
                                        return Completable.complete();
                                    }
                                    return Completable.error(throwable);
                                }))
                        .onErrorResumeNext(throwable -> removeCheckInDataIfCheckedOut()
                                .andThen(Completable.error(throwable))))
                .andThen(processCheckOut())
                .doOnSubscribe(disposable -> Timber.d("Initiating checkout"))
                .doOnComplete(() -> Timber.i("Successfully checked out"));
    }

    /**
     * Should be called after a check-out occurred (either triggered by the user or in the
     * backend).
     */
    private Completable processCheckOut() {
        return getCheckInDataIfAvailable()
                .flatMapCompletable(historyManager::addCheckOutItem)
                .andThen(removeCheckInData())
                .andThen(removeAdditionalCheckInProperties())
                .andThen(disableAutomaticCheckOut())
                .andThen(disableCheckOutReminderNotification());
    }

    /**
     * Create check-out data of current trace ID and the current timestamp.
     *
     * @see <a href="https://www.luca-app.de/securityoverview/processes/guest_checkout.html#checkout-process">Security
     * Overview: Checkout Process</a>
     */
    private Single<CheckOutRequestData> generateCheckOutData() {
        return Single.just(new CheckOutRequestData())
                .flatMap(checkOutRequestData -> getCheckedInTraceId()
                        .toSingle()
                        .flatMap(traceId -> Completable.mergeArray(
                                SerializationUtil.toBase64(traceId)
                                        .doOnSuccess(checkOutRequestData::setTraceId)
                                        .ignoreElement(),
                                TimeUtil.getCurrentUnixTimestamp()
                                        .flatMap(TimeUtil::roundUnixTimestampDownToMinute)
                                        .doOnSuccess(checkOutRequestData::setRoundedUnixTimestamp)
                                        .ignoreElement()
                        ).toSingle(() -> checkOutRequestData)));
    }

    @RequiresPermission("android.permission.ACCESS_FINE_LOCATION")
    public Completable enableAutomaticCheckOut() {
        return Completable.fromAction(() -> {
            if (automaticCheckoutDisposable != null && !automaticCheckoutDisposable.isDisposed()) {
                automaticCheckoutDisposable.dispose();
            }
            automaticCheckoutDisposable = createAutoCheckoutGeofenceRequest()
                    .flatMapObservable(geofenceManager::getGeofenceEvents)
                    .firstElement()
                    .ignoreElement()
                    .andThen(performAutomaticCheckout())
                    .doOnError(throwable -> Timber.w("Unable to perform automatic check-out: %s", throwable.toString()))
                    .retryWhen(errors -> errors
                            .doOnNext(throwable -> Timber.v("Retrying automatic check-out in %d seconds", TimeUnit.MILLISECONDS.toSeconds(AUTOMATIC_CHECK_OUT_RETRY_DELAY)))
                            .delay(AUTOMATIC_CHECK_OUT_RETRY_DELAY, TimeUnit.MILLISECONDS, Schedulers.io()))
                    .subscribeOn(Schedulers.io())
                    .subscribe();
            managerDisposable.add(automaticCheckoutDisposable);
        });
    }

    public Completable disableAutomaticCheckOut() {
        return Maybe.fromCallable(() -> autoCheckoutGeofenceRequest)
                .flatMapCompletable(geofenceManager::removeGeofences)
                .andThen(Completable.fromAction(() -> {
                    if (automaticCheckoutDisposable != null && !automaticCheckoutDisposable.isDisposed()) {
                        automaticCheckoutDisposable.dispose();
                    }
                }))
                .doOnComplete(() -> autoCheckoutGeofenceRequest = null);
    }

    public Maybe<GeofencingRequest> getAutoCheckoutGeofenceRequest() {
        return Maybe.fromCallable(() -> autoCheckoutGeofenceRequest);
    }

    public Single<Boolean> isAutomaticCheckoutEnabled() {
        return getAutoCheckoutGeofenceRequest()
                .map(geofencingRequest -> true)
                .defaultIfEmpty(false);
    }

    private Completable performAutomaticCheckout() {
        return checkOut()
                .andThen(showAutomaticCheckoutNotification())
                .andThen(Completable.fromAction(() -> {
                    ((LucaApplication) context.getApplicationContext()).stopIfNotCurrentlyActive();
                }));
    }

    private Completable showAutomaticCheckoutNotification() {
        return Completable.fromAction(() -> {
            NotificationCompat.Builder notificationBuilder = notificationManager.createEventNotificationBuilder(
                    notificationManager.createOpenAppIntent(),
                    context.getString(R.string.notification_auto_checkout_triggered_title),
                    context.getString(R.string.notification_auto_checkout_triggered_description)
            );
            notificationManager.showNotification(NOTIFICATION_ID_CHECKOUT_TRIGGERED, notificationBuilder.build())
                    .subscribe();
        });
    }

    private Single<GeofencingRequest> createAutoCheckoutGeofenceRequest() {
        return getCheckInDataIfAvailable()
                .toSingle()
                .flatMap(this::createGeofenceBuilder)
                .map(geofenceBuilder -> new GeofencingRequest.Builder()
                        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
                        .addGeofence(geofenceBuilder.build())
                        .build())
                .doOnSuccess(geofencingRequest -> autoCheckoutGeofenceRequest = geofencingRequest);
    }

    private Single<Geofence.Builder> createGeofenceBuilder(@NonNull CheckInData checkInData) {
        return Single.defer(() -> {
            if (!checkInData.hasLocationRestriction()) {
                return Single.error(new GeofenceException("Location restriction not available for check-in data"));
            }

            long radius = checkInData.getRadius();
            radius = Math.max(MINIMUM_GEOFENCE_RADIUS, radius);
            radius = Math.min(MAXIMUM_GEOFENCE_RADIUS, radius);

            return Single.just(new Geofence.Builder()
                    .setRequestId(checkInData.getLocationId().toString().toLowerCase(Locale.ROOT))
                    .setCircularRegion(checkInData.getLatitude(), checkInData.getLongitude(), radius)
                    .setNotificationResponsiveness((int) UPDATE_INTERVAL_DEFAULT)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT));
        });
    }

    /**
     * If currently checked in, this will poll the backend and check the check-in status. If the
     * status changes, this will trigger a checkout.
     */
    public Completable monitorCheckOutAtBackend() {
        return Observable.interval(0, CHECK_OUT_POLLING_INTERVAL, TimeUnit.MILLISECONDS, Schedulers.io())
                .flatMapCompletable(tick -> isCheckedIn()
                        .filter(Boolean::booleanValue)
                        .flatMapCompletable(isCheckedIn -> checkOutIfNotCheckedInAtBackend())
                        .doOnError(throwable -> Timber.w("Unable to monitor backend check-out: %s", throwable.toString()))
                        .onErrorComplete());
    }

    public Completable checkOutIfNotCheckedInAtBackend() {
        return isCheckedInAtBackend(false)
                .filter(isCheckedIn -> !isCheckedIn)
                .flatMapCompletable(isCheckedOut -> processCheckOut());
    }

    public Completable enableCheckOutReminderNotification() {
        return isCheckedIn()
                .filter(isCheckedIn -> isCheckedIn)
                .ignoreElement()
                .andThen(disableCheckOutReminderNotification())
                .andThen(calculateDurationToCheckOutReminder())
                .flatMapCompletable(delay -> Completable.fromAction(() ->
                        checkOutReminderDisposable = Completable.timer(delay, TimeUnit.MILLISECONDS, Schedulers.io())
                                .andThen(showCheckOutReminderNotification())
                                .doOnError(throwable -> Timber.w("Could not show check-out reminder: %s", throwable.toString()))
                                .onErrorComplete()
                                .subscribe(() -> Timber.d("Show check-out reminder in %s", TimeUtil.getReadableDurationWithPlural(delay, context).blockingGet()))
                ));
    }

    public Completable disableCheckOutReminderNotification() {
        return Completable.fromAction(() -> {
            if (checkOutReminderDisposable != null && !checkOutReminderDisposable.isDisposed()) {
                checkOutReminderDisposable.dispose();
            }
        });
    }

    @NonNull
    private Completable showCheckOutReminderNotification() {
        return notificationManager.showNotificationUntilDisposed(
                LucaNotificationManager.NOTIFICATION_ID_CHECKOUT_REMINDER,
                notificationManager.createCheckOutReminderNotificationBuilder().build()
        );
    }

    private Maybe<Long> calculateDurationToCheckOutReminder() {
        return Maybe.zip(getAverageCheckInDurationIfAvailable(), getCurrentCheckInDuration(),
                (averageCheckInDuration, currentCheckInDuration) -> averageCheckInDuration - currentCheckInDuration);
    }

    /*
        Deletion
     */

    public Completable deleteCheckInFromBackend(@NonNull String traceId) {
        return generateDeletionRequestData(traceId)
                .flatMapCompletable(requestData -> networkManager.getLucaEndpointsV3()
                        .flatMapCompletable(lucaEndpointsV3 -> lucaEndpointsV3.deleteTrace(requestData)))
                .andThen(deleteCheckInLocally(traceId));
    }

    private Single<TraceDeletionRequestData> generateDeletionRequestData(@NonNull String traceId) {
        return cryptoManager.initialize(context)
                .andThen(Single.fromCallable(() -> {
                    byte[] traceIdBytes = SerializationUtil.fromBase64(traceId).blockingGet();
                    PrivateKey privateKey = getGuestEphemeralKeyPairAlias(traceIdBytes)
                            .flatMap(cryptoManager::getKeyPairPrivateKey)
                            .blockingGet();

                    long timestamp = TimeUtil.getCurrentUnixTimestamp().blockingGet();
                    byte[] timestampBytes = TimeUtil.encodeUnixTimestamp(timestamp).blockingGet();

                    byte[] signedData = CryptoManager.concatenate(
                            "DELETE_TRACE".getBytes(StandardCharsets.UTF_8),
                            traceIdBytes,
                            timestampBytes
                    ).blockingGet();

                    String encodedSignature = cryptoManager.ecdsa(signedData, privateKey)
                            .flatMap(SerializationUtil::toBase64)
                            .blockingGet();

                    return new TraceDeletionRequestData(traceId, timestamp, encodedSignature);
                }));
    }

    public Completable deleteCheckInLocally(@NonNull String traceId) {
        return historyManager.deleteItems(historyItem -> traceId.equals(historyItem.getRelatedId()));
    }

    /*
        Distance and duration
     */

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Completable assertMinimumDistanceToLocation() {
        return getCheckInDataIfAvailable()
                .filter(CheckInData::hasLocationRestriction)
                .filter(data -> !skipMinimumDistanceAssertion)
                .flatMapCompletable(data -> {
                    if (!locationManager.hasLocationPermission()) {
                        return Completable.error(new CheckOutException("Location permission has not been granted", CheckOutException.MISSING_PERMISSION_ERROR));
                    } else if (!locationManager.isLocationServiceEnabled()) {
                        return Completable.error(new CheckOutException("Location service is disabled", CheckOutException.LOCATION_UNAVAILABLE_ERROR));
                    } else {
                        return getCurrentDistanceToVenueLocation()
                                .timeout(LOCATION_REQUEST_TIMEOUT, TimeUnit.MILLISECONDS)
                                .doOnError(throwable -> Timber.w(throwable, "Unable to get distance to venue location"))
                                .onErrorResumeNext(throwable -> Maybe.error(new CheckOutException("Unable to get location distance", throwable, CheckOutException.LOCATION_UNAVAILABLE_ERROR)))
                                .flatMapCompletable(distance -> {
                                    if (distance > data.getRadius()) {
                                        return Completable.complete();
                                    } else {
                                        return Completable.error(new CheckOutException("Current location still in venue range", CheckOutException.MINIMUM_DISTANCE_ERROR));
                                    }
                                });
                    }
                });
    }

    public Completable assertMinimumCheckInDuration() {
        return getCheckInDataIfAvailable()
                .filter(CheckInData::hasDurationRestriction)
                .filter(data -> !skipMinimumCheckInDurationAssertion)
                .flatMapCompletable(data -> getCurrentCheckInDuration()
                        .flatMapCompletable(checkInDuration -> {
                            if (checkInDuration > data.getMinimumDuration()) {
                                return Completable.complete();
                            } else {
                                return Completable.error(new CheckOutException("Minimum check-in duration not yet reached", CheckOutException.MINIMUM_DURATION_ERROR));
                            }
                        }));
    }

    public Maybe<Long> getCurrentCheckInDuration() {
        return getCheckInDataIfAvailable()
                .map(CheckInData::getTimestamp)
                .flatMapSingle(checkInTimestamp -> genuinityManager
                        .initialize(context)
                        .andThen(genuinityManager.getOrFetchOrRestoreServerTimeOffset().onErrorReturnItem(0L))
                        .map(serverOffset -> TimeUtil.getCurrentMillis() - checkInTimestamp - serverOffset)
                );
    }

    public Maybe<Location> getVenueLocation() {
        return getCheckInDataIfAvailable()
                .filter(CheckInData::hasLocation)
                .map(data -> {
                    Location venueLocation = new Location(android.location.LocationManager.GPS_PROVIDER);
                    venueLocation.setLatitude(data.getLatitude());
                    venueLocation.setLongitude(data.getLongitude());
                    return venueLocation;
                });
    }

    @RequiresPermission(anyOf = {ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION})
    public Maybe<Double> getCurrentDistanceToVenueLocation() {
        return getVenueLocation()
                .flatMap(location -> locationManager.getLastKnownDistanceTo(location)
                        .switchIfEmpty(locationManager.getDistanceUpdatesTo(location)
                                .firstElement()));
    }

    public Single<LocationResponseData> getLocationDataFromScannerId(@NonNull String scannerId) {
        return networkManager.getLucaEndpointsV3()
                .flatMap(lucaEndpointsV3 -> lucaEndpointsV3.getScanner(scannerId)
                        .map(jsonObject -> jsonObject.get("locationId").getAsString())
                        .flatMap(lucaEndpointsV3::getLocation));
    }

    /*
        Check-In Data
     */

    public Maybe<CheckInData> getCheckInDataIfAvailable() {
        return Maybe.fromCallable(() -> checkInData);
    }

    private Maybe<Long> getAverageCheckInDurationIfAvailable() {
        return getCheckInDataIfAvailable()
                .map(CheckInData::getAverageCheckInDuration)
                .filter(averageCheckInDuration -> averageCheckInDuration > 0);
    }

    public Observable<CheckInData> getCheckInDataAndChanges() {
        return preferencesManager.restoreIfAvailableAndGetChanges(KEY_CHECK_IN_DATA, CheckInData.class);
    }

    private Maybe<CheckInData> fetchCheckInDataFromBackend(boolean useOlderTraceIds) {
        return getTraceDataFromBackend(useOlderTraceIds)
                .flatMap(traceData -> {
                    if (traceData.isCheckedOut()) {
                        return Maybe.empty();
                    }
                    return networkManager.getLucaEndpointsV3()
                            .flatMap(lucaEndpointsV3 -> lucaEndpointsV3.getLocation(traceData.getLocationId()))
                            .map(location -> {
                                Timber.d("Creating check-in data for location: %s", location);
                                CheckInData checkInData = new CheckInData();
                                checkInData.setTraceId(traceData.getTraceId());
                                checkInData.setTimestamp(TimeUtil.convertFromUnixTimestamp(traceData.getCheckInTimestamp()).blockingGet());
                                checkInData.setLocationId(UUID.fromString(traceData.getLocationId()));
                                checkInData.setPrivateMeeting(location.isPrivate());
                                checkInData.setContactDataMandatory(location.isContactDataMandatory());

                                if (location.getGroupName() == null && location.getAreaName() == null) {
                                    // private meeting location
                                    if (meetingAdditionalData != null) {
                                        checkInData.setLocationAreaName(meetingAdditionalData.getFirstName() + " " + meetingAdditionalData.getLastName());
                                    }
                                    checkInData.setLocationGroupName(context.getString(R.string.meeting_heading));
                                } else {
                                    // regular location
                                    checkInData.setLocationGroupName(location.getGroupName());
                                    checkInData.setLocationAreaName(location.getAreaName());
                                    checkInData.setLatitude(location.getLatitude());
                                    checkInData.setLongitude(location.getLongitude());
                                }
                                checkInData.setRadius(location.getRadius());
                                checkInData.setMinimumDuration(MINIMUM_CHECK_IN_DURATION);
                                checkInData.setAverageCheckInDuration(TimeUnit.MINUTES.toMillis(location.getAverageCheckInDuration()));
                                return checkInData;
                            }).toMaybe();

                })
                .doOnSubscribe(disposable -> Timber.v("Requesting check-in data from backend"));
    }

    private Completable persistCheckInData(CheckInData newCheckInData) {
        return preferencesManager.persist(KEY_CHECK_IN_DATA, newCheckInData)
                .doOnSubscribe(disposable -> Timber.d("Persisting check-in data: %s", newCheckInData));
    }

    private Completable removeCheckInDataIfCheckedOut() {
        return isCheckedInAtBackend(false)
                .flatMapCompletable(isCheckedIn -> {
                    if (!isCheckedIn) {
                        return removeCheckInData();
                    } else {
                        Timber.w("Not removing check-in data, still checked in");
                        return Completable.complete();
                    }
                });
    }

    private Completable removeCheckInData() {
        return Completable.mergeArray(
                deleteUnusedTraceData(),
                preferencesManager.delete(KEY_CHECK_IN_DATA)
        ).andThen(Completable.fromAction(() -> checkInData = null))
                .doOnComplete(() -> Timber.d("Removed check-in data"));
    }

    /*
        Archive
     */

    public Observable<CheckInData> getArchivedCheckInData() {
        return archiver.getData();
    }

    public Maybe<CheckInData> getArchivedCheckInData(@NonNull String traceId) {
        return getArchivedCheckInData()
                .filter(archivedCheckInData -> traceId.equals(archivedCheckInData.getTraceId()))
                .firstElement();
    }

    private Completable invokeDeleteOldArchivedCheckInData() {
        return invokeDelayed(deleteOldArchivedCheckInData(), TimeUnit.SECONDS.toMillis(3));
    }

    public Completable addCheckInDataToArchive(@NonNull CheckInData checkInData) {
        return archiver.addData(checkInData);
    }

    public Completable deleteOldArchivedCheckInData() {
        return archiver.deleteDataOlderThan()
                .doOnComplete(() -> Timber.d("Deleted old archived check-in data"))
                .doOnError(throwable -> Timber.w("Unable to delete old check-in data: %s", throwable.toString()));
    }

    /*
        Trace data
     */

    private Maybe<TraceData> getTraceDataFromBackend(boolean useOlderTraceIds) {
        return getTraceDataForCheckedInTraceIdFromBackend()
                .switchIfEmpty(getTraceDataForRecentTraceIdsFromBackend(useOlderTraceIds));
    }

    private Maybe<TraceData> getTraceDataForCheckedInTraceIdFromBackend() {
        return getCheckedInTraceId()
                .flatMap(this::getTraceDataFromBackend);
    }

    private Maybe<TraceData> getTraceDataForRecentTraceIdsFromBackend(boolean useOlderTraceIds) {
        return getRecentTraceIds(useOlderTraceIds)
                .takeLast(RECENT_TRACE_IDS_LIMIT)
                .toList()
                .flatMapObservable(this::getTraceDataFromBackend)
                .lastElement();
    }

    private Maybe<TraceData> getTraceDataFromBackend(@NonNull byte[] traceId) {
        return Single.fromCallable(() -> Collections.singletonList(traceId))
                .flatMapObservable(this::getTraceDataFromBackend)
                .lastElement();
    }

    private Observable<TraceData> getTraceDataFromBackend(@NonNull List<byte[]> traceIds) {
        return Observable.fromIterable(traceIds)
                .flatMapSingle(SerializationUtil::toBase64)
                .toList()
                .filter(serializedTraceIds -> !serializedTraceIds.isEmpty())
                .map(serializedTraceIds -> {
                    JsonArray jsonArray = new JsonArray(serializedTraceIds.size());
                    for (String serializedTraceId : serializedTraceIds) {
                        jsonArray.add(serializedTraceId);
                    }
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.add("traceIds", jsonArray);
                    return jsonObject;
                })
                .flatMapSingle(jsonObject -> networkManager.getLucaEndpointsV3()
                        .flatMap(lucaEndpointsV3 -> lucaEndpointsV3.getTraces(jsonObject)))
                .flatMapObservable(Observable::fromIterable)
                .sorted((first, second) -> Long.compare(first.getCheckInTimestamp(), second.getCheckInTimestamp()));
    }

    public Maybe<byte[]> getCheckedInTraceId() {
        return getCheckInDataIfAvailable()
                .map(CheckInData::getTraceId)
                .flatMapSingle(SerializationUtil::fromBase64);
    }

    public Single<Boolean> hasRecentTraceIds(boolean useOlderTraceIds) {
        return getRecentTraceIds(useOlderTraceIds)
                .isEmpty()
                .map(isEmpty -> !isEmpty);
    }

    private Observable<byte[]> getRecentTraceIds(boolean useOlderTraceIds) {
        return getTraceIdWrappers()
                .filter(traceIdWrapper -> {
                    long creationTimestamp = TimeUtil.convertFromUnixTimestamp(traceIdWrapper.getTimestamp()).blockingGet();
                    long age = TimeUtil.getCurrentMillis() - creationTimestamp;
                    if (useOlderTraceIds) {
                        return age > MAXIMUM_YOUNGER_TRACE_ID_AGE;
                    } else {
                        return age <= MAXIMUM_YOUNGER_TRACE_ID_AGE;
                    }
                })
                .map(TraceIdWrapper::getTraceId);
    }

    public Observable<String> getArchivedTraceIds() {
        return getArchivedCheckInData()
                .map(CheckInData::getTraceId);
    }

    /*
        Trace IDs
     */

    /**
     * Generate and persist a trace ID from given user ID.
     */
    public Single<TraceIdWrapper> getTraceIdWrapper(@NonNull UUID userId) {
        return generateTraceIdWrapper(userId)
                .flatMap(traceIdWrapper -> persistTraceIdWrapper(traceIdWrapper)
                        .andThen(Single.just(traceIdWrapper)));
    }

    private Single<TraceIdWrapper> generateTraceIdWrapper(@NonNull UUID userId) {
        return TimeUtil.getCurrentUnixTimestamp()
                .flatMap(TimeUtil::roundUnixTimestampDownToMinute)
                .flatMap(roundedUnixTimestamp -> generateTraceId(userId, roundedUnixTimestamp)
                        .map(traceId -> new TraceIdWrapper(roundedUnixTimestamp, traceId)));
    }

    public Single<byte[]> generateTraceId(@NonNull UUID userId, long roundedUnixTimestamp) {
        return cryptoManager.initialize(context)
                .andThen(Single.zip(CryptoManager.encode(userId), TimeUtil.encodeUnixTimestamp(roundedUnixTimestamp), Pair::new))
                .flatMap(encodedDataPair -> CryptoManager.concatenate(encodedDataPair.first, encodedDataPair.second))
                .flatMap(encodedData -> getCurrentTracingSecret()
                        .flatMap(tracingSecret -> cryptoManager.hmac(encodedData, tracingSecret)))
                .flatMap(traceId -> CryptoManager.trim(traceId, TRIMMED_HASH_LENGTH))
                .doOnSuccess(traceId -> Timber.d("Generated new trace ID: %s", SerializationUtil.toBase64(traceId).blockingGet()));
    }

    /**
     * Get current {@link TraceIdWrapper}s ordered by timestamp.
     */
    public Observable<TraceIdWrapper> getTraceIdWrappers() {
        return restoreTraceIdWrappers();
    }

    /**
     * Restore {@link TraceIdWrapperList} from {@link PreferencesManager} and sort by timestamp.
     */
    private Observable<TraceIdWrapper> restoreTraceIdWrappers() {
        return preferencesManager.restoreIfAvailable(KEY_TRACE_ID_WRAPPERS, TraceIdWrapperList.class)
                .flatMapObservable(Observable::fromIterable)
                .sorted((first, second) -> Long.compare(first.getTimestamp(), second.getTimestamp()));
    }

    /**
     * Persist given {@link TraceIdWrapper}, appending it to the list of stored ones.
     */
    private Completable persistTraceIdWrapper(@NonNull TraceIdWrapper traceIdWrapper) {
        return getTraceIdWrappers()
                .mergeWith(Observable.just(traceIdWrapper))
                .toList()
                .map(TraceIdWrapperList::new)
                .flatMapCompletable(traceIdWrappers -> preferencesManager.persist(KEY_TRACE_ID_WRAPPERS, traceIdWrappers));
    }

    /**
     * Delete all trace IDs and their associated user ephemeral key pair that do not belong to any check-in.
     */
    public Completable deleteUnusedTraceData() {
        Observable<String> allTraceIds = getTraceIdWrappers()
                .map(TraceIdWrapper::getTraceId)
                .flatMapSingle(SerializationUtil::toBase64);

        Observable<String> checkedInTraceIds = getArchivedTraceIds();

        Observable<String> discardableTraceIds = Single.zip(
                allTraceIds.toList(),
                checkedInTraceIds.toList(),
                (all, checkedIn) -> {
                    ArrayList<String> discardable = new ArrayList<>(all);
                    discardable.removeAll(checkedIn);
                    return discardable;
                }
        ).flatMapObservable(Observable::fromIterable);

        return cryptoManager.initialize(context)
                .andThen(discardableTraceIds)
                .flatMapSingle(SerializationUtil::fromBase64)
                .flatMapSingle(CheckInManager::getGuestEphemeralKeyPairAlias)
                .flatMapCompletable(cryptoManager::deleteKeyPair)
                .andThen(preferencesManager.delete(KEY_TRACE_ID_WRAPPERS));
    }

    /*
        Tracing secrets
     */

    /**
     * Get or create tracing secret - a randomly generated seed used to derive trace IDs when
     * checking in using the Guest App. It is stored locally on the Guest App until it is shared
     * with the Health Department during contact tracing. Moreover, the tracing secret is rotated on
     * a regular basis in order to limit the number of trace IDs that can be reconstructed when the
     * secret is shared
     * <br>
     * Rotation of the daily tracing secret is ensured by the method either restoring today's secret
     * or generating (and persisting) a new one for today.
     *
     * @see <a href="https://www.luca-app.de/securityoverview/properties/secrets.html#term-tracing-secret">Security
     * Overview: Secrets</a>
     * @see <a href="https://luca-app.de/securityoverview/processes/guest_registration.html#rotating-the-tracing-secret">Security
     * Overview: Rotating the Tracing Secret</a>
     */
    public Single<byte[]> getCurrentTracingSecret() {
        return restoreCurrentTracingSecret()
                .switchIfEmpty(generateTracingSecret()
                        .observeOn(Schedulers.io())
                        .flatMap(secret -> persistCurrentTracingSecret(secret)
                                .andThen(Single.just(secret))));
    }

    private Single<byte[]> generateTracingSecret() {
        return cryptoManager.initialize(context)
                .andThen(cryptoManager.generateSecureRandomData(TRIMMED_HASH_LENGTH))
                .doOnSuccess(bytes -> Timber.d("Generated new tracing secret"));
    }

    /**
     * Restore only the recent most tracing secret, meaning today's secret.
     *
     * @return today's tracing secret if available
     */
    private Maybe<byte[]> restoreCurrentTracingSecret() {
        return restoreRecentTracingSecrets(0)
                .map(pair -> pair.second)
                .firstElement();
    }

    /**
     * Persist given tracing secret to preferences, encrypted as a {@link WrappedSecret}.
     */
    private Completable persistCurrentTracingSecret(@NonNull byte[] secret) {
        return TimeUtil.getStartOfCurrentDayTimestamp()
                .map(startOfDayTimestamp -> KEY_PREFIX_TRACING_SECRET + startOfDayTimestamp)
                .flatMapCompletable(preferenceKey -> cryptoManager.persistWrappedSecret(preferenceKey, secret));
    }

    public Observable<Pair<Long, byte[]>> restoreRecentTracingSecrets(long duration) {
        return cryptoManager.initialize(context)
                .andThen(generateRecentStartOfDayTimestamps(duration))
                .flatMapMaybe(startOfDayTimestamp -> cryptoManager.restoreWrappedSecretIfAvailable(KEY_PREFIX_TRACING_SECRET + startOfDayTimestamp)
                        .map(secret -> new Pair<>(startOfDayTimestamp, secret)));
    }

    public Observable<Long> generateRecentStartOfDayTimestamps(long duration) {
        return TimeUtil.getStartOfCurrentDayTimestamp()
                .flatMapObservable(firstStartOfDayTimestamp -> Observable.range(0, (int) TimeUnit.MILLISECONDS.toDays(duration) + 1)
                        .map(dayIndex -> firstStartOfDayTimestamp - TimeUnit.DAYS.toMillis(dayIndex)));
    }

    public static Single<String> getGuestEphemeralKeyPairAlias(byte[] traceId) {
        return SerializationUtil.toBase64(traceId)
                .map(serializedTraceId -> ALIAS_GUEST_EPHEMERAL_KEY_PAIR + "-" + serializedTraceId);
    }

    public void setSkipMinimumCheckInDurationAssertion(boolean skipMinimumCheckInDurationAssertion) {
        this.skipMinimumCheckInDurationAssertion = skipMinimumCheckInDurationAssertion;
    }

    public void setSkipMinimumDistanceAssertion(boolean skipMinimumDistanceAssertion) {
        this.skipMinimumDistanceAssertion = skipMinimumDistanceAssertion;
    }

    @Nullable
    public MeetingAdditionalData getMeetingAdditionalData() {
        return meetingAdditionalData;
    }

    public void setMeetingAdditionalData(@Nullable MeetingAdditionalData meetingAdditionalData) {
        this.meetingAdditionalData = meetingAdditionalData;
    }

}
