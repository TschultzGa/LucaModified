package de.culture4life.luca.ui.myluca;

import android.app.Application;
import android.os.Bundle;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import de.culture4life.luca.R;
import de.culture4life.luca.checkin.CheckInManager;
import de.culture4life.luca.children.Children;
import de.culture4life.luca.children.ChildrenManager;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.document.DocumentAlreadyImportedException;
import de.culture4life.luca.document.DocumentExpiredException;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.document.DocumentParsingException;
import de.culture4life.luca.document.DocumentVerificationException;
import de.culture4life.luca.document.Documents;
import de.culture4life.luca.document.TestResultPositiveException;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.meeting.MeetingManager;
import de.culture4life.luca.registration.Person;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseQrCodeViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.ViewEvent;
import de.culture4life.luca.ui.checkin.CheckInViewModel;
import de.culture4life.luca.util.TimeUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class MyLucaViewModel extends BaseQrCodeViewModel {

    private final DocumentManager documentManager;
    private final RegistrationManager registrationManager;
    private final GenuinityManager genuinityManager;
    private final ChildrenManager childrenManager;

    private final MutableLiveData<Bundle> bundle = new MutableLiveData<>();
    private final MutableLiveData<Person> user = new MutableLiveData<>();
    private final MutableLiveData<List<MyLucaListItem>> myLucaItems = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<MyLucaListItem>> itemToDelete = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<MyLucaListItem>> itemToExpand = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Document>> parsedDocument = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Document>> addedDocument = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<String>> possibleCheckInData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGenuineTime = new MutableLiveData<>(true);
    private final MutableLiveData<Children> children = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Document>> showBirthDateHint = new MutableLiveData<>();

    private CheckInViewModel checkInViewModel;

    private ViewError importError;
    private ViewError deleteError;

    public MyLucaViewModel(@NonNull Application application) {
        super(application);
        this.documentManager = this.application.getDocumentManager();
        this.registrationManager = this.application.getRegistrationManager();
        this.genuinityManager = this.application.getGenuinityManager();
        this.childrenManager = this.application.getChildrenManager();
    }

    public void setupViewModelReference(FragmentActivity activity) {
        if (checkInViewModel == null) {
            checkInViewModel = new ViewModelProvider(activity).get(CheckInViewModel.class);
            checkInViewModel.setupViewModelReference(activity);
        }
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.mergeArray(
                        documentManager.initialize(application),
                        registrationManager.initialize(application),
                        genuinityManager.initialize(application),
                        childrenManager.initialize(application)
                ))
                .andThen(updateUserName())
                .andThen(invokeListUpdate())
                .andThen(invokeIsGenuineTimeUpdate())
                .andThen(updateChildCounter())
                .doOnComplete(this::handleApplicationDeepLinkIfAvailable);
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                keepIsGenuineTimeUpdated(),
                keepDocumentsUpdated()
        );
    }

    public Completable updateUserName() {
        return registrationManager.getRegistrationData()
                .flatMapCompletable(registrationData -> update(user, registrationData.getPerson()));
    }

    /*
        List items
     */

    public Completable invokeListUpdate() {
        return Completable.fromAction(() -> modelDisposable.add(updateListItems()
                .doOnError(throwable -> Timber.w("Unable to load list items: %s", throwable.toString()))
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()));
    }

    private Completable updateListItems() {
        return loadListItems()
                .toList()
                .flatMapCompletable(items -> update(myLucaItems, items));
    }

    private Observable<MyLucaListItem> loadListItems() {
        return documentManager.getOrRestoreDocuments()
                .flatMapSingle(document -> documentManager.adjustValidityStartTimestampIfRequired(document)
                        .andThen(Single.just(document)))
                .flatMapMaybe(this::createListItem)
                .sorted((first, second) -> Long.compare(second.getTimestamp(), first.getTimestamp()))
                .doOnSubscribe(disposable -> Timber.d("Loading list items"));
    }

    private Maybe<MyLucaListItem> createListItem(@NonNull Document document) {
        return Maybe.fromCallable(() -> {
            if (document.getType() == Document.TYPE_APPOINTMENT) {
                return new AppointmentItem(application, document);
            } else if (document.getType() == Document.TYPE_VACCINATION) {
                return new VaccinationItem(application, document);
            } else if (document.getType() == Document.TYPE_RECOVERY) {
                return new RecoveryItem(application, document);
            } else {
                return new TestResultItem(application, document);
            }
        });
    }

    public Completable deleteListItem(@NonNull MyLucaListItem myLucaListItem) {
        return Completable.defer(() -> {
            Document document;
            if (myLucaListItem instanceof TestResultItem) {
                document = myLucaListItem.getDocument();
            } else {
                return Completable.error(new IllegalArgumentException("Unable to delete item, unknown type"));
            }
            return documentManager.unredeemDocument(document)
                    .andThen(documentManager.deleteDocument(document.getId()))
                    .andThen(invokeListUpdate())
                    .doOnSubscribe(disposable -> {
                        removeError(deleteError);
                        updateAsSideEffect(isLoading, true);
                    })
                    .doOnError(throwable -> {
                        deleteError = new ViewError.Builder(application)
                                .withCause(throwable)
                                .removeWhenShown()
                                .build();
                        addError(deleteError);
                    })
                    .doFinally(() -> updateAsSideEffect(isLoading, false));
        });
    }

    public void onItemDeletionRequested(@NonNull MyLucaListItem item) {
        updateAsSideEffect(itemToDelete, new ViewEvent<>(item));
    }

    public void onItemExpandToggleRequested(@NonNull MyLucaListItem item) {
        updateAsSideEffect(itemToExpand, new ViewEvent<>(item));
    }

    /*
        QR code scanning
     */

    public boolean canProcessBarcode(@NonNull String barcodeData) {
        return Single.defer(() -> {
            if (DocumentManager.isTestResult(barcodeData)) {
                return getEncodedDocumentFromDeepLink(barcodeData);
            } else {
                return Single.just(barcodeData);
            }
        })
                .flatMap(documentManager::parseAndValidateEncodedDocument)
                .map(document -> true)
                .onErrorReturnItem(false)
                .blockingGet();
    }

    @Override
    @NonNull
    protected Completable processBarcode(@NonNull String barcodeData) {
        return Completable.defer(() -> {
            if (checkInViewModel.canProcessBarcode(barcodeData)) {
                ViewEvent<String> barcodeDataEvent = new ViewEvent<>(barcodeData);
                return update(possibleCheckInData, barcodeDataEvent);
            } else {
                return process(barcodeData);
            }
        }).doOnSubscribe(disposable -> {
            removeError(importError);
            updateAsSideEffect(getShowCameraPreview(), new CameraRequest(false, true));
            updateAsSideEffect(isLoading, true);
        }).doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    public Completable process(@NonNull String barcodeData) {
        // TODO: 21.09.21 better distinguish between processBarcode and process
        return Single.defer(() -> {
            if (DocumentManager.isTestResult(barcodeData)) {
                return getEncodedDocumentFromDeepLink(barcodeData);
            } else {
                return Single.just(barcodeData);
            }
        }).doOnSuccess(value -> {
            Timber.d("Processing barcodeData: %s", value);
            getNotificationManager().vibrate().subscribe();
        }).flatMapCompletable(this::parseAndValidateDocument);
    }

    /*
        Documents
     */

    private Completable keepDocumentsUpdated() {
        return preferencesManager.getChanges(DocumentManager.KEY_DOCUMENTS, Documents.class)
                .delay(1, TimeUnit.SECONDS)
                .flatMapCompletable(documents -> invokeListUpdate());
    }

    private Completable parseAndValidateDocument(@NonNull String encodedDocument) {
        return documentManager.parseAndValidateEncodedDocument(encodedDocument)
                .doOnSubscribe(disposable -> Timber.d("Attempting to parse encoded document: %s", encodedDocument))
                .doOnSuccess(testResult -> Timber.d("Parsed document: %s", testResult))
                .flatMapCompletable(testResult -> update(parsedDocument, new ViewEvent<>(testResult)))
                .doOnSubscribe(disposable -> {
                    removeError(importError);
                    updateAsSideEffect(getShowCameraPreview(), new CameraRequest(false, true));
                    updateAsSideEffect(isLoading, true);
                })
                .doOnError(throwable -> {
                    Timber.w("Unable to parse document: %s", throwable.toString());
                    ViewError.Builder errorBuilder = createErrorBuilder(throwable)
                            .withTitle(R.string.document_import_error_title);

                    if (throwable instanceof DocumentParsingException) {
                        if (MeetingManager.isPrivateMeeting(encodedDocument) || CheckInManager.isSelfCheckInUrl(encodedDocument)) {
                            // the user tried to check-in from the wrong tab
                            errorBuilder.withTitle(R.string.document_import_error_check_in_scanner_title);
                            errorBuilder.withDescription(R.string.document_import_error_check_in_scanner_description);
                        } else if (URLUtil.isValidUrl(encodedDocument) && !DocumentManager.isTestResult(encodedDocument)) {
                            // data is actually an URL that the user may want to open
                            errorBuilder.withDescription(R.string.document_import_error_unsupported_but_url_description);
                            errorBuilder.withResolveLabel(R.string.action_continue);
                            errorBuilder.withResolveAction(Completable.fromAction(() -> application.openUrl(encodedDocument)));
                        } else {
                            errorBuilder.withDescription(R.string.document_import_error_unsupported_description);
                        }
                    } else if (throwable instanceof DocumentExpiredException) {
                        errorBuilder.withDescription(R.string.document_import_error_expired_description);
                    } else if (throwable instanceof DocumentVerificationException) {
                        switch (((DocumentVerificationException) throwable).getReason()) {
                            case NAME_MISMATCH:
                                if (childrenManager.hasChildren().blockingGet()) {
                                    errorBuilder.withDescription(R.string.document_import_error_name_mismatch_including_children_description);
                                } else {
                                    errorBuilder.withDescription(R.string.document_import_error_name_mismatch_description);
                                }
                                break;
                            case INVALID_SIGNATURE:
                                errorBuilder.withDescription(R.string.document_import_error_invalid_signature_description);
                                break;
                            case DATE_OF_BIRTH_TOO_OLD_FOR_CHILD:
                                errorBuilder.withDescription(R.string.document_import_error_child_too_old_description);
                                break;
                            case TIMESTAMP_IN_FUTURE:
                                errorBuilder.withDescription(R.string.document_import_error_time_in_future_description);
                                break;
                        }
                    }

                    importError = errorBuilder.build();
                    addError(importError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribeOn(Schedulers.io());
    }

    public Completable addDocumentIfBirthDatesMatch(@NonNull Document document) {
        return Completable.defer(() -> {
            if (hasNonMatchingBirthDate(document, myLucaItems.getValue())) {
                return update(showBirthDateHint, new ViewEvent<>(document));
            } else {
                return addDocument(document);
            }
        });
    }

    public Completable addDocument(@NonNull Document document) {
        return documentManager.redeemDocument(document)
                .andThen(documentManager.addDocument(document))
                .andThen(update(addedDocument, new ViewEvent<>(document)))
                .andThen(invokeListUpdate())
                .doOnSubscribe(disposable -> {
                    removeError(importError);
                    updateAsSideEffect(isLoading, true);
                })
                .doOnError(throwable -> {
                    ViewError.Builder errorBuilder = createErrorBuilder(throwable)
                            .withTitle(R.string.document_import_error_title);

                    boolean outcomeUnknown = false;
                    if (throwable instanceof DocumentVerificationException) {
                        outcomeUnknown = ((DocumentVerificationException) throwable).getReason() == DocumentVerificationException.Reason.OUTCOME_UNKNOWN;
                    }
                    if (throwable instanceof TestResultPositiveException || outcomeUnknown) {
                        errorBuilder
                                .withTitle(R.string.document_import_error_not_negative_title)
                                .withDescription(R.string.document_import_error_not_negative_description);
                    } else if (throwable instanceof DocumentAlreadyImportedException) {
                        errorBuilder.withDescription(R.string.document_import_error_already_imported_description);
                    } else if (throwable instanceof DocumentExpiredException) {
                        errorBuilder.withDescription(R.string.document_import_error_expired_description);
                    }

                    importError = errorBuilder.build();
                    addError(importError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    protected static boolean hasNonMatchingBirthDate(@NonNull Document document, List<MyLucaListItem> myLucaItems) {
        if (document.getType() != Document.TYPE_VACCINATION) {
            return false;
        }
        for (MyLucaListItem myLucaListItem : myLucaItems) {
            if (myLucaListItem instanceof VaccinationItem) {
                Document oldDocument = myLucaListItem.getDocument();
                if (hasNonMatchingBirthDate(document, oldDocument)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean hasNonMatchingBirthDate(@NonNull Document document1, @NonNull Document document2) {
        long dateOfBirth1 = TimeUtil.getStartOfDayTimestamp(document1.getDateOfBirth()).blockingGet();
        long dateOfBirth2 = TimeUtil.getStartOfDayTimestamp(document2.getDateOfBirth()).blockingGet();
        return document2.getFirstName().equals(document1.getFirstName())
                && document2.getLastName().equals(document1.getLastName())
                && dateOfBirth2 != dateOfBirth1;
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
            if (DocumentManager.isTestResult(url)) {
                return getEncodedDocumentFromDeepLink(url)
                        .flatMapCompletable(this::parseAndValidateDocument)
                        .doFinally(() -> application.onDeepLinkHandled(url));
            } else if (DocumentManager.isAppointment(url)) {
                return parseAndValidateDocument(url)
                        .doFinally(() -> application.onDeepLinkHandled(url));
            }
            return Completable.complete();
        }).doOnComplete(() -> Timber.d("Handled application deep link: %s", url));
    }

    private Single<String> getEncodedDocumentFromDeepLink(@NonNull String url) {
        return Single.fromCallable(() -> {
            if (!DocumentManager.isTestResult(url)) {
                throw new IllegalArgumentException("Unable to get encoded document from URL");
            }
            String[] parts = url.split("#", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Unable to get encoded document from URL");
            }
            return URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name());
        });
    }

    /*
        Children
     */

    void onChildrenManagementRequested() {
        navigationController.navigate(R.id.action_myLucaFragment_to_childrenFragment);
    }

    private Completable updateChildCounter() {
        return childrenManager.getChildren()
                .flatMapCompletable(children -> update(this.children, children));
    }

    /*
        Appointments
     */

    public void onAppointmentRequested() {
        application.openUrl("https://www.luca-app.de/coronatest");
    }

    /*
        Genuity
     */

    private Completable keepIsGenuineTimeUpdated() {
        return genuinityManager.getIsGenuineTimeChanges()
                .flatMapCompletable(genuineTime -> update(isGenuineTime, genuineTime));
    }

    private Completable invokeIsGenuineTimeUpdate() {
        return Completable.fromAction(() -> modelDisposable.add(genuinityManager.isGenuineTime()
                .flatMapCompletable(genuineTime -> update(isGenuineTime, genuineTime))
                .onErrorComplete()
                .subscribe()));
    }

    public Completable invokeServerTimeOffsetUpdate() {
        return Completable.fromAction(() -> modelDisposable.add(genuinityManager.invokeServerTimeOffsetUpdate()
                .delaySubscription(1, TimeUnit.SECONDS)
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()));
    }

    public LiveData<Bundle> getBundle() {
        return bundle;
    }

    public void setBundle(@Nullable Bundle bundle) {
        this.bundle.setValue(bundle);
    }

    public LiveData<List<MyLucaListItem>> getMyLucaItems() {
        return myLucaItems;
    }

    public LiveData<ViewEvent<Document>> getParsedDocument() {
        return parsedDocument;
    }

    public LiveData<ViewEvent<Document>> getAddedDocument() {
        return addedDocument;
    }

    public LiveData<Person> getUser() {
        return user;
    }

    public LiveData<ViewEvent<String>> getPossibleCheckInData() {
        return possibleCheckInData;
    }

    public LiveData<Boolean> getIsGenuineTime() {
        return isGenuineTime;
    }

    public MutableLiveData<Children> getChildren() {
        return children;
    }

    public LiveData<ViewEvent<Document>> getShowBirthDateHint() {
        return showBirthDateHint;
    }

    public MutableLiveData<ViewEvent<MyLucaListItem>> getItemToDelete() {
        return itemToDelete;
    }

    public MutableLiveData<ViewEvent<MyLucaListItem>> getItemToExpand() {
        return itemToExpand;
    }

}
