package de.culture4life.luca.ui.myluca;

import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import de.culture4life.luca.R;
import de.culture4life.luca.children.Children;
import de.culture4life.luca.children.ChildrenManager;
import de.culture4life.luca.consent.ConsentManager;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.document.Documents;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.idnow.IdNowManager;
import de.culture4life.luca.idnow.LucaIdData;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.registration.Person;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.ViewEvent;
import de.culture4life.luca.ui.myluca.listitems.AppointmentItem;
import de.culture4life.luca.ui.myluca.listitems.DocumentItem;
import de.culture4life.luca.ui.myluca.listitems.IdentityEmptyItem;
import de.culture4life.luca.ui.myluca.listitems.IdentityItem;
import de.culture4life.luca.ui.myluca.listitems.IdentityQueuedItem;
import de.culture4life.luca.ui.myluca.listitems.IdentityRequestedItem;
import de.culture4life.luca.ui.myluca.listitems.MyLucaListItem;
import de.culture4life.luca.ui.myluca.listitems.RecoveryItem;
import de.culture4life.luca.ui.myluca.listitems.TestResultItem;
import de.culture4life.luca.ui.myluca.listitems.VaccinationItem;
import de.culture4life.luca.ui.qrcode.DocumentBarcodeProcessor;
import de.culture4life.luca.util.LucaUrlUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import timber.log.Timber;

public class MyLucaViewModel extends BaseViewModel {

    private final DocumentManager documentManager;
    private final RegistrationManager registrationManager;
    private final GenuinityManager genuinityManager;
    private final ChildrenManager childrenManager;
    private final LucaNotificationManager notificationManager;
    private final IdNowManager idNowManager;
    private final ConsentManager consentManager;
    private final DocumentBarcodeProcessor documentBarcodeProcessor;

    private final MutableLiveData<Bundle> bundle = new MutableLiveData<>();
    private final MutableLiveData<Person> user = new MutableLiveData<>();
    private final MutableLiveData<List<MyLucaListItem>> myLucaItems = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<DocumentItem>> itemToDelete = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<MyLucaListItem>> itemToExpand = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Boolean>> addedDocument = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<String>> possibleCheckInData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGenuineTime = new MutableLiveData<>(true);
    private final MutableLiveData<Children> children = new MutableLiveData<>();

    private ViewError deleteError;
    private ViewError deleteIdentityError;

    public MyLucaViewModel(@NonNull Application application) {
        super(application);
        this.documentManager = this.application.getDocumentManager();
        this.registrationManager = this.application.getRegistrationManager();
        this.genuinityManager = this.application.getGenuinityManager();
        this.childrenManager = this.application.getChildrenManager();
        this.notificationManager = this.application.getNotificationManager();
        this.idNowManager = this.application.getIdNowManager();
        this.consentManager = this.application.getConsentManager();
        this.documentBarcodeProcessor = new DocumentBarcodeProcessor(this.application, this);
    }

    @Override
    public Completable initialize() {
        return super.initialize()
                .andThen(Completable.mergeArray(
                        documentManager.initialize(application),
                        registrationManager.initialize(application),
                        genuinityManager.initialize(application),
                        childrenManager.initialize(application),
                        idNowManager.initialize(application),
                        consentManager.initialize(application)
                ))
                .andThen(updateUserName())
                .andThen(invokeListUpdate())
                .andThen(invokeIsGenuineTimeUpdate())
                .andThen(invokeIdEnrollmentStatusUpdateIfRequired())
                .andThen(updateChildCounter())
                .doOnComplete(this::handleApplicationDeepLinkIfAvailable);
    }

    @Override
    public Completable keepDataUpdated() {
        return Completable.mergeArray(
                super.keepDataUpdated(),
                keepIsGenuineTimeUpdated(),
                keepDocumentsUpdated(),
                keepIdUpdated()
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
        return invoke(updateListItems());
    }

    private Completable updateListItems() {
        return loadDocumentItems()
                .mergeWith(loadIdentityItem())
                .toList()
                .flatMapCompletable(items -> update(myLucaItems, items));
    }

    private Observable<MyLucaListItem> loadDocumentItems() {
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

    public void onDeleteIdentityListItem(@Nonnull MyLucaListItem listItem) {
        invoke(
                deleteLucaIdentity()
                        .andThen(resetIdentityItem(listItem))
        ).subscribe();
    }

    @Nonnull
    private Completable deleteLucaIdentity() {
        return idNowManager.unEnroll()
                .doOnSubscribe(disposable -> {
                    removeError(deleteIdentityError);
                    updateAsSideEffect(isLoading, true);
                })
                .doOnError(throwable -> {
                    deleteIdentityError = new ViewError.Builder(application)
                            .withCause(throwable)
                            .removeWhenShown()
                            .build();
                    addError(deleteIdentityError);
                })
                .doFinally(() -> updateAsSideEffect(isLoading, false));
    }

    private Completable resetIdentityItem(MyLucaListItem listItem) {
        return Completable.fromAction(() -> {
            if (listItem instanceof IdentityItem) {
                IdentityItem identityItem = (IdentityItem) listItem;
                identityItem.setIdData(null);
            }
        });
    }

    public Completable deleteDocumentListItem(@NonNull DocumentItem documentItem) {
        return Completable.defer(() -> {
            Document document;
            if (documentItem instanceof TestResultItem) {
                document = documentItem.getDocument();
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

    public void onItemDeletionRequested(@NonNull DocumentItem item) {
        updateAsSideEffect(itemToDelete, new ViewEvent<>(item));
    }

    public void onItemExpandToggleRequested(@NonNull DocumentItem item) {
        updateAsSideEffect(itemToExpand, new ViewEvent<>(item));
    }

    /*
        ID
     */

    private Completable invokeIdEnrollmentStatusUpdateIfRequired() {
        return invoke(idNowManager.updateEnrollmentStatusIfRequired());
    }

    private Completable keepIdUpdated() {
        Observable<Boolean> consentChanges = consentManager.getConsentAndChanges(ConsentManager.ID_TERMS_OF_SERVICE_LUCA_ID)
                .map(consent -> true)
                .skip(1); // we only care about changes

        Observable<Boolean> statusChanges = idNowManager.getVerificationStatusAndChanges()
                .map(verificationStatus -> true)
                .skip(1); // we only care about changes

        return Observable.mergeArray(consentChanges, statusChanges)
                .flatMapCompletable(verificationStatus -> invokeListUpdate());
    }

    public Maybe<LucaIdData.DecryptedIdData> getIdDataIfAvailable() {
        return idNowManager.getDecryptedIdDataIfAvailable();
    }

    /*
        QR code scanning
     */

    public Completable process(@NonNull String barcodeData) {
        // TODO: 21.09.21 better distinguish between processBarcode and process
        return Single.defer(() -> {
            if (LucaUrlUtil.isTestResult(barcodeData)) {
                return DocumentManager.getEncodedDocumentFromDeepLink(barcodeData);
            } else {
                return Single.just(barcodeData);
            }
        }).doOnSuccess(value -> {
            Timber.d("Processing barcodeData: %s", value);
            notificationManager.vibrate().subscribe();
        }).flatMapCompletable(this::parseAndValidateDocument);
    }

    /*
        Documents
     */

    private Completable keepDocumentsUpdated() {
        return preferencesManager.getChanges(DocumentManager.KEY_DOCUMENTS, Documents.class)
                .flatMapCompletable(documents -> invokeListUpdate());
    }

    private Completable parseAndValidateDocument(@NonNull String encodedDocument) {
        return documentBarcodeProcessor.process(encodedDocument)
                .andThen(update(addedDocument, new ViewEvent<>(true)))
                .doOnSubscribe(disposable -> updateAsSideEffect(isLoading, true))
                .doFinally(() -> updateAsSideEffect(isLoading, false))
                .subscribeOn(Schedulers.io());
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
            if (LucaUrlUtil.isTestResult(url)) {
                return DocumentManager.getEncodedDocumentFromDeepLink(url)
                        .flatMapCompletable(this::parseAndValidateDocument)
                        .doFinally(() -> application.onDeepLinkHandled(url));
            } else if (LucaUrlUtil.isAppointment(url)) {
                return parseAndValidateDocument(url)
                        .doFinally(() -> application.onDeepLinkHandled(url));
            }
            return Completable.error(IllegalStateException::new);
        }).doOnComplete(() -> Timber.d("Handled application deep link: %s", url));
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
        return invoke(genuinityManager.isGenuineTime()
                .flatMapCompletable(genuineTime -> update(isGenuineTime, genuineTime)));
    }

    public Completable invokeServerTimeOffsetUpdate() {
        return invoke(genuinityManager.invokeServerTimeOffsetUpdate()
                .delaySubscription(1, TimeUnit.SECONDS));
    }

    /*
        Id Ident
     */
    private Maybe<MyLucaListItem> loadIdentityItem() {
        return idNowManager.isEnrollmentEnabled()
                .flatMapMaybe(enabled -> {
                    if (!enabled) {
                        return Maybe.empty();
                    } else {
                        return idNowManager.getVerificationStatus()
                                .map(status -> {
                                    switch (status) {
                                        case QUEUED: {
                                            return new IdentityQueuedItem();
                                        }
                                        case PENDING: {
                                            String token = idNowManager.getEnrollmentToken().blockingGet();
                                            return new IdentityRequestedItem(token);
                                        }
                                        case SUCCESS: {
                                            return new IdentityItem(null);
                                        }
                                        default: {
                                            return new IdentityEmptyItem();
                                        }
                                    }
                                }).toMaybe();
                    }
                });
    }

    /*
        Getter & Setter
     */
    public LiveData<Bundle> getBundleLiveData() {
        return bundle;
    }

    public void setBundle(@Nullable Bundle bundle) {
        this.bundle.setValue(bundle);
    }

    public LiveData<List<MyLucaListItem>> getMyLucaItems() {
        return myLucaItems;
    }

    public LiveData<ViewEvent<Boolean>> getAddedDocument() {
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

    public LiveData<Children> getChildren() {
        return children;
    }

    public LiveData<ViewEvent<DocumentItem>> getItemToDelete() {
        return itemToDelete;
    }

    @Nonnull
    public LiveData<ViewEvent<MyLucaListItem>> getItemToExpand() {
        return itemToExpand;
    }

}
