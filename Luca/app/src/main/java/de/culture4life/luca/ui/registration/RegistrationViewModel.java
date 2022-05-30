package de.culture4life.luca.ui.registration;

import static java.net.HttpURLConnection.HTTP_BAD_GATEWAY;
import static de.culture4life.luca.registration.RegistrationManager.REGISTRATION_COMPLETED_KEY;
import static de.culture4life.luca.registration.RegistrationManager.REGISTRATION_DATA_KEY;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.LucaApplication;
import de.culture4life.luca.R;
import de.culture4life.luca.connect.ConnectManager;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.health.HealthDepartmentManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.registration.RegistrationData;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.util.StringSanitizeUtil;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import timber.log.Timber;

public class RegistrationViewModel extends BaseViewModel {

    public static final String GERMAN_REGION_CODE = "DE";
    public static final long DEBOUNCE_DURATION = 100;

    public static final boolean SKIP_PHONE_NUMBER_VERIFICATION = LucaApplication.IS_USING_STAGING_ENVIRONMENT;
    private static final int MAXIMUM_TAN_CHALLENGE_IDS = 10;
    private static final long INITIAL_TAN_REQUESTS_TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    public static final String LAST_VERIFIED_PHONE_NUMBER_KEY = "last_verified_phone_number";
    public static final String PHONE_VERIFICATION_COMPLETED_KEY = "phone_verification_completed";
    public static final String RECENT_TAN_CHALLENGE_IDS_KEY = "recent_tan_challenge_ids";
    public static final String LAST_TAN_REQUEST_TIMESTAMP_KEY = "last_tan_request_timestamp";
    public static final String NEXT_TAN_REQUEST_TIMEOUT_DURATION_KEY = "next_tan_request_timeout_duration";

    private final RegistrationManager registrationManager;
    private final PreferencesManager preferencesManager;
    private final DocumentManager documentManager;
    private final HealthDepartmentManager healthDepartmentManager;
    private final ConnectManager connectManager;
    private final PhoneNumberUtil phoneNumberUtil;

    private final MutableLiveData<Double> progress = new MutableLiveData<>(0D);
    private final MutableLiveData<String> firstName = new MutableLiveData<>();
    private final MutableLiveData<String> lastName = new MutableLiveData<>();
    private final MutableLiveData<String> phoneNumber = new MutableLiveData<>();
    private final MutableLiveData<String> email = new MutableLiveData<>();
    private final MutableLiveData<String> street = new MutableLiveData<>();
    private final MutableLiveData<String> houseNumber = new MutableLiveData<>();
    private final MutableLiveData<String> city = new MutableLiveData<>();
    private final MutableLiveData<String> postalCode = new MutableLiveData<>();

    private final Map<LiveData<String>, MutableLiveData<Boolean>> validationStatuses;
    private final Map<LiveData<String>, BehaviorSubject<String>> formValueSubjects;

    private final MutableLiveData<Boolean> shouldRequestNewVerificationTan = new MutableLiveData<>(true);
    private final MutableLiveData<Long> nextPossibleTanRequestTimestamp = new MutableLiveData<>();
    private final MutableLiveData<Boolean> completed = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> connectEnrollmentStatus = new MutableLiveData<>(false);

    private RegistrationData registrationData;
    private ViewError registrationError;

    private boolean isInEditMode;
    private boolean shouldReImportTestData;
    private boolean isNameChanged;
    private boolean isPostalCodeChanged;
    private boolean isAnyContactDataChanged;
    private boolean isRegisteringUser;

    public RegistrationViewModel(@NonNull Application application) {
        super(application);
        preferencesManager = this.application.getPreferencesManager();
        registrationManager = this.application.getRegistrationManager();
        documentManager = this.application.getDocumentManager();
        healthDepartmentManager = this.application.getHealthDepartmentManager();
        connectManager = this.application.getConnectManager();
        phoneNumberUtil = PhoneNumberUtil.getInstance();

        formValueSubjects = new HashMap<>();
        validationStatuses = new HashMap<>();

        List<MutableLiveData<String>> formDataFields = Arrays.asList(firstName, lastName, phoneNumber, email, street, houseNumber, city, postalCode);
        for (MutableLiveData<String> formData : formDataFields) {
            // set empty value
            formData.setValue("");

            // create validation status
            MutableLiveData<Boolean> validationStatus = new MutableLiveData<>();
            validationStatus.setValue(false);
            validationStatuses.put(formData, validationStatus);

            // create value subject
            BehaviorSubject<String> formValueSubject = BehaviorSubject.createDefault(formData.getValue());
            formValueSubjects.put(formData, formValueSubject);
        }
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.mergeArray(
                        initializeEditMode(),
                        initializeRegistrationData(),
                        resetTanRequestRateLimits(),
                        updateNextPossibleTanRequestTimestamp(),
                        updateShouldRequestNewTan(),
                        updateProgress().delaySubscription(100, TimeUnit.MILLISECONDS)
                ));
    }

    private Completable initializeRegistrationData() {
        return registrationManager.getRegistrationData()
                .doOnSuccess(registrationData -> this.registrationData = registrationData)
                .ignoreElement();
    }

    private Completable initializeEditMode() {
        return preferencesManager.restoreOrDefault(REGISTRATION_COMPLETED_KEY, false)
                .doOnSuccess(registrationCompleted -> isInEditMode = registrationCompleted)
                .ignoreElement();
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                validateFormValueChanges(),
                observeRegistrationDataChanges(),
                observePhoneNumberVerificationChanges(),
                keepConnectEnrollmentStatusUpdated()
        );
    }

    private Completable observeRegistrationDataChanges() {
        return preferencesManager.restoreIfAvailableAndGetChanges(REGISTRATION_DATA_KEY, RegistrationData.class)
                .doOnNext(updatedRegistrationData -> {
                    Timber.d("Restored registration data: %s", updatedRegistrationData);
                    this.registrationData = updatedRegistrationData;
                })
                .flatMapCompletable(registrationData -> updateFormValuesWithRegistrationData());
    }

    private Completable observePhoneNumberVerificationChanges() {
        return preferencesManager.restoreIfAvailableAndGetChanges(LAST_VERIFIED_PHONE_NUMBER_KEY, String.class)
                .flatMapCompletable(lastVerifiedNumber -> Completable.fromAction(() -> {
                    registrationData.setPhoneNumber(lastVerifiedNumber);
                    updateAsSideEffect(phoneNumber, lastVerifiedNumber);
                }));
    }

    private Completable validateFormValueChanges() {
        return Completable.mergeArray(
                validateFormValueChanges(firstName, RegistrationViewModel::isValidName),
                validateFormValueChanges(lastName, RegistrationViewModel::isValidName),
                validateFormValueChanges(phoneNumber, this::isValidPhoneNumber),
                validateFormValueChanges(email, RegistrationViewModel::isValidEMailAddress),
                validateFormValueChanges(street, RegistrationViewModel::isValidStreet),
                validateFormValueChanges(houseNumber, RegistrationViewModel::isValidHouseNumber),
                validateFormValueChanges(city, RegistrationViewModel::isValidCity),
                validateFormValueChanges(postalCode, RegistrationViewModel::isValidPostalCode)
        );
    }

    private Completable validateFormValueChanges(MutableLiveData<String> data, ValidationMethod validationMethod) {
        return Objects.requireNonNull(formValueSubjects.get(data))
                .map(StringSanitizeUtil::sanitize)
                .map(String::trim)
                .doOnNext(data::postValue)
                .debounce(DEBOUNCE_DURATION, TimeUnit.MILLISECONDS)
                .distinctUntilChanged()
                .map(validationMethod::isValid)
                .switchMapCompletable(isValid -> Completable.defer(() -> {
                    MutableLiveData<Boolean> validationStatus = getValidationStatus(data);
                    boolean hasValidationChanged = isValid != validationStatus.getValue();
                    if (hasValidationChanged) {
                        return update(validationStatus, isValid)
                                .andThen(updateProgress().delaySubscription(500, TimeUnit.MILLISECONDS));
                    } else {
                        return Completable.complete();
                    }
                }))
                .subscribeOn(Schedulers.computation());
    }

    public void onUserDataUpdateRequested() {
        modelDisposable.add(updateRegistrationDataWithFormValues()
                .andThen(registrationManager.updateUser())
                .andThen(persistUserDataUpdateInHistory())
                .andThen(additionalStepsAfterUpdate())
                .doOnSubscribe(disposable -> {
                    updateAsSideEffect(isLoading, true);
                    removeError(registrationError);
                })
                .doOnComplete(() -> updateAsSideEffect(completed, true))
                .doOnError(throwable -> {
                    registrationError = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title)
                            .withResolveAction(Completable.fromAction(this::onUserDataUpdateRequested))
                            .withResolveLabel(R.string.action_retry)
                            .build();
                    addError(registrationError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.i("User data updated"),
                        throwable -> Timber.w(throwable, "Unable to update user data: %s", throwable.toString())
                ));
    }

    private Completable additionalStepsAfterUpdate() {
        return Completable.mergeArray(
                updateResponsibleHealthDepartmentIfRequired(),
                unEnrollIfRequired(),
                updateSharedDataIfRequired(),
                reImportDocumentsIfRequired()
        );
    }

    private Completable updateResponsibleHealthDepartmentIfRequired() {
        return Single.fromCallable(this::isPostalCodeChanged)
                .filter(postalCodeChanged -> postalCodeChanged)
                .flatMapCompletable(postalCodeChanged ->
                        healthDepartmentManager.deleteResponsibleHealthDepartment()
                                .andThen(healthDepartmentManager.updateResponsibleHealthDepartmentIfRequired()
                                ));
    }

    private Completable unEnrollIfRequired() {
        return Single.fromCallable(this::getShouldUnEnrollLucaConnect)
                .filter(shouldUnEnroll -> shouldUnEnroll)
                .flatMapCompletable(shouldUnEnroll -> connectManager.unEnroll());
    }

    private Completable updateSharedDataIfRequired() {
        return Single.fromCallable(this::getShouldUpdateLucaConnectSharedData)
                .filter(shouldUpdate -> shouldUpdate)
                .flatMapCompletable(shouldUpdate -> connectManager.reEnroll());
    }

    private Completable reImportDocumentsIfRequired() {
        return Single.fromCallable(this::getShouldReImportTestData)
                .filter(shouldReImport -> shouldReImport)
                .flatMapCompletable(shouldReImport -> documentManager.reImportDocuments());
    }

    public void onRegistrationRequested() {
        if (isRegisteringUser) {
            // prevent multiple calls due to double click
            return;
        }
        isRegisteringUser = true;
        modelDisposable.add(updateRegistrationDataWithFormValues()
                .andThen(registrationManager.registerUser())
                .andThen(persistUserDataUpdateInHistory())
                .andThen(healthDepartmentManager.invokeResponsibleHealthDepartmentUpdateIfRequired())
                .doOnSubscribe(disposable -> {
                    updateAsSideEffect(isLoading, true);
                    removeError(registrationError);
                })
                .doOnComplete(() -> updateAsSideEffect(completed, true))
                .doOnError(throwable -> {
                    registrationError = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title)
                            .withResolveAction(Completable.fromAction(this::onRegistrationRequested))
                            .withResolveLabel(R.string.action_retry)
                            .build();
                    addError(registrationError);
                })
                .doFinally(() -> {
                    updateAsSideEffect(isLoading, false);
                    isRegisteringUser = false;
                })
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Timber.i("User registered"),
                        throwable -> Timber.w("Unable to register user")
                ));
    }

    public void updateRegistrationDataWithFormValuesAsSideEffect() {
        modelDisposable.add(updateRegistrationDataWithFormValues()
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe());
    }

    private Completable persistUserDataUpdateInHistory() {
        return application.getHistoryManager()
                .addContactDataUpdateItem(registrationData);
    }

    public Completable updateRegistrationDataWithFormValues() {
        return Completable.mergeArray(
                updatePhoneNumberVerificationStatus(),
                updateShouldReImportingTestData(),
                updateLucaConnectUpdatesRequired()
        )
                .andThen(Single.fromCallable(() -> buildUpdateRegistrationData(registrationData)))
                .flatMapCompletable(data -> registrationManager.persistRegistrationData(registrationData));
    }

    private RegistrationData buildUpdateRegistrationData(RegistrationData registrationData) {
        registrationData.setId(registrationData.getId());
        registrationData.setRegistrationTimestamp(registrationData.getRegistrationTimestamp());
        registrationData.setFirstName(getSanitizedString(firstName.getValue()));
        registrationData.setLastName(getSanitizedString(lastName.getValue()));
        if (!isInEditMode || SKIP_PHONE_NUMBER_VERIFICATION) {
            registrationData.setPhoneNumber(getSanitizedString(phoneNumber.getValue()));
        }
        String email = this.email.getValue();
        if (email != null && (isValidEMailAddress(email) || email.isEmpty())) {
            // email is optional and should only be set when valid or empty (to delete value)
            registrationData.setEmail(email);
        }
        registrationData.setStreet(getSanitizedString(street.getValue()));
        registrationData.setHouseNumber(getSanitizedString(houseNumber.getValue()));
        registrationData.setCity(getSanitizedString(city.getValue()));
        registrationData.setPostalCode(getSanitizedString(postalCode.getValue()));
        return registrationData;
    }

    private Completable updateFormValuesWithRegistrationData() {
        return Completable.mergeArray(
                updateFormValue(firstName, registrationData.getFirstName()),
                updateFormValue(lastName, registrationData.getLastName()),
                updateFormValue(phoneNumber, registrationData.getPhoneNumber()),
                updateFormValue(email, registrationData.getEmail()),
                updateFormValue(street, registrationData.getStreet()),
                updateFormValue(houseNumber, registrationData.getHouseNumber()),
                updateFormValue(city, registrationData.getCity()),
                updateFormValue(postalCode, registrationData.getPostalCode())
        );
    }

    public Completable useDebugRegistrationData() {
        return Completable.fromAction(() -> {
            registrationData.setFirstName("Erika");
            registrationData.setLastName("Mustermann");
            registrationData.setPhoneNumber("+491711234567");
            registrationData.setEmail("erika.mustermann@example.de");
            registrationData.setStreet("Street");
            registrationData.setHouseNumber("123");
            registrationData.setPostalCode("12345");
            registrationData.setCity("City");
        }).andThen(Completable.mergeArray(
                registrationManager.persistRegistrationData(registrationData),
                updateFormValuesWithRegistrationData()
        ));
    }

    private Completable updateFormValue(MutableLiveData<String> liveData, @Nullable String newValue) {
        return Completable.fromAction(() -> {
            if (newValue == null) {
                return;
            }
            onFormValueChanged(liveData, newValue);
        });
    }

    private Completable updateProgress() {
        return Observable.defer(() -> Observable.fromIterable(validationStatuses.values()))
                .filter(LiveData::getValue)
                .count()
                .map(validValuesCount -> validValuesCount / (double) formValueSubjects.size())
                .flatMapCompletable(validValuesRatio -> update(progress, validValuesRatio));
    }

    public Completable updateShouldReImportingTestData() {
        Single<Boolean> hasDocuments = documentManager.getOrRestoreDocuments()
                .isEmpty()
                .map(isEmpty -> !isEmpty);

        Single<Boolean> isSameFirstName = Maybe.fromCallable(firstName::getValue)
                .map(value -> value.equals(registrationData.getFirstName()))
                .defaultIfEmpty(false);

        Single<Boolean> isSameLastName = Maybe.fromCallable(lastName::getValue)
                .map(value -> value.equals(registrationData.getLastName()))
                .defaultIfEmpty(false);

        Single<Boolean> hasRelevantDataChanged = Single.zip(isSameFirstName, isSameLastName, (sameFirstName, sameLastName) -> !sameFirstName || !sameLastName);

        return Single.zip(hasDocuments, hasRelevantDataChanged, (testResults, relevantDataChanged) -> testResults && relevantDataChanged)
                .doOnSuccess(reImportTestData -> {
                    Timber.d("Should re-import test data: %b", reImportTestData);
                    this.shouldReImportTestData = reImportTestData;
                }).ignoreElement();
    }

    public void onFormValueChanged(LiveData<String> liveData, @NonNull String newValue) {
        Objects.requireNonNull(formValueSubjects.get(liveData)).onNext(newValue);
    }

    public Completable updatePhoneNumberVerificationStatus() {
        return Single.fromCallable(phoneNumber::getValue)
                .flatMapCompletable(this::updatePhoneNumberVerificationStatus);
    }

    public Completable updatePhoneNumberVerificationStatus(@NonNull String currentNumber) {
        return preferencesManager.restoreOrDefault(LAST_VERIFIED_PHONE_NUMBER_KEY, "")
                .doOnSuccess(lastVerifiedNumber -> Timber.v("Last verified number: %s - Current number: %s", lastVerifiedNumber, currentNumber))
                .map(lastVerifiedNumber -> isSamePhoneNumber(lastVerifiedNumber, currentNumber))
                .flatMapCompletable(sameNumber -> {
                    if (sameNumber) {
                        return preferencesManager.persist(PHONE_VERIFICATION_COMPLETED_KEY, true);
                    } else {
                        Timber.d("Resetting verification, phone number changed: %s", currentNumber);
                        return Completable.mergeArray(
                                preferencesManager.persist(PHONE_VERIFICATION_COMPLETED_KEY, false),
                                updateShouldRequestNewTan()
                        );
                    }
                });
    }

    public Single<Boolean> getPhoneNumberVerificationStatus() {
        return Single.just(true);
    }

    public Completable requestPhoneNumberVerificationTan() {
        return assertTanRateLimitNotReached()
                .andThen(Single.fromCallable(this::getFormattedPhoneNumber))
                .flatMap(registrationManager::requestPhoneNumberVerificationTan)
                .flatMapCompletable(challengeId -> Completable.mergeArray(
                        addToRecentTanChallengeIds(challengeId),
                        preferencesManager.persist(LAST_TAN_REQUEST_TIMESTAMP_KEY, TimeUtil.getCurrentMillis())
                ))
                .andThen(incrementTanRequestTimeoutDuration())
                .doOnSubscribe(disposable -> {
                    removeError(registrationError);
                    updateAsSideEffect(isLoading, true);
                })
                .doOnComplete(() -> updateAsSideEffect(shouldRequestNewVerificationTan, false))
                .doOnError(throwable -> {
                    updateAsSideEffect(shouldRequestNewVerificationTan, true);
                    ViewError.Builder builder = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title);

                    if (throwable instanceof VerificationException) {
                        builder = builder
                                .withDescription(((VerificationException) throwable).getMessage());
                    } else {
                        builder = builder
                                .withResolveAction(Completable.fromAction(this::requestPhoneNumberVerificationTan))
                                .withResolveLabel(R.string.action_retry);

                        if (NetworkManager.isHttpException(throwable, 429)) {
                            builder = builder
                                    .withTitle(R.string.verification_rate_limit_title)
                                    .withDescription(R.string.verification_rate_limit_description);
                        } else if (NetworkManager.isHttpException(throwable, HTTP_BAD_GATEWAY)) {
                            builder = builder
                                    .withTitle(R.string.error_http_server_error_title)
                                    .withDescription(R.string.error_http_server_error_description);
                        }
                    }

                    registrationError = builder.build();
                    addError(registrationError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    private Completable assertTanRateLimitNotReached() {
        return getNextPossibleTanVerificationTimestamp()
                .map(nextPossibleTimestamp -> nextPossibleTimestamp - TimeUtil.getCurrentMillis())
                .flatMapCompletable(remainingTimeoutDuration -> {
                    if (remainingTimeoutDuration <= 0) {
                        return Completable.complete();
                    } else {
                        String readableDuration = TimeUtil.getReadableDurationWithPlural(remainingTimeoutDuration, getApplication()).blockingGet();
                        return Completable.error(new VerificationException(application.getString(R.string.verification_timeout_error_description, readableDuration)));
                    }
                });
    }

    private Single<Long> getNextPossibleTanVerificationTimestamp() {
        Single<Long> getLastTimestamp = preferencesManager.restoreOrDefault(LAST_TAN_REQUEST_TIMESTAMP_KEY, 0L);
        Single<Long> getTimeout = preferencesManager.restoreOrDefault(NEXT_TAN_REQUEST_TIMEOUT_DURATION_KEY, INITIAL_TAN_REQUESTS_TIMEOUT);
        return Single.zip(getLastTimestamp, getTimeout, (lastTimestamp, timeout) -> lastTimestamp + timeout);
    }

    private Completable incrementTanRequestTimeoutDuration() {
        return preferencesManager.restoreOrDefault(NEXT_TAN_REQUEST_TIMEOUT_DURATION_KEY, INITIAL_TAN_REQUESTS_TIMEOUT)
                .map(timeoutDuration -> timeoutDuration * 2)
                .doOnSuccess(timeoutDuration -> Timber.i("Increasing TAN request timeout duration to %d", timeoutDuration))
                .flatMapCompletable(timeoutDuration -> preferencesManager.persist(NEXT_TAN_REQUEST_TIMEOUT_DURATION_KEY, timeoutDuration))
                .andThen(updateNextPossibleTanRequestTimestamp());
    }

    private Completable resetTanRequestRateLimits() {
        return preferencesManager.restoreIfAvailable(LAST_TAN_REQUEST_TIMESTAMP_KEY, Long.class)
                .map(lastTanRequestTimestamp -> TimeUtil.getCurrentMillis() - lastTanRequestTimestamp)
                .filter(durationSinceLastRequest -> durationSinceLastRequest > TimeUnit.DAYS.toMillis(1))
                .doOnSuccess(durationSinceLastRequest -> Timber.i("Resetting TAN request rate limits"))
                .flatMapCompletable(durationSinceLastRequest -> preferencesManager.persist(NEXT_TAN_REQUEST_TIMEOUT_DURATION_KEY, INITIAL_TAN_REQUESTS_TIMEOUT));
    }

    private Completable updateNextPossibleTanRequestTimestamp() {
        return getNextPossibleTanVerificationTimestamp()
                .flatMapCompletable(timestamp -> update(nextPossibleTanRequestTimestamp, timestamp));
    }

    private Completable updateShouldRequestNewTan() {
        return preferencesManager.containsKey(RECENT_TAN_CHALLENGE_IDS_KEY)
                .map(challengeIdAvailable -> !challengeIdAvailable || shouldRequestNewVerificationTan.getValue())
                .flatMapCompletable(requestNewTan -> update(shouldRequestNewVerificationTan, requestNewTan));
    }

    public void onPhoneNumberVerificationCanceled() {
        updateAsSideEffect(shouldRequestNewVerificationTan, true);
    }

    public Completable verifyTan(String verificationTan) {
        return restoreRecentTanChallengeIds()
                .takeLast(MAXIMUM_TAN_CHALLENGE_IDS)
                .toList()
                .flatMapCompletable(challengeIds -> registrationManager.verifyPhoneNumberWithVerificationTan(verificationTan, challengeIds))
                .andThen(Completable.mergeArray(
                        preferencesManager.persist(LAST_VERIFIED_PHONE_NUMBER_KEY, phoneNumber.getValue()),
                        preferencesManager.persist(PHONE_VERIFICATION_COMPLETED_KEY, true),
                        clearRecentTanChallengeIds()))
                .doOnSubscribe(disposable -> {
                    removeError(registrationError);
                    updateAsSideEffect(isLoading, true);
                })
                .doOnError(throwable -> {
                    if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_BAD_REQUEST)) {
                        // TAN was incorrect
                        // No need to create an error here, this will be handled in the TAN dialog
                        return;
                    }

                    ViewError.Builder builder = createErrorBuilder(throwable)
                            .withTitle(R.string.error_request_failed_title);

                    if (throwable instanceof VerificationException) {
                        builder = builder
                                .withDescription(((VerificationException) throwable).getMessage());
                    } else {
                        builder = builder
                                .withResolveAction(verifyTan(verificationTan))
                                .withResolveLabel(R.string.action_retry);
                    }

                    registrationError = builder.build();
                    addError(registrationError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Completable addToRecentTanChallengeIds(@NonNull String challengeId) {
        return persistRecentTanChallengeIds(restoreRecentTanChallengeIds()
                .mergeWith(Observable.just(challengeId)))
                .doOnComplete(() -> Timber.d("Added challenge ID: %s", challengeId));
    }

    private Observable<String> restoreRecentTanChallengeIds() {
        return preferencesManager.restoreOrDefault(RECENT_TAN_CHALLENGE_IDS_KEY, new ChallengeIdContainer())
                .flatMapObservable(Observable::fromIterable);
    }

    private Completable persistRecentTanChallengeIds(Observable<String> challengeIds) {
        return challengeIds.toList()
                .map(ChallengeIdContainer::new)
                .flatMapCompletable(challengeIdContainer -> preferencesManager.persist(RECENT_TAN_CHALLENGE_IDS_KEY, challengeIdContainer));
    }

    private Completable clearRecentTanChallengeIds() {
        return preferencesManager.delete(RECENT_TAN_CHALLENGE_IDS_KEY);
    }

    private Completable keepConnectEnrollmentStatusUpdated() {
        return connectManager.getEnrollmentStatusAndChanges()
                .flatMapCompletable(status -> updateIfRequired(connectEnrollmentStatus, status));
    }

    private boolean isLucaConnectEnrolled() {
        return connectEnrollmentStatus.getValue();
    }

    public boolean isLucaConnectNoticeRequired() {
        boolean isLucaConnectEnrolled = isLucaConnectEnrolled();
        return isLucaConnectEnrolled && (getShouldUnEnrollLucaConnect() || isAnyContactPropertyChanged());
    }

    public Completable updateLucaConnectUpdatesRequired() {
        return Completable.fromAction(() -> {
            isNameChanged = !registrationData.hasSameName(buildUpdateRegistrationData(new RegistrationData()));
            isPostalCodeChanged = !registrationData.hasSamePostalCode(buildUpdateRegistrationData(new RegistrationData()));
            isAnyContactDataChanged = !registrationData.equals(buildUpdateRegistrationData(new RegistrationData()));
        });
    }

    public boolean getShouldUnEnrollLucaConnect() {
        return isLucaConnectEnrolled() && (isNameChanged || isPostalCodeChanged);
    }

    public boolean getShouldUpdateLucaConnectSharedData() {
        return isLucaConnectEnrolled() && !getShouldUnEnrollLucaConnect() && isAnyContactDataChanged;
    }

    public String getFormattedPhoneNumber() {
        return getFormattedPhoneNumber(getPhoneNumber().getValue(), PhoneNumberUtil.PhoneNumberFormat.E164);
    }

    public String getFormattedPhoneNumber(@NonNull String phoneNumberString, @NonNull PhoneNumberUtil.PhoneNumberFormat format) {
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneNumberString, "DE");
            return phoneNumberUtil.format(phoneNumber, format);
        } catch (NumberParseException e) {
            return getPhoneNumber().getValue();
        }
    }

    public boolean isUsingTestingCredentials() {
        return Objects.equals(firstName.getValue(), "Mickey")
                && Objects.equals(lastName.getValue(), "Mouse")
                && Objects.equals(phoneNumber.getValue(), "+4900000000000")
                && Objects.equals(email.getValue(), "mickey.mouse@gmail.com");
    }

    boolean isSamePhoneNumber(String firstNumber, String secondNumber) {
        if (Objects.equals(firstNumber, secondNumber)) {
            return true;
        } else {
            return phoneNumberUtil.isNumberMatch(firstNumber, secondNumber) == PhoneNumberUtil.MatchType.EXACT_MATCH;
        }
    }

    static boolean isValidName(String name) {
        return name.length() > 0;
    }

    boolean isValidPhoneNumber(@NonNull String phoneNumberString) {
        if (isUsingTestingCredentials()) {
            return true;
        }
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneNumberString, GERMAN_REGION_CODE);
            return phoneNumberUtil.isValidNumber(phoneNumber);
        } catch (NumberParseException e) {
            return false;
        }
    }

    boolean isMobilePhoneNumber(String phoneNumberString) {
        try {
            Phonenumber.PhoneNumber phoneNumber = phoneNumberUtil.parse(phoneNumberString, GERMAN_REGION_CODE);
            return phoneNumberUtil.getNumberType(phoneNumber) == PhoneNumberUtil.PhoneNumberType.MOBILE;
        } catch (NumberParseException e) {
            return false;
        }
    }

    static boolean isValidEMailAddress(@NonNull String emailAddress) {
        return emailAddress.length() > 3 && emailAddress.contains("@");
    }

    static boolean isValidStreet(String address) {
        return address.length() > 0;
    }

    static boolean isValidHouseNumber(String houseNumber) {
        return houseNumber.length() > 0;
    }

    static boolean isValidPostalCode(String postalCode) {
        return postalCode.matches("^[a-zA-Z0-9]*$")
                && postalCode.length() >= 3
                && postalCode.length() <= 15;
    }

    static boolean isValidCity(String city) {
        return city.length() > 0;
    }

    static String getSanitizedString(String value) {
        return StringSanitizeUtil.sanitize(value).trim();
    }

    public LiveData<String> getFirstName() {
        return firstName;
    }

    public LiveData<String> getLastName() {
        return lastName;
    }

    public LiveData<String> getPhoneNumber() {
        return phoneNumber;
    }

    public LiveData<String> getEmail() {
        return email;
    }

    public LiveData<String> getStreet() {
        return street;
    }

    public LiveData<String> getHouseNumber() {
        return houseNumber;
    }

    public LiveData<String> getCity() {
        return city;
    }

    public LiveData<String> getPostalCode() {
        return postalCode;
    }

    public LiveData<Double> getProgress() {
        return progress;
    }

    public MutableLiveData<Boolean> getValidationStatus(LiveData<String> textLiveData) {
        return validationStatuses.get(textLiveData);
    }

    public boolean isInEditMode() {
        return isInEditMode;
    }

    public LiveData<Boolean> getShouldRequestNewVerificationTan() {
        return shouldRequestNewVerificationTan;
    }

    public LiveData<Long> getNextPossibleTanRequestTimestamp() {
        return nextPossibleTanRequestTimestamp;
    }

    public LiveData<Boolean> getCompleted() {
        return completed;
    }

    public boolean getShouldReImportTestData() {
        return shouldReImportTestData;
    }

    public boolean isNameChanged() {
        return isNameChanged;
    }

    public boolean isPostalCodeChanged() {
        return isPostalCodeChanged;
    }

    public boolean isAnyContactPropertyChanged() {
        return isAnyContactDataChanged;
    }
}
