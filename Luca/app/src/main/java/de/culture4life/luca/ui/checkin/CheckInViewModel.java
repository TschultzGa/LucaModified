package de.culture4life.luca.ui.checkin;

import static de.culture4life.luca.crypto.HashProvider.TRIMMED_HASH_LENGTH;
import static de.culture4life.luca.document.DocumentManager.HasDocumentCheckResult.VALID_DOCUMENT;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_BOOSTERED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_NOT_SHARED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_PCR_TESTED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_QUICK_TESTED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_RECOVERED;
import static de.culture4life.luca.ui.checkin.QrCodeData.ENTRY_POLICY_VACCINATED;

import android.app.Application;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import de.culture4life.luca.R;
import de.culture4life.luca.checkin.CheckInData;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.crypto.AsymmetricCipherProvider;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.crypto.DailyPublicKeyData;
import de.culture4life.luca.crypto.TraceIdWrapper;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.meeting.MeetingAdditionalData;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.pojo.LocationResponseData;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseQrCodeCallback;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.ViewEvent;
import de.culture4life.luca.ui.checkin.flow.children.ConfirmCheckInViewModel;
import de.culture4life.luca.ui.checkin.flow.children.EntryPolicyViewModel;
import de.culture4life.luca.ui.checkin.flow.children.VoluntaryCheckInViewModel;
import de.culture4life.luca.util.LucaUrlUtil;
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

public class CheckInViewModel extends BaseViewModel implements BaseQrCodeCallback {

    private static final UUID DEBUGGING_SCANNER_ID = UUID.fromString("1444c1a2-1922-4c11-813d-710d9f901227");
    private static final long CHECK_IN_POLLING_INTERVAL = TimeUnit.SECONDS.toMillis(3);
    public static final boolean FEATURE_ANONYMOUS_CHECKIN_DISABLED = true;
    public static final boolean FEATURE_ENTRY_POLICY_CHECKIN_DISABLED = true;

    private final RegistrationManager registrationManager;
    private final CheckInManager checkInManager;
    private final CryptoManager cryptoManager; // initialization deferred to first use
    private final MeetingManager meetingManager;
    private final NetworkManager networkManager;
    private final DocumentManager documentManager;

    private final MutableLiveData<Bundle> bundle = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<String>> possibleDocumentData = new MutableLiveData<>();
    private final MutableLiveData<Bitmap> qrCode = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<CheckInData>> checkInData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> networkAvailable = new MutableLiveData<>();
    private final MutableLiveData<Boolean> contactDataMissing = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<String>> privateMeetingUrl = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Pair<String, LocationResponseData>>> checkInMultiConfirm = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Boolean>> showCameraPreview = new MutableLiveData<>();
    private final MutableLiveData<Boolean> dailyPublicKeyAvailable = new MutableLiveData<>();

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

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.fromAction(() -> checkInData.postValue(null)))
                .andThen(Completable.mergeArray(
                        registrationManager.initialize(application),
                        checkInManager.initialize(application),
                        meetingManager.initialize(application),
                        networkManager.initialize(application),
                        documentManager.initialize(application)
                ))
                .andThen(registrationManager.getUserIdIfAvailable()
                        .doOnSuccess(uuid -> this.userId = uuid)
                        .ignoreElement())
                .andThen(invoke(updateDailyKeyAvailability()))
                .andThen(invokeHandleDeepLinkIfAvailable())
                .andThen(invokeShowCameraPreviewInitialization());
    }

    private Completable updateDailyKeyAvailability() {
        return cryptoManager.initialize(application)
                .andThen(cryptoManager.hasDailyPublicKey())
                .flatMapCompletable(hasDailyPublicKey -> update(dailyPublicKeyAvailable, hasDailyPublicKey));
    }

    @NonNull
    private Completable invokeShowCameraPreviewInitialization() {
        return invokeDelayed(update(showCameraPreview, new ViewEvent<>(true)), 100);
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
                //.flatMapCompletable(hasProvidedRequiredData -> update(contactDataMissing, !hasProvidedRequiredData))
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
        return preferencesManager.restoreOrDefault(CheckInManager.KEY_INCLUDE_ENTRY_POLICY, false)
                .flatMap(includeEntryPolicy -> generateQrCodeData(this.userId, false, includeEntryPolicy));
    }

    @VisibleForTesting
    protected Single<QrCodeData> generateQrCodeData(UUID actualUserId, boolean isAnonymous, boolean shareEntryPolicy) {
        Timber.i("CheckInViewModel.generateQrCodeData(%s, %b, %b)", actualUserId.toString(), isAnonymous, shareEntryPolicy);
        return cryptoManager.initialize(application)
                .andThen(Single.just(new QrCodeData()))
                .flatMap(qrCodeData -> checkInManager.getTraceIdWrapper(actualUserId)
                        .flatMapCompletable(userTraceIdWrapper -> Completable.mergeArray(
                                cryptoManager.getDailyPublicKey()
                                        .map(DailyPublicKeyData::getId)
                                        .doOnSuccess(qrCodeData::setKeyId)
                                        .doOnError(err -> Timber.i("GenerateQRCodeData -> Cannot get daily public key"))
                                        .ignoreElement(),
                                CheckInManager.getGuestEphemeralKeyPairAlias(userTraceIdWrapper.getTraceId())
                                        .flatMap(cryptoManager::getKeyPair)
                                        .doOnError(err -> Timber.i("Cannoit get guest ephermeral key pair alias"))
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
                                getQrCodeEntryPolicy(shareEntryPolicy)
                                        .doOnSuccess(qrCodeData::setEntryPolicy)
                                        .ignoreElement(),
                                Completable.fromAction(() -> qrCodeData.setTraceId(userTraceIdWrapper.getTraceId()))))
                .andThen(Single.just(qrCodeData)))
                .doOnSuccess(qrCodeData -> Timber.i("We have the qrcode data %s %s", qrCodeData.getTraceId(), qrCodeData.getUserEphemeralPublicKey()))
                .doOnError(err -> Timber.i("Generate qr code data failed"));
    }

    private Single<Byte> getQrCodeEntryPolicy(boolean shareEntryPolicy) {
        return Single.just(shareEntryPolicy)
                .flatMap(includeEntryPolicy -> {
                    if (!includeEntryPolicy) {
                        return Single.just(ENTRY_POLICY_NOT_SHARED);
                    } else {
                        return Maybe.mergeArray(
                                getEntryPolicyIfAvailable(documentManager.hasQuickTestDocument(), ENTRY_POLICY_QUICK_TESTED),
                                getEntryPolicyIfAvailable(documentManager.hasPcrTestDocument(), ENTRY_POLICY_PCR_TESTED),
                                getEntryPolicyIfAvailable(documentManager.hasRecoveryDocument(), ENTRY_POLICY_RECOVERED),
                                getEntryPolicyIfAvailable(documentManager.hasVaccinationDocument(), ENTRY_POLICY_VACCINATED),
                                getEntryPolicyIfAvailable(documentManager.hasBoosterDocument(), ENTRY_POLICY_BOOSTERED)
                        ).reduce(Integer::sum).defaultIfEmpty(ENTRY_POLICY_NOT_SHARED);
                    }
                })
                .map(entryPolicy -> (byte) (int) entryPolicy);
    }

    private Maybe<Integer> getEntryPolicyIfAvailable(Single<DocumentManager.HasDocumentCheckResult> hasDocument, @QrCodeData.EntryPolicy int entryPolicy) {
        return hasDocument.flatMapMaybe(available -> {
            if (available == VALID_DOCUMENT) {
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
                            qrCodeData.setEncryptedData(new byte[32]);
                        })
                        .ignoreElement(),
                generateVerificationTag(new byte[32], userTraceIdWrapper.getTimestamp())
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
                .flatMap(encodedData -> cryptoManager.ecdh(userEphemeralPrivateKey)
                        .flatMap(cryptoManager::generateDataEncryptionSecret)
                        .flatMap(CryptoManager::createKeyFromSecret)
                        .flatMap(encodingKey -> cryptoManager.getSymmetricCipherProvider().encrypt(encodedData, iv, encodingKey)));
    }

    private Single<byte[]> generateVerificationTag(@NonNull byte[] encryptedUserIdAndSecret, long roundedUnixTimestamp) {
        return TimeUtil.encodeUnixTimestamp(roundedUnixTimestamp)
                .flatMap(encodedTimestamp -> CryptoManager.concatenate(encodedTimestamp, encryptedUserIdAndSecret))
                .flatMap(encodedData -> cryptoManager.getDataSecret()
                        .flatMap(cryptoManager::generateDataAuthenticationSecret)
                        .flatMap(dataAuthenticationSecret -> cryptoManager.hmac(encodedData, dataAuthenticationSecret)))
                .flatMap(verificationTag -> CryptoManager.trim(verificationTag, 8))
                .doOnSuccess(verificationTag -> Timber.d("Generated new verification tag: %s", SerializationUtil.toBase64(verificationTag).blockingGet()));
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
                .flatMap(encodedQrCodeData -> cryptoManager.hash(encodedQrCodeData)
                        .flatMap(checksum -> CryptoManager.trim(checksum, 4))
                        .flatMap(checksum -> CryptoManager.concatenate(encodedQrCodeData, checksum)))
                .flatMap(SerializationUtil::toBase32);
    }

    private Single<Bitmap> generateQrCode(@NonNull String data) {
        return Single.fromCallable(() -> QRCode.from(data)
                .withSize(500, 500)
                .withHint(EncodeHintType.MARGIN, 0)
                .bitmap());
    }

    /*
        QR code scanning
    */

    public boolean canProcessBarcode(@NonNull String url) {
        return LucaUrlUtil.isSelfCheckIn(url) || LucaUrlUtil.isPrivateMeeting(url);
    }

    @Override
    @NonNull
    public Completable processBarcode(@NonNull String barcodeData) {
        return isDocument(barcodeData)
                .flatMapCompletable(isDocument -> {
                            if (isDocument) {
                                return update(possibleDocumentData, new ViewEvent<>(barcodeData));
                            } else {
                                return process(barcodeData);
                            }
                        }
                ).doOnSubscribe(disposable -> {
                    removeError(deepLinkError);
                    updateAsSideEffect(showCameraPreview, new ViewEvent<>(false));
                    updateAsSideEffect(isLoading, true);
                }).doFinally(() -> {
                    updateAsSideEffect(isLoading, false);
                });
    }

    private Single<Boolean> isDocument(@NonNull String barcodeData) {
        return Single.defer(() -> {
            if (LucaUrlUtil.isTestResult(barcodeData)) {
                return DocumentManager.getEncodedDocumentFromDeepLink(barcodeData);
            } else {
                return Single.just(barcodeData);
            }
        })
                .flatMap(documentManager::parseAndValidateEncodedDocument)
                .map(document -> true)
                .onErrorReturnItem(false);
    }

    public Completable process(@NonNull String barcodeData) {
        Timber.d("CheckInViewModel.process(%s)", barcodeData);
        return Single.just(barcodeData)
                .doOnSuccess(value -> Timber.d("Processing barcode: %s", value))
                .flatMapCompletable(this::handleDeepLink);
    }

    /*
        Deep link handling
     */

    private Completable invokeHandleDeepLinkIfAvailable() {
        return invoke(application.getDeepLink()
                .flatMapCompletable(url -> handleDeepLink(url).doFinally(() -> application.onDeepLinkHandled(url))));
    }

    private Completable handleDeepLink(@NonNull String url) {
        Timber.i("CheckInViewModel.handleDeepLink(%s)", url);
        return Completable.defer(() -> {
            if (LucaUrlUtil.isPrivateMeeting(url)) {
                return handleMeetingCheckInDeepLink(url);
            } else if (LucaUrlUtil.isSelfCheckIn(url)) {
                return processConfirmCheckInFlow(url);
            } else {
                return Completable.error(new InvalidCheckInLinkException());
            }
        }).doOnSubscribe(disposable -> {
            removeError(deepLinkError);
            updateAsSideEffect(showCameraPreview, new ViewEvent<>(false));
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
                .getRegistrationData()
                .map(MeetingAdditionalData::new)
                .map(meetingAdditionalData -> new Gson().toJson(meetingAdditionalData));

        return extractMeetingHostName.andThen(Single.zip(scannerId, additionalData, Pair::new))
                .flatMapCompletable(scannerIdAndAdditionalData ->
                        performSelfCheckIn(
                                scannerIdAndAdditionalData.first,
                                scannerIdAndAdditionalData.second,
                                true,
                                false,
                                false
                        )
                );
    }

    private static Single<MeetingAdditionalData> getMeetingAdditionalDataFromUrl(@NonNull String url) {
        return getAdditionalDataFromUrlIfAvailable(url)
                .toSingle()
                .map(json -> new Gson().fromJson(json, MeetingAdditionalData.class));
    }

    private Completable processConfirmCheckInFlow(@NonNull String url) {
        Timber.i("CheckInViewModel.processConfirmCheckInFlow(%s)", url);
        return getScannerIdFromUrl(url)
                .flatMap(uuid -> checkInManager.getLocationDataFromScannerId(uuid.toString()))
                .flatMapCompletable(locationResponseData ->
                        shouldShowConfirmCheckInFlow(locationResponseData)
                                .flatMapCompletable(shouldShow -> {
                                    if (shouldShow) {
                                        Timber.i("CheckInViewModel.processConfirmCheckInFlow -> asking if want to check in");
                                        return update(checkInMultiConfirm, new ViewEvent<>(new Pair<>(url, locationResponseData)));
                                    } else {
                                        // check-in directly, get checkin parameters from location and user settings
                                        Timber.i("Go directly to handleSelfCheckInDeppLink (called from processConfirmationCheckInFlow)");
                                        return handleSelfCheckInDeepLink(
                                                url,
                                                isCheckInAnonymous(locationResponseData),
                                                isShareEntryPolicyState(locationResponseData));
                                    }
                                })
                );
    }

    private Single<Boolean> shouldShowConfirmCheckInFlow(@NonNull LocationResponseData locationResponseData) {
        return Single.zip(
                preferencesManager.restoreOrDefault(ConfirmCheckInViewModel.KEY_SKIP_CHECK_IN_CONFIRMATION, false),
                preferencesManager.restoreOrDefault(VoluntaryCheckInViewModel.KEY_ALWAYS_CHECK_IN_VOLUNTARY, false),
                preferencesManager.restoreOrDefault(EntryPolicyViewModel.KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, false),
                (alwaysSkipConfirmation, alwaysCheckInVoluntary, alwaysShareEntryPolicy) -> {
                    boolean shouldShowEntryPolicyPage = (!alwaysShareEntryPolicy && locationResponseData.getEntryPolicy() != null && !FEATURE_ENTRY_POLICY_CHECKIN_DISABLED);
                    boolean shouldShowVoluntaryCheckInPage = (!alwaysCheckInVoluntary && !locationResponseData.isContactDataMandatory() && !FEATURE_ANONYMOUS_CHECKIN_DISABLED);
                    boolean shouldShowCheckInConfirmationPage = !alwaysSkipConfirmation && (locationResponseData.isContactDataMandatory() || FEATURE_ANONYMOUS_CHECKIN_DISABLED);
                    return shouldShowEntryPolicyPage || shouldShowVoluntaryCheckInPage || shouldShowCheckInConfirmationPage;
                }
        );
    }

    private boolean isCheckInAnonymous(@NonNull LocationResponseData locationResponseData) {
        boolean alwaysCheckInVoluntary = preferencesManager
                .restoreOrDefault(EntryPolicyViewModel.KEY_ALWAYS_SHARE_ENTRY_POLICY_STATUS, false)
                .blockingGet();

        return (locationResponseData.getEntryPolicy() != null && alwaysCheckInVoluntary) && !FEATURE_ANONYMOUS_CHECKIN_DISABLED;
    }

    private boolean isShareEntryPolicyState(@NonNull LocationResponseData locationResponseData) {
        boolean shareEntryPolicyStatus = preferencesManager
                .restoreOrDefault(VoluntaryCheckInViewModel.KEY_ALWAYS_CHECK_IN_VOLUNTARY, false)
                .blockingGet();

        return (!locationResponseData.isContactDataMandatory() && shareEntryPolicyStatus) && !FEATURE_ENTRY_POLICY_CHECKIN_DISABLED;
    }

    public Completable handleSelfCheckInDeepLink(@NonNull String url) {
        return handleSelfCheckInDeepLink(url, false, false);
    }

    public Completable handleSelfCheckInDeepLink(@NonNull String url, boolean isAnonymous, boolean shareEntryPolicyState) {
        Timber.i("CheckInViewModel.handleSelfCheckInDeepLink(%s, %b, %b)", url, isAnonymous, shareEntryPolicyState);
        UUID scannerId = getScannerIdFromUrl(url)
                .doOnError((err) -> Timber.i("ScannerId cannot be optained") )
                .onErrorComplete()
                .blockingGet();
        Single<String> additionalData = getAdditionalDataFromUrlIfAvailable(url).defaultIfEmpty("");

        Timber.i("ScannerId: %s AdditionalData: %s", scannerId.toString(), additionalData);
        return Single.zip(Single.just(scannerId), additionalData, Pair::new)
                .flatMapCompletable(scannerIdAndAdditionalData -> performSelfCheckIn(
                        scannerIdAndAdditionalData.first,
                        scannerIdAndAdditionalData.second,
                        false,
                        isAnonymous,
                        shareEntryPolicyState
                ))
                .doOnError(err -> Timber.i(err,"Failed in performedSelfCheckin (handleSelfCheckInDeepLink) called it"));
    }

    private Completable performSelfCheckIn(
            UUID scannerId,
            @Nullable String additionalData,
            boolean requirePrivateMeeting,
            boolean isAnonymousCheckIn,
            boolean shareEntryPolicyState) {
            Timber.i("WELL GET THE UUIDS AND GENERATE THE QR CODE DATA FOR ALL OF THEM");
            ArrayList<String> uuids = preferencesManager.restoreOrDefault(RegistrationManager.ALL_REGISTERED_UUIDS, new ArrayList<String>())
                    .onErrorComplete()
                    .doOnError(err -> Timber.e(err, "Could not get"))
                    .blockingGet();
            Timber.i("YO this is the uuids: %d", uuids.size());

            List<Completable> completableList = new ArrayList<>();
            for (int i=0; i < uuids.size(); i++) { // uh butt better for debugging
                UUID uuid = UUID.fromString(uuids.get(i));
                completableList.add(generateQrCodeData(uuid, isAnonymousCheckIn || requirePrivateMeeting, shareEntryPolicyState)
                        //.doOnError(err -> Timber.i("Failed right before calling checkinManager.checkIn(scannerId, qrCodeData)"))
                        .flatMapCompletable(qrCodeData -> checkInManager.checkIn(scannerId, qrCodeData))
                        //.doOnError(err -> Timber.i("Failed somewhere in checkinManager.checkIn (performSelfCheckin called it)"))
                        .andThen(Completable.fromAction(() -> uploadAdditionalDataIfAvailableAsSideEffect(scannerId, additionalData)))
                        //.doOnError(err -> Timber.i("Failed in uploadAdditionalDataIfAvailableAsSideEffect (performSelfCheckin called it"))
                        //.doOnSubscribe((o) -> Timber.i("YO SOMEONE SUBSCRIBED EHRE TO THE COOOL STUFF")));
                        .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true)));
                Timber.i("Added generateQrCodeData completable chain for uuid %s", uuid.toString());
            }

            Timber.i("YO we have %d completables", completableList.size());
            return Completable.mergeArray(completableList.toArray(completableList.toArray(new Completable[0])));
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
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.i("Checked in"),
                        throwable -> Timber.w("Unable to check in: %s", throwable.toString())
                ));
    }

    public void onCheckInRequested(@NonNull String url, boolean isAnonymous, boolean shareEntryPolicyStatus) {
        handleSelfCheckInDeepLink(url, isAnonymous, shareEntryPolicyStatus)
                .onErrorComplete()
                .doOnSubscribe(disposable -> {
                    updateAsSideEffect(isLoading, true);
                })
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    public void onCheckInMultiConfirmDismissed() {
        updateAsSideEffect(showCameraPreview, new ViewEvent<>(true));
    }

    public void onImportDocumentConfirmationDismissed() {
        updateAsSideEffect(showCameraPreview, new ViewEvent<>(true));
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
        updateAsSideEffect(showCameraPreview, new ViewEvent<>(true));
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
        updateAsSideEffect(showCameraPreview, new ViewEvent<>(true));
    }

    public void onContactDataMissingDialogDismissed() {
        updateAsSideEffect(showCameraPreview, new ViewEvent<>(true));
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
        return Single.fromCallable(() -> {
                    if (!LucaUrlUtil.isSelfCheckIn(url)) {
                        throw new IllegalArgumentException("Not a valid check-in URL: " + url);
                    }
                    Timber.i("getScannerIdFromUrl %s", url);
                    return UUID.fromString(Uri.parse(url).getLastPathSegment());
                }
        );
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
                .flatMapSingle(SerializationUtil::fromBase64)
                .map(String::new);
    }

    public LiveData<Bundle> getBundleLiveData() {
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

    public LiveData<Boolean> isContactDataMissing() {
        return contactDataMissing;
    }

    public LiveData<ViewEvent<CheckInData>> getCheckInData() {
        return checkInData;
    }

    public LiveData<ViewEvent<Pair<String, LocationResponseData>>> getCheckInMultiConfirm() {
        return checkInMultiConfirm;
    }

    @NonNull
    public LiveData<ViewEvent<String>> getConfirmPrivateMeeting() {
        return privateMeetingUrl;
    }

    @NonNull
    public LiveData<ViewEvent<Boolean>> getShowCameraPreview() {
        return showCameraPreview;
    }

    @NonNull
    public LiveData<Boolean> isDailyPublicKeyAvailable() {
        return dailyPublicKeyAvailable;
    }
}
