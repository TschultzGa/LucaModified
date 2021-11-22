package de.culture4life.luca.ui.checkin;

import static de.culture4life.luca.crypto.HashProvider.TRIMMED_HASH_LENGTH;
import static de.culture4life.luca.registration.RegistrationManager.USER_ID_KEY;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_NOT_SHARED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_PCR_TESTED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_QUICK_TESTED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_RECOVERED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_VACCINATED;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.zxing.EncodeHintType;

import net.glxn.qrgen.android.QRCode;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.ECPublicKey;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.R;
import de.culture4life.luca.checkin.CheckInData;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.crypto.AsymmetricCipherProvider;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.crypto.DailyKeyPairPublicKeyWrapper;
import de.culture4life.luca.crypto.TraceIdWrapper;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.meeting.MeetingAdditionalData;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.LocationResponseData;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseQrCodeViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.ViewEvent;
import de.culture4life.luca.ui.myluca.MyLucaViewModel;
import de.culture4life.luca.util.SerializationUtil;
import de.culture4life.luca.util.ThrowableUtil;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class CheckInViewModel extends BaseQrCodeViewModel {

    private static final UUID DEBUGGING_SCANNER_ID = UUID.fromString("1444c1a2-1922-4c11-813d-710d9f901227");
    private static final long CHECK_IN_POLLING_INTERVAL = TimeUnit.SECONDS.toMillis(3);
    private static final String KEY_SKIP_CHECK_IN_CONFIRMATION = "dont_ask_confirmation";
    private static final boolean FEATURE_ANONYMOUS_CHECKIN_DISABLED = true;

    private final RegistrationManager registrationManager;
    private final CheckInManager checkInManager;
    private final CryptoManager cryptoManager;
    private final MeetingManager meetingManager;
    private final NetworkManager networkManager;
    private final DocumentManager documentManager;

    private final MutableLiveData<Bundle> bundle = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<String>> possibleDocumentData = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> qrCode = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<CheckInData>> checkInData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> networkAvailable = new MutableLiveData<>();
    private final MutableLiveData<Boolean> contactDataMissing = new MutableLiveData<>();
    private final MutableLiveData<Boolean> updateRequired = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<String>> privateMeetingUrl = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Pair<String, String>>> confirmCheckIn = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Pair<String, String>>> voluntaryCheckIn = new MutableLiveData<>();

    private MyLucaViewModel myLucaViewModel;

    private UUID userId;

    private ViewError meetingError;
    private ViewError deepLinkError;

    public CheckInViewModel(@NonNull Application application) {
        super(application);
        this.registrationManager = this.application.getRegistrationManager();
        this.checkInManager = this.application.getCheckInManager();
        this.cryptoManager = this.application.getCryptoManager();
        this.meetingManager = this.application.getMeetingManager();
        this.networkManager = this.application.getNetworkManager();
        this.documentManager = this.application.getDocumentManager();
    }

    public void setupViewModelReference(FragmentActivity activity) {
        if (myLucaViewModel == null) {
            myLucaViewModel = new ViewModelProvider(activity).get(MyLucaViewModel.class);
            myLucaViewModel.setupViewModelReference(activity);
        }
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.fromAction(() -> checkInData.setValue(null)))
                .andThen(Completable.mergeArray(
                        registrationManager.initialize(application),
                        checkInManager.initialize(application),
                        cryptoManager.initialize(application),
                        meetingManager.initialize(application),
                        networkManager.initialize(application),
                        documentManager.initialize(application)
                ))
                .andThen(preferencesManager.restore(USER_ID_KEY, UUID.class)
                        .doOnSuccess(uuid -> this.userId = uuid)
                        .ignoreElement())
                .andThen(Completable.fromAction(() -> modelDisposable.add(getCameraConsentGiven()
                        .flatMapCompletable(cameraConsentGiven -> update(getShowCameraPreview(), new CameraRequest(cameraConsentGiven, true)))
                        .delaySubscription(100, TimeUnit.MILLISECONDS)
                        .subscribe())))
                .doOnComplete(this::handleApplicationDeepLinkIfAvailable);
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                observeNetworkChanges(),
                observeCheckInDataChanges(),
                observeIncludeEntryPolicyChanges(),
                keepUpdatingQrCodes().delaySubscription(100, TimeUnit.MILLISECONDS)
        );
    }

    private Completable observeNetworkChanges() {
        return networkManager.getConnectivityStateAndChanges()
                .flatMapCompletable(isNetworkConnected -> update(networkAvailable, isNetworkConnected));
    }

    /**
     * Poll backend for a processed check-in referencing trace IDs previously shown as QR-code.
     *
     * @return Completable providing visual feedback, e.g. redirecting the user to the venue
     * fragment
     * @see <a href="https://luca-app.de/securityoverview/processes/guest_app_checkin.html#qr-code-scanning-feedback">Security
     * Overview: QR Code Scanning Feedback</a>
     */
    private Completable observeCheckInDataChanges() {
        return Completable.mergeArray(
                checkInManager.updateCheckInDataIfNecessary(CHECK_IN_POLLING_INTERVAL, false),
                checkInManager.getCheckInDataAndChanges()
                        .observeOn(AndroidSchedulers.mainThread())
                        .flatMapCompletable(updatedCheckInData -> Completable.fromAction(() -> {
                            updateAsSideEffect(checkInData, new ViewEvent<>(updatedCheckInData));
                            if (isCurrentDestinationId(R.id.checkInFragment)) {
                                navigationController.navigate(R.id.action_checkInFragment_to_venueDetailFragmentCheckedIn, bundle.getValue());
                            }
                        }))
        );
    }

    private Completable observeIncludeEntryPolicyChanges() {
        return preferencesManager.getChanges(CheckInManager.KEY_INCLUDE_ENTRY_POLICY, Boolean.class)
                .flatMapCompletable(includeEntryPolicy -> updateQrCode())
                .subscribeOn(Schedulers.io());
    }

    public void checkIfContactDataMissing() {
        modelDisposable.add(registrationManager.hasProvidedRequiredContactData()
                .doOnSuccess(hasProvidedRequiredData -> Timber.v("Has provided required contact data: %b", hasProvidedRequiredData))
                .subscribeOn(Schedulers.io())
                .flatMapCompletable(hasProvidedRequiredData -> update(contactDataMissing, !hasProvidedRequiredData))
                .subscribe());
    }

    public void checkIfUpdateIsRequired() {
        modelDisposable.add(application.isUpdateRequired()
                .doOnSubscribe(disposable -> Timber.d("Checking if update is required"))
                .doOnSuccess(isUpdateRequired -> Timber.v("Update required: %b", isUpdateRequired))
                .doOnError(throwable -> Timber.w("Unable to check if update is required: %s", throwable.toString()))
                .flatMapCompletable(isUpdateRequired -> update(updateRequired, isUpdateRequired))
                .retryWhen(throwable -> throwable.delay(5, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    public void checkIfHostingMeeting() {
        modelDisposable.add(meetingManager.isCurrentlyHostingMeeting()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        isHostingMeeting -> {
                            if (isCurrentDestinationId(R.id.checkInFragment) && isHostingMeeting) {
                                navigationController.navigate(R.id.action_checkInFragment_to_meetingFragment, bundle.getValue());
                            }
                        },
                        throwable -> Timber.w("Unable to check if hosting a meeting")
                ));
    }

    /*
        QR code generation
     */

    private Completable keepUpdatingQrCodes() {
        return Observable.interval(0, 1, TimeUnit.MINUTES, Schedulers.io())
                .flatMapCompletable(tick -> updateQrCode());
    }

    private Completable updateQrCode() {
        return generateQrCodeData()
                .doOnSubscribe(disposable -> Timber.d("Generating new QR code data"))
                .doOnSuccess(qrCodeData -> Timber.i("Generated new QR code data: %s", qrCodeData))
                .flatMap(this::serializeQrCodeData)
                .doOnSuccess(serializedQrCodeData -> Timber.d("Serialized QR code data: %s", serializedQrCodeData))
                .flatMap(this::generateQrCode)
                .flatMapCompletable(bitmap -> update(qrCode, bitmap))
                .doOnError(throwable -> Timber.w("Unable to update QR code: %s", throwable.toString()))
                .onErrorComplete()
                .doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    private Single<QrCodeData> generateQrCodeData() {
        return generateQrCodeData(false);
    }

    private Single<QrCodeData> generateQrCodeData(boolean isAnonymous) {
        return Single.just(new QrCodeData())
                .flatMap(qrCodeData -> checkInManager.getTraceIdWrapper(userId)
                        .flatMapCompletable(userTraceIdWrapper -> Completable.mergeArray(
                                cryptoManager.getDailyKeyPairPublicKeyWrapper()
                                        .map(DailyKeyPairPublicKeyWrapper::getId)
                                        .doOnSuccess(qrCodeData::setKeyId)
                                        .ignoreElement(),
                                cryptoManager.getGuestEphemeralKeyPair(userTraceIdWrapper.getTraceId())
                                        .observeOn(Schedulers.computation())
                                        .flatMapCompletable(keyPair -> {
                                            if (!isAnonymous) {
                                                return setQrCodeEncryptedData(qrCodeData, keyPair, userTraceIdWrapper);
                                            } else {
                                                return setQrCodeAnonymousEncryptedData(qrCodeData, keyPair, userTraceIdWrapper);
                                            }
                                        }),
                                TimeUtil.encodeUnixTimestamp(userTraceIdWrapper.getTimestamp())
                                        .doOnSuccess(qrCodeData::setTimestamp)
                                        .ignoreElement(),
                                getQrCodeEntryPolicy()
                                        .doOnSuccess(qrCodeData::setEntryPolicy)
                                        .ignoreElement(),
                                Completable.fromAction(() -> qrCodeData.setTraceId(userTraceIdWrapper.getTraceId()))))
                        .andThen(Single.just(qrCodeData)));
    }

    private Single<Byte> getQrCodeEntryPolicy() {
        return preferencesManager.restoreOrDefault(CheckInManager.KEY_INCLUDE_ENTRY_POLICY, false)
                .flatMap(includeEntryPolicy -> {
                    if (!includeEntryPolicy) {
                        return Single.just(ENTRY_POLICY_NOT_SHARED);
                    } else {
                        return getEntryPolicyIfAvailable(documentManager.hasVaccinationDocument(), ENTRY_POLICY_VACCINATED)
                                .switchIfEmpty(getEntryPolicyIfAvailable(documentManager.hasRecoveryDocument(), ENTRY_POLICY_RECOVERED))
                                .switchIfEmpty(getEntryPolicyIfAvailable(documentManager.hasPcrTestDocument(), ENTRY_POLICY_PCR_TESTED))
                                .switchIfEmpty(getEntryPolicyIfAvailable(documentManager.hasQuickTestDocument(), ENTRY_POLICY_QUICK_TESTED))
                                .defaultIfEmpty(ENTRY_POLICY_NOT_SHARED);
                    }
                })
                .map(entryPolicy -> (byte) (int) entryPolicy);
    }

    private Maybe<Integer> getEntryPolicyIfAvailable(Single<Boolean> hasDocument, @QrCodeData.EntryPolicy int entryPolicy) {
        return hasDocument.flatMapMaybe(available -> {
            if (available) {
                return Maybe.just(entryPolicy);
            } else {
                return Maybe.empty();
            }
        });
    }

    private Completable setQrCodeEncryptedData(QrCodeData qrCodeData, KeyPair keyPair, TraceIdWrapper userTraceIdWrapper) {
        return Completable.mergeArray(
                encryptUserIdAndSecret(userId, keyPair)
                        .doOnSuccess(encryptedDataAndIv -> qrCodeData.setEncryptedData(encryptedDataAndIv.first))
                        .flatMap(encryptedDataAndIv -> generateVerificationTag(encryptedDataAndIv.first, userTraceIdWrapper.getTimestamp())
                                .doOnSuccess(qrCodeData::setVerificationTag))
                        .ignoreElement(),
                Single.just(keyPair.getPublic())
                        .cast(ECPublicKey.class)
                        .flatMap(publicKey -> AsymmetricCipherProvider.encode(publicKey, true))
                        .doOnSuccess(qrCodeData::setUserEphemeralPublicKey)
                        .ignoreElement()
        );
    }

    private Completable setQrCodeAnonymousEncryptedData(QrCodeData qrCodeData, KeyPair keyPair, TraceIdWrapper userTraceIdWrapper) {
        return Completable.mergeArray(
                Single.just(keyPair.getPublic())
                        .cast(ECPublicKey.class)
                        .flatMap(publicKey -> AsymmetricCipherProvider.encode(publicKey, true))
                        .doOnSuccess(userEphemeralKey -> {
                            qrCodeData.setUserEphemeralPublicKey(userEphemeralKey);
                            qrCodeData.setEncryptedData(new byte[0]);
                        })
                        .ignoreElement(),
                generateVerificationTag(new byte[0], userTraceIdWrapper.getTimestamp())
                        .doOnSuccess(qrCodeData::setVerificationTag)
                        .ignoreElement()
        );
    }

    private Single<android.util.Pair<byte[], byte[]>> encryptUserIdAndSecret(@NonNull UUID userId, @NonNull KeyPair userEphemeralKeyPair) {
        return Single.just(userEphemeralKeyPair.getPublic())
                .cast(ECPublicKey.class)
                .flatMap(publicKey -> AsymmetricCipherProvider.encode(publicKey, true))
                .flatMap(encodedPublicKey -> CryptoManager.trim(encodedPublicKey, TRIMMED_HASH_LENGTH))
                .flatMap(iv -> encryptUserIdAndSecret(userId, userEphemeralKeyPair.getPrivate(), iv)
                        .map(bytes -> new android.util.Pair<>(bytes, iv)));
    }

    private Single<byte[]> encryptUserIdAndSecret(@NonNull UUID userId, @NonNull PrivateKey userEphemeralPrivateKey, @NonNull byte[] iv) {
        return cryptoManager.getDataSecret()
                .flatMap(userDataSecret -> CryptoManager.encode(userId)
                        .flatMap(encodedUserId -> CryptoManager.concatenate(encodedUserId, userDataSecret)))
                .flatMap(encodedData -> cryptoManager.generateSharedDiffieHellmanSecret(userEphemeralPrivateKey)
                        .flatMap(cryptoManager::generateDataEncryptionSecret)
                        .flatMap(CryptoManager::createKeyFromSecret)
                        .flatMap(encodingKey -> cryptoManager.getSymmetricCipherProvider().encrypt(encodedData, iv, encodingKey)));
    }

    private Single<byte[]> generateVerificationTag(@NonNull byte[] encryptedUserIdAndSecret, long roundedUnixTimestamp) {
        return TimeUtil.encodeUnixTimestamp(roundedUnixTimestamp)
                .flatMap(encodedTimestamp -> CryptoManager.concatenate(encodedTimestamp, encryptedUserIdAndSecret))
                .flatMap(encodedData -> cryptoManager.getDataSecret()
                        .flatMap(cryptoManager::generateDataAuthenticationSecret)
                        .flatMap(CryptoManager::createKeyFromSecret)
                        .flatMap(dataAuthenticationKey -> cryptoManager.getMacProvider().sign(encodedData, dataAuthenticationKey)))
                .flatMap(verificationTag -> CryptoManager.trim(verificationTag, 8))
                .doOnSuccess(verificationTag -> Timber.d("Generated new verification tag: %s", SerializationUtil.serializeToBase64(verificationTag).blockingGet()));
    }

    private Single<String> serializeQrCodeData(@NonNull QrCodeData qrCodeData) {
        return Single.fromCallable(() -> ByteBuffer.allocate(97)
                .put(qrCodeData.getVersion())
                .put(qrCodeData.getDeviceType())
                .put(qrCodeData.getEntryPolicy())
                .put(qrCodeData.getKeyId())
                .put(qrCodeData.getTimestamp())
                .put(qrCodeData.getTraceId())
                .put(qrCodeData.getEncryptedData())
                .put(qrCodeData.getUserEphemeralPublicKey())
                .put(qrCodeData.getVerificationTag())
                .array())
                .flatMap(encodedQrCodeData -> cryptoManager.getHashProvider().hash(encodedQrCodeData)
                        .flatMap(checksum -> CryptoManager.trim(checksum, 4))
                        .flatMap(checksum -> CryptoManager.concatenate(encodedQrCodeData, checksum)))
                .flatMap(SerializationUtil::serializeBase32);
    }

    private Single<Bitmap> generateQrCode(@NonNull String data) {
        return Single.fromCallable(() -> QRCode.from(data)
                .withSize(500, 500)
                .withHint(EncodeHintType.MARGIN, 0)
                .bitmap());
    }

    @Override
    protected boolean isCurrentDestinationId(int destinationId) {
        return super.isCurrentDestinationId(destinationId);
    }

    /*
        QR code scanning
    */

    public boolean canProcessBarcode(@NonNull String url) {
        return isDeepLink(url) && (CheckInManager.isSelfCheckInUrl(url) || MeetingManager.isPrivateMeeting(url));
    }

    @Override
    @NonNull
    protected Completable processBarcode(@NonNull String barcodeData) {
        return Completable.defer(() -> {
            if (myLucaViewModel.canProcessBarcode(barcodeData)) {
                ViewEvent<String> barcodeDataEvent = new ViewEvent<>(barcodeData);
                return update(possibleDocumentData, barcodeDataEvent);
            } else {
                return process(barcodeData);
            }
        }).doOnSubscribe(disposable -> {
            removeError(deepLinkError);
            updateAsSideEffect(getShowCameraPreview(), new CameraRequest(false, true));
            updateAsSideEffect(isLoading, true);
        }).doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    public Completable process(@NonNull String barcodeData) {
        return Single.just(barcodeData)
                .doOnSuccess(value -> Timber.d("Processing barcode: %s", value))
                .doOnSuccess(deepLink -> getNotificationManager().vibrate().subscribe())
                .flatMapCompletable(this::handleDeepLink);
    }

    /*
        Deep link handling
     */

    private void handleApplicationDeepLinkIfAvailable() {
        modelDisposable.add(application.getDeepLink()
                .flatMapCompletable(url -> handleDeepLink(url)
                        .doOnComplete(() -> application.onDeepLinkHandled(url)))
                .subscribe(
                        () -> Timber.d("Handled application deep link"),
                        throwable -> Timber.w("Unable handle application deep link: %s", throwable.toString())
                ));
    }

    private Completable handleDeepLink(@NonNull String url) {
        return Completable.defer(() -> {
            if (MeetingManager.isPrivateMeeting(url)) {
                return handleMeetingCheckInDeepLink(url);
            } else if (CheckInManager.isSelfCheckInUrl(url)) {
                return processMandatoryOrVoluntarySelfCheckIn(url);
            } else {
                return Completable.error(new InvalidCheckInLinkException());
            }
        }).doOnSubscribe(disposable -> {
            removeError(deepLinkError);
            updateAsSideEffect(getShowCameraPreview(), new CameraRequest(false, true));
            updateAsSideEffect(isLoading, true);
        }).doOnError(throwable -> {
            ViewError.Builder errorBuilder = createErrorBuilder(throwable)
                    .withTitle(R.string.error_check_in_failed);

            if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                errorBuilder.withDescription(R.string.error_location_not_found);
            } else if (ThrowableUtil.isCause(InvalidCheckInLinkException.class, throwable)) {
                errorBuilder.withDescription(application.getString(R.string.check_in_invalid_deeplink_error, url));
            } else {
                errorBuilder
                        .withResolveAction(handleDeepLink(url))
                        .withResolveLabel(R.string.action_retry);
            }

            deepLinkError = errorBuilder.build();
            addError(deepLinkError);
        }).doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    private Completable handleMeetingCheckInDeepLink(@NonNull String url) {
        return update(privateMeetingUrl, new ViewEvent<>(url));
    }

    private Completable handleMeetingCheckInDeepLinkAfterApproval(@NonNull String url) {
        Completable extractMeetingHostName = getMeetingAdditionalDataFromUrl(url)
                .doOnSuccess(checkInManager::setMeetingAdditionalData)
                .ignoreElement();

        Single<UUID> scannerId = getScannerIdFromUrl(url);
        Single<String> additionalData = registrationManager
                .getOrCreateRegistrationData()
                .map(MeetingAdditionalData::new)
                .map(meetingAdditionalData -> new Gson().toJson(meetingAdditionalData));

        return extractMeetingHostName.andThen(Single.zip(scannerId, additionalData, Pair::new))
                .flatMapCompletable(scannerIdAndAdditionalData -> performSelfCheckIn(scannerIdAndAdditionalData.first, scannerIdAndAdditionalData.second, true, false));
    }

    private static Single<MeetingAdditionalData> getMeetingAdditionalDataFromUrl(@NonNull String url) {
        return getAdditionalDataFromUrlIfAvailable(url)
                .toSingle()
                .map(json -> new Gson().fromJson(json, MeetingAdditionalData.class));
    }

    private Completable processMandatoryOrVoluntarySelfCheckIn(@NonNull String url) {
        return getScannerIdFromUrl(url)
                .flatMap(uuid -> checkInManager.getLocationDataFromScannerId(uuid.toString()))
                .flatMapCompletable(locationResponseData -> {
                    // disable anonymous checkin for now, remove when not necessary anymore
                    if (locationResponseData.isContactDataMandatory() || FEATURE_ANONYMOUS_CHECKIN_DISABLED) {
                        return handleSelfCheckInDeepLinkConfirmIfNecessary(url, locationResponseData);
                    } else {
                        return handleVoluntarySelfCheckIn(url, locationResponseData);
                    }
                });
    }

    private Completable handleSelfCheckInDeepLinkConfirmIfNecessary(@NonNull String url, @NonNull LocationResponseData locationResponseData) {
        return preferencesManager.restoreOrDefault(KEY_SKIP_CHECK_IN_CONFIRMATION, false)
                .flatMapCompletable(skipConfirmation -> {
                    if (skipConfirmation) {
                        return handleSelfCheckInDeepLink(url);
                    } else {
                        return update(confirmCheckIn, new ViewEvent<>(new Pair<>(url, locationResponseData.getGroupName())));
                    }
                });
    }

    private Completable handleVoluntarySelfCheckIn(@NonNull String url, @NonNull LocationResponseData locationResponseData) {
        return update(voluntaryCheckIn, new ViewEvent<>(new Pair<>(url, locationResponseData.getGroupName())));
    }

    public Completable handleSelfCheckInDeepLink(@NonNull String url) {
        return handleSelfCheckInDeepLink(url, false);
    }

    public Completable handleSelfCheckInDeepLink(@NonNull String url, boolean isAnonymous) {
        Single<UUID> scannerId = getScannerIdFromUrl(url);
        Single<String> additionalData = getAdditionalDataFromUrlIfAvailable(url).defaultIfEmpty("");

        return Single.zip(scannerId, additionalData, Pair::new)
                .flatMapCompletable(scannerIdAndAdditionalData -> performSelfCheckIn(
                        scannerIdAndAdditionalData.first,
                        scannerIdAndAdditionalData.second,
                        false,
                        isAnonymous
                ));
    }

    private Completable performSelfCheckIn(UUID scannerId, @Nullable String additionalData, boolean requirePrivateMeeting, boolean isAnonymousCheckIn) {
        return generateQrCodeData(isAnonymousCheckIn)
                .flatMapCompletable(qrCodeData -> checkInManager.checkIn(scannerId, qrCodeData))
                .andThen(Completable.defer(() -> {
                    if (requirePrivateMeeting) {
                        return checkInManager.assertCheckedInToPrivateMeeting();
                    } else {
                        return Completable.complete();
                    }
                }))
                .andThen(Completable.fromAction(() -> uploadAdditionalDataIfAvailableAsSideEffect(scannerId, additionalData)))
                .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true));
    }

    private void uploadAdditionalDataIfAvailableAsSideEffect(@NonNull UUID scannerId, @Nullable String additionalData) {
        uploadAdditionalDataIfAvailable(scannerId, additionalData)
                .doOnError(throwable -> Timber.w("Unable to upload additional data: %s", throwable.toString()))
                .retryWhen(errors -> errors.delay(10, TimeUnit.SECONDS))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.v("Uploaded additional data"),
                        throwable -> Timber.e(throwable, "Unable to upload additional data")
                );
    }

    private Completable uploadAdditionalDataIfAvailable(@NonNull UUID scannerId, @Nullable String additionalData) {
        return Maybe.fromCallable(() -> additionalData)
                .filter(data -> !data.isEmpty())
                .map(JsonParser::parseString)
                .map(JsonElement::getAsJsonObject)
                .flatMapCompletable(additionalProperties -> uploadAdditionalData(scannerId, additionalProperties));
    }

    private Completable uploadAdditionalData(@NonNull UUID scannerId, @NonNull JsonObject additionalData) {
        return checkInManager.getLocationPublicKey(scannerId)
                .flatMapCompletable(locationPublicKey -> checkInManager.addAdditionalCheckInProperties(additionalData, locationPublicKey));
    }

    public void onDebuggingCheckInRequested() {
        modelDisposable.add(generateQrCodeData()
                .flatMapCompletable(qrCodeData -> checkInManager.checkIn(DEBUGGING_SCANNER_ID, qrCodeData))
                .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true))
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribe(
                        () -> Timber.i("Checked in"),
                        throwable -> Timber.w("Unable to check in: %s", throwable.toString())
                ));
    }

    public void onCheckInConfirmationApproved(@NonNull String url, boolean skipConfirmation) {
        persistSkipCheckInConfirmation(skipConfirmation)
                .andThen(handleSelfCheckInDeepLink(url))
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    public void onCheckInConfirmationDismissed(@NonNull String url, boolean skipConfirmation) {
        persistSkipCheckInConfirmation(skipConfirmation)
                .andThen(update(getShowCameraPreview(), new CameraRequest(true, true)))
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    public void onVoluntaryCheckInConfirmationApproved(@NonNull String url, boolean shareContactData) {
        handleSelfCheckInDeepLink(url, !shareContactData)
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    public void onVoluntaryCheckInConfirmationDismissed() {
        updateAsSideEffect(getShowCameraPreview(), new CameraRequest(true, true));
    }

    public void onImportDocumentConfirmationDismissed() {
        updateAsSideEffect(getShowCameraPreview(), new CameraRequest(true, true));
    }

    public void onPrivateMeetingJoinApproved(@NonNull String url) {
        modelDisposable.add(handleMeetingCheckInDeepLinkAfterApproval(url)
                .doOnSubscribe(disposable -> {
                    updateAsSideEffect(isLoading, true);
                    removeError(meetingError);
                })
                .doOnError(throwable -> {
                    ViewError.Builder errorBuilder = createErrorBuilder(throwable)
                            .withTitle(R.string.error_check_in_failed)
                            .removeWhenShown();

                    if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                        errorBuilder.withDescription(R.string.error_location_not_found);
                    }

                    meetingError = errorBuilder.build();
                    addError(meetingError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> Timber.i("Joined private meeting"),
                        throwable -> Timber.w("Unable to join private meeting: %s", throwable.toString())
                ));
    }

    public void onPrivateMeetingJoinDismissed(@NonNull String url) {
        updateAsSideEffect(getShowCameraPreview(), new CameraRequest(true, true));
    }

    public void onPrivateMeetingCreationRequested() {
        modelDisposable.add(createPrivateMeeting()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            Timber.i("Meeting created");
                            if (isCurrentDestinationId(R.id.checkInFragment)) {
                                navigationController.navigate(R.id.action_checkInFragment_to_meetingFragment, bundle.getValue());
                            }
                        },
                        throwable -> Timber.w("Unable to create meeting: %s", throwable.toString())
                ));
    }

    public void onPrivateMeetingCreationDismissed() {
        updateAsSideEffect(getShowCameraPreview(), new CameraRequest(true, true));
    }

    public void onContactDataMissingDialogDismissed() {
        updateAsSideEffect(getShowCameraPreview(), new CameraRequest(true, true));
    }

    public void onUpdateRequiredDialogDismissed() {
        updateAsSideEffect(getShowCameraPreview(), new CameraRequest(true, true));
    }

    private Completable persistSkipCheckInConfirmation(boolean skipCheckInConfirmation) {
        return preferencesManager.persist(KEY_SKIP_CHECK_IN_CONFIRMATION, skipCheckInConfirmation);
    }

    private Completable createPrivateMeeting() {
        return meetingManager.createPrivateMeeting()
                .doOnSubscribe(disposable -> {
                    Timber.d("Creating meeting");
                    updateAsSideEffect(isLoading, true);
                    removeError(meetingError);
                })
                .doOnError(throwable -> {
                    meetingError = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title)
                            .removeWhenShown()
                            .build();
                    addError(meetingError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    public static Single<UUID> getScannerIdFromUrl(@NonNull String url) {
        return Single.fromCallable(() -> UUID.fromString(Uri.parse(url).getLastPathSegment()));
    }

    public static Maybe<String> getEncodedAdditionalDataFromUrlIfAvailable(@NonNull String url) {
        return Maybe.fromCallable(
                () -> {
                    int startIndex = url.indexOf('#') + 1;
                    if (startIndex < 1 || startIndex >= url.length()) {
                        return null;
                    }
                    int endIndex = url.length();
                    if (url.contains("/CWA")) {
                        endIndex = url.indexOf("/CWA");
                    }
                    return url.substring(startIndex, endIndex);
                });
    }

    public static Maybe<String> getAdditionalDataFromUrlIfAvailable(@NonNull String url) {
        return getEncodedAdditionalDataFromUrlIfAvailable(url)
                .flatMapSingle(SerializationUtil::deserializeFromBase64)
                .map(String::new);
    }

    private static boolean isDeepLink(@NonNull String data) {
        return URLUtil.isHttpsUrl(data) && data.contains("luca-app.de");
    }

    public LiveData<Bundle> getBundle() {
        return bundle;
    }

    public void setBundle(@Nullable Bundle bundle) {
        this.bundle.setValue(bundle);
    }

    public LiveData<ViewEvent<String>> getPossibleDocumentData() {
        return possibleDocumentData;
    }

    public LiveData<Bitmap> getQrCode() {
        return qrCode;
    }

    public LiveData<Boolean> isNetworkAvailable() {
        return networkAvailable;
    }

    public LiveData<Boolean> isUpdateRequired() {
        return updateRequired;
    }

    public LiveData<Boolean> isContactDataMissing() {
        return contactDataMissing;
    }

    public LiveData<ViewEvent<CheckInData>> getCheckInData() {
        return checkInData;
    }

    public LiveData<ViewEvent<Pair<String, String>>> getConfirmCheckIn() {
        return confirmCheckIn;
    }

    public LiveData<ViewEvent<Pair<String, String>>> getVoluntaryCheckIn() {
        return voluntaryCheckIn;
    }

    public LiveData<ViewEvent<String>> getConfirmPrivateMeeting() {
        return privateMeetingUrl;
    }

}
