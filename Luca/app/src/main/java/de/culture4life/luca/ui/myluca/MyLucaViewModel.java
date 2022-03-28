package de.culture4life.luca.ui.myluca;

import android.app.Application;
import android.os.Bundle;

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
import de.culture4life.luca.children.Children;
import de.culture4life.luca.children.ChildrenManager;
import de.culture4life.luca.document.Document;
import de.culture4life.luca.document.DocumentManager;
import de.culture4life.luca.document.Documents;
import de.culture4life.luca.genuinity.GenuinityManager;
import de.culture4life.luca.notification.LucaNotificationManager;
import de.culture4life.luca.registration.Person;
import de.culture4life.luca.registration.RegistrationManager;
import de.culture4life.luca.ui.BaseViewModel;
import de.culture4life.luca.ui.ViewError;
import de.culture4life.luca.ui.ViewEvent;
import de.culture4life.luca.ui.checkin.CheckInViewModel;
import de.culture4life.luca.ui.qrcode.DocumentBarcodeProcessor;
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
    private final DocumentBarcodeProcessor documentBarcodeProcessor;

    private final MutableLiveData<Bundle> bundle = new MutableLiveData<>();
    private final MutableLiveData<Person> user = new MutableLiveData<>();
    private final MutableLiveData<List<MyLucaListItem>> myLucaItems = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<MyLucaListItem>> itemToDelete = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<MyLucaListItem>> itemToExpand = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<Boolean>> addedDocument = new MutableLiveData<>();
    private final MutableLiveData<ViewEvent<String>> possibleCheckInData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isGenuineTime = new MutableLiveData<>(true);
    private final MutableLiveData<Children> children = new MutableLiveData<>();

    private CheckInViewModel checkInViewModel;

    private ViewError deleteError;

    public MyLucaViewModel(@NonNull Application application) {
        super(application);
        this.documentManager = this.application.getDocumentManager();
        this.registrationManager = this.application.getRegistrationManager();
        this.genuinityManager = this.application.getGenuinityManager();
        this.childrenManager = this.application.getChildrenManager();
        this.notificationManager = this.application.getNotificationManager();
        documentBarcodeProcessor = new DocumentBarcodeProcessor(this.application, this);
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
            if (DocumentManager.isTestResult(url)) {
                return getEncodedDocumentFromDeepLink(url)
                        .flatMapCompletable(this::parseAndValidateDocument)
                        .doFinally(() -> application.onDeepLinkHandled(url));
            } else if (DocumentManager.isAppointment(url)) {
                return parseAndValidateDocument(url)
                        .doFinally(() -> application.onDeepLinkHandled(url));
            }
            return Completable.error(IllegalStateException::new);
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

    public MutableLiveData<Children> getChildren() {
        return children;
    }

    public MutableLiveData<ViewEvent<MyLucaListItem>> getItemToDelete() {
        return itemToDelete;
    }

    public MutableLiveData<ViewEvent<MyLucaListItem>> getItemToExpand() {
        return itemToExpand;
    }

}
