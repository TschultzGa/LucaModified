package de.culture4life.luca.document;

import android.content.Context;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.nexenio.rxkeystore.util.RxBase64;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

import de.culture4life.luca.BuildConfig;
import de.culture4life.luca.Manager;
import de.culture4life.luca.children.Children;
import de.culture4life.luca.children.ChildrenManager;
import de.culture4life.luca.crypto.CryptoManager;
import de.culture4life.luca.crypto.HashProvider;
import de.culture4life.luca.document.DocumentVerificationException.Reason;
import de.culture4life.luca.document.provider.DocumentProvider;
import de.culture4life.luca.document.provider.ProvidedDocument;
import de.culture4life.luca.document.provider.appointment.AppointmentProvider;
import de.culture4life.luca.document.provider.baercode.BaercodeDocumentProvider;
import de.culture4life.luca.document.provider.eudcc.EudccDocumentProvider;
import de.culture4life.luca.document.provider.opentestcheck.OpenTestCheckDocumentProvider;
import de.culture4life.luca.document.provider.ubirch.UbirchDocumentProvider;
import de.culture4life.luca.history.HistoryManager;
import de.culture4life.luca.network.NetworkManager;
import de.culture4life.luca.network.endpoints.LucaEndpointsV3;
import de.culture4life.luca.network.pojo.DocumentProviderData;
import de.culture4life.luca.network.pojo.DocumentProviderDataList;
import de.culture4life.luca.preference.PreferencesManager;
import de.culture4life.luca.registration.Person;
import de.culture4life.luca.registration.RegistrationData;
import de.culture4life.luca.registration.RegistrationManager;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Predicate;
import timber.log.Timber;

public class DocumentManager extends Manager {

    public static final String KEY_DOCUMENTS = "test_results";
    public static final String KEY_DOCUMENT_TAG = "test_result_tag_";
    public static final String KEY_PROVIDER_DATA = "document_provider_data";
    public static final String KEY_LAST_RE_VERIFICATION_TIMESTAMP = "last_document_re_verification";
    private static final byte[] DOCUMENT_REDEEM_HASH_SUFFIX = "testRedeemCheck".getBytes(StandardCharsets.UTF_8);

    private final PreferencesManager preferencesManager;
    private final NetworkManager networkManager;
    private final HistoryManager historyManager;
    private final CryptoManager cryptoManager;
    private final RegistrationManager registrationManager;
    private final ChildrenManager childrenManager;

    private final AppointmentProvider appointmentProvider;
    private final UbirchDocumentProvider ubirchDocumentProvider;
    private final OpenTestCheckDocumentProvider openTestCheckDocumentProvider;
    private EudccDocumentProvider eudccDocumentProvider;
    private BaercodeDocumentProvider baercodeDocumentProvider;

    @Nullable
    private Documents documents;

    public DocumentManager(@NonNull PreferencesManager preferencesManager, @NonNull NetworkManager networkManager, @NonNull HistoryManager historyManager,
                           @NonNull CryptoManager cryptoManager, @NonNull RegistrationManager registrationManager, @NonNull ChildrenManager childrenManager) {
        this.preferencesManager = preferencesManager;
        this.networkManager = networkManager;
        this.historyManager = historyManager;
        this.cryptoManager = cryptoManager;
        this.registrationManager = registrationManager;
        this.childrenManager = childrenManager;
        this.appointmentProvider = new AppointmentProvider();
        this.ubirchDocumentProvider = new UbirchDocumentProvider();
        this.openTestCheckDocumentProvider = new OpenTestCheckDocumentProvider(this);
    }

    @Override
    protected Completable doInitialize(@NonNull Context context) {
        return Completable.mergeArray(
                preferencesManager.initialize(context),
                networkManager.initialize(context),
                historyManager.initialize(context),
                registrationManager.initialize(context),
                childrenManager.initialize(context),
                cryptoManager.initialize(context)
        ).andThen(Completable.fromAction(() -> {
            this.eudccDocumentProvider = new EudccDocumentProvider(context);
            this.baercodeDocumentProvider = new BaercodeDocumentProvider(context);
        })).andThen(deleteExpiredDocuments())
                .andThen(reVerifyDocumentsIfRequired());
    }

    public Single<Boolean> hasMatchingDocument(@NonNull Predicate<Document> filterFunction) {
        return getOrRestoreDocuments()
                .filter(filterFunction)
                .isEmpty()
                .map(isEmpty -> !isEmpty);
    }

    public Single<Boolean> hasVaccinationDocument() {
        return hasMatchingDocument(document -> document.isValidVaccination() && document.isVerified());
    }

    public Single<Boolean> hasRecoveryDocument() {
        return hasMatchingDocument(document -> document.isValidRecovery() && document.isVerified());
    }

    public Single<Boolean> hasPcrTestDocument() {
        return hasMatchingDocument(document -> document.isValidNegativeTestResult() && document.getType() == Document.TYPE_PCR);
    }

    public Single<Boolean> hasQuickTestDocument() {
        return hasMatchingDocument(document -> document.isValidNegativeTestResult() && document.getType() == Document.TYPE_FAST);
    }

    public Single<Document> parseAndValidateEncodedDocument(@NonNull String encodedDocument) {
        Single<Person> getPerson = registrationManager.getOrCreateRegistrationData()
                .map(RegistrationData::getPerson);

        Single<Children> getChildren = childrenManager.getChildren();

        return Single.zip(getPerson, getChildren, (person, children) ->
                Single.mergeDelayError(getDocumentProvidersFor(encodedDocument)
                        .doOnNext(documentProvider -> Timber.v("Attempting to parse using %s", documentProvider.getClass().getSimpleName()))
                        .map(documentProvider -> documentProvider.verifyParseAndValidate(encodedDocument, person, children)
                                .doOnError(throwable -> Timber.w("Parsing failed: %s", throwable.toString()))
                                .map(ProvidedDocument::getDocument))
                        .toFlowable(BackpressureStrategy.BUFFER))
                        .firstOrError())
                .flatMap(documentSingle -> documentSingle)
                .onErrorResumeNext(throwable -> {
                    if (throwable instanceof NoSuchElementException) {
                        return Single.error(new DocumentParsingException("No parser available for encoded data"));
                    } else {
                        return Single.error(throwable);
                    }
                });
    }

    public Single<? extends ProvidedDocument> parseEncodedDocument(@NonNull String encodedDocument) {
        return Single.mergeDelayError(getDocumentProvidersFor(encodedDocument)
                .doOnNext(documentProvider -> Timber.v("Attempting to parse using %s", documentProvider.getClass().getSimpleName()))
                .map(documentProvider -> documentProvider.verify(encodedDocument)
                        .andThen(documentProvider.parse(encodedDocument))
                        .doOnError(throwable -> Timber.w("Parsing failed: %s", throwable.toString())))
                .toFlowable(BackpressureStrategy.BUFFER))
                .firstOrError()
                .onErrorResumeNext(throwable -> {
                    if (throwable instanceof NoSuchElementException) {
                        return Single.error(new DocumentParsingException("No parser available for encoded data"));
                    } else {
                        return Single.error(throwable);
                    }
                });
    }

    public Completable addDocument(@NonNull Document document) {
        return getDocumentResultIfAvailable(document.getId())
                .isEmpty()
                .flatMapCompletable(isNewDocument -> {
                    if (!isNewDocument) {
                        return Completable.error(new DocumentAlreadyImportedException());
                    }
                    if (document.getExpirationTimestamp() < System.currentTimeMillis() && !BuildConfig.DEBUG) {
                        return Completable.error(new DocumentExpiredException());
                    }
                    if (document.getOutcome() == Document.OUTCOME_POSITIVE
                            && document.getType() != Document.TYPE_GREEN_PASS) {
                        if (!document.isValidRecovery()) {
                            return Completable.error(new TestResultPositiveException());
                        }
                    }
                    if (document.getOutcome() == Document.OUTCOME_UNKNOWN
                            && document.getType() != Document.TYPE_GREEN_PASS
                            && document.getType() != Document.TYPE_APPOINTMENT) {
                        return Completable.error(new DocumentVerificationException(Reason.OUTCOME_UNKNOWN));
                    }
                    return getOrRestoreDocuments()
                            .mergeWith(Observable.just(document))
                            .toList()
                            .map(Documents::new)
                            .flatMapCompletable(this::persistDocuments)
                            .andThen(addToHistory(document))
                            .doOnSubscribe(disposable -> Timber.d("Persisting document: %s", document));
                });
    }

    private Observable<? extends DocumentProvider<? extends ProvidedDocument>> getDocumentProvidersFor(@NonNull String encodedDocument) {
        return getDocumentProviders()
                .filter(documentProvider -> documentProvider.canParse(encodedDocument).blockingGet());
    }

    private Observable<? extends DocumentProvider<? extends ProvidedDocument>> getDocumentProviders() {
        return Observable.just(
                appointmentProvider,
                openTestCheckDocumentProvider,
                baercodeDocumentProvider,
                eudccDocumentProvider
        ); // TODO: 07.05.21 add ubirch
    }

    /**
     * Redeem a document so that it can not be imported on another device.
     *
     * @param document document object to redeem
     */
    public Completable redeemDocument(@NonNull Document document) {
        return networkManager.getLucaEndpointsV3()
                .flatMapCompletable(lucaEndpointsV3 -> Single.zip(generateEncodedDocumentHash(document), generateOrRestoreDocumentTag(document), (hash, tag) -> {
                    JsonObject jsonObject = jsonObjectWith(hash, tag);
                    return jsonObject;
                }).flatMapCompletable(lucaEndpointsV3::redeemDocument))
                .onErrorResumeNext(throwable -> {
                    if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_CONFLICT)) {
                        return Completable.error(new DocumentAlreadyImportedException(throwable));
                    } else {
                        return Completable.error(throwable);
                    }
                });
    }

    /**
     * Unredeem the given document so it can be imported again on another device.
     *
     * @param document document object to unredeem
     */
    public Completable unredeemDocument(@NonNull Document document) {
        return Single.zip(generateEncodedDocumentHash(document), generateOrRestoreDocumentTag(document), this::jsonObjectWith)
                .flatMapCompletable(jsonObject -> networkManager.getLucaEndpointsV3()
                        .flatMapCompletable(lucaEndpointsV3 -> lucaEndpointsV3.unredeemDocument(jsonObject)))
                .onErrorResumeNext(throwable -> {
                    if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                        // The route is not yet available on backend or the document was already unredeemed
                        return Completable.complete();
                    }
                    return Completable.error(throwable);
                });
    }

    /**
     * Unredeem and delete all documents stored so they can be imported again on another device.
     */
    public Completable unredeemAndDeleteAllDocuments() {
        return getOrRestoreDocuments()
                .flatMapCompletable(document -> unredeemDocument(document)
                        .andThen(deleteDocument(document.getId())));
    }

    /**
     * By default, vaccination certificates with the required number of doses
     * are considered to provide full immunization after two weeks.
     * <p>
     * Booster shots however should be treated as valid immediately,
     * given that a currently valid vaccination or recovery certificate is available.
     */
    public Completable adjustValidityStartTimestampIfRequired(@NonNull Document document) {
        if (document.getType() != Document.TYPE_VACCINATION
                || document.getOutcome() != Document.OUTCOME_FULLY_IMMUNE
                || document.isValid()) {
            return Completable.complete();
        }
        return getOrRestoreDocuments()
                .filter(it -> (it.isValidVaccination() || it.isValidRecovery()) && it.isSameOwner(document))
                .lastElement()
                .flatMapCompletable(it -> Completable.fromAction(() -> {
                    if (it.getExpirationTimestamp() > document.getValidityStartTimestamp()) {
                        document.setValidityStartTimestamp(document.getTestingTimestamp());
                    }
                }));
    }

    private JsonObject jsonObjectWith(String hash, String tag) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("hash", hash);
        jsonObject.addProperty("tag", tag);
        return jsonObject;
    }

    protected Single<String> generateEncodedDocumentHash(@NonNull Document document) {
        return Single.fromCallable(document::getHashableEncodedData)
                .map(hashableEncodedData -> hashableEncodedData.getBytes(StandardCharsets.UTF_8))
                .flatMap(bytes -> CryptoManager.createKeyFromSecret(DOCUMENT_REDEEM_HASH_SUFFIX)
                        .flatMap(secretKey -> cryptoManager.getMacProvider().sign(bytes, secretKey)))
                .flatMap(bytes -> RxBase64.encode(bytes, Base64.NO_WRAP));
    }

    private Single<String> generateOrRestoreDocumentTag(@NonNull Document document) {
        return Single.just(KEY_DOCUMENT_TAG + document.getId())
                .flatMap(key -> preferencesManager.restoreIfAvailable(key, String.class)
                        .switchIfEmpty(cryptoManager.generateSecureRandomData(HashProvider.TRIMMED_HASH_LENGTH)
                                .flatMap(data -> RxBase64.encode(data, Base64.NO_WRAP))
                                .doOnSuccess(tag -> Timber.d("Generated new tag for test: %s: %s", document, tag))
                                .flatMap(tag -> preferencesManager.persist(key, tag)
                                        .andThen(Single.just(tag)))));
    }

    private Completable addToHistory(@NonNull Document document) {
        return historyManager.addDocumentImportedItem(document);
    }

    public Maybe<Document> getDocumentResultIfAvailable(@NonNull String id) {
        return getOrRestoreDocuments()
                .filter(document -> id.equals(document.getId()))
                .firstElement();
    }

    public Observable<Document> getOrRestoreDocuments() {
        return Maybe.fromCallable(() -> documents)
                .flatMapObservable(Observable::fromIterable)
                .switchIfEmpty(restoreDocuments());
    }

    private Observable<Document> restoreDocuments() {
        return preferencesManager.restoreOrDefault(KEY_DOCUMENTS, new Documents())
                .doOnSuccess(restoredData -> this.documents = restoredData)
                .flatMapObservable(Observable::fromIterable);
    }

    private Completable persistDocuments(@NonNull Documents documents) {
        return preferencesManager.persist(KEY_DOCUMENTS, documents)
                .doOnSubscribe(disposable -> this.documents = documents);
    }

    public Completable reImportDocuments() {
        return getOrRestoreDocuments()
                .flatMapSingle(document -> unredeemDocument(document)
                        .andThen(Single.just(document)))
                .map(Document::getEncodedData).toList()
                .doOnSuccess(encodedDocuments -> Timber.i("Re-importing %d documents", encodedDocuments.size()))
                .flatMapCompletable(encodedDocuments -> clearDocuments()
                        .andThen(Observable.fromIterable(encodedDocuments)
                                .flatMapCompletable(encodedDocument -> parseAndValidateEncodedDocument(encodedDocument)
                                        .doOnSuccess(document -> Timber.d("Re-importing document: %s", document))
                                        .flatMapCompletable(document -> redeemDocument(document)
                                                .andThen(addDocument(document)))
                                        .doOnError(throwable -> Timber.w("Unable to re-import document: %s", throwable.toString()))
                                        .onErrorComplete())));
    }

    private Completable reVerifyDocumentsIfRequired() {
        return preferencesManager.restoreOrDefault(KEY_LAST_RE_VERIFICATION_TIMESTAMP, 0L)
                .filter(timestamp -> timestamp < 1634112471927L) // before 13.10.2021
                .flatMapCompletable(timestamp -> reVerifyDocuments());
    }

    public Completable reVerifyDocuments() {
        Flowable<Completable> updateVerificationStatus = getOrRestoreDocuments()
                .toFlowable(BackpressureStrategy.BUFFER)
                .filter(document -> eudccDocumentProvider.canParse(document.getEncodedData()).blockingGet())
                .map(document -> eudccDocumentProvider.verify(document.getEncodedData())
                        .andThen(Single.just(true))
                        .onErrorReturnItem(false)
                        .doOnSuccess(document::setVerified)
                        .doFinally(() -> Timber.d("Re-verified %s", document))
                        .ignoreElement());

        Completable persistUpdatedDocuments = getOrRestoreDocuments()
                .toList()
                .map(Documents::new)
                .flatMapCompletable(this::persistDocuments);

        return Completable.mergeDelayError(updateVerificationStatus)
                .andThen(persistUpdatedDocuments)
                .onErrorComplete()
                .andThen(preferencesManager.persist(KEY_LAST_RE_VERIFICATION_TIMESTAMP, System.currentTimeMillis()));
    }

    public Completable clearDocuments() {
        return preferencesManager.delete(KEY_DOCUMENTS)
                .doOnComplete(() -> documents = null);
    }

    public Completable deleteExpiredDocuments() {
        return Single.fromCallable(System::currentTimeMillis)
                .flatMapCompletable(this::deleteDocumentsExpiredBefore);
    }

    private Completable deleteDocumentsExpiredBefore(long timestamp) {
        return deleteDocuments(document -> timestamp < document.getExpirationTimestamp())
                .doOnSubscribe(disposable -> Timber.d("Deleting documents expired before %d", timestamp));
    }

    public Completable deleteDocument(@NonNull String id) {
        return deleteDocuments(document -> !id.equals(document.getId()))
                .doOnSubscribe(disposable -> Timber.d("Deleting document: %s", id));
    }

    private Completable deleteDocuments(@NonNull Predicate<Document> filterFunction) {
        return getOrRestoreDocuments()
                .filter(filterFunction)
                .toList()
                .map(Documents::new)
                .flatMapCompletable(this::persistDocuments);
    }

    /**
     * Will emit the {@link DocumentProviderData} with a matching fingerprint or all available data
     * if no fingerprint matches.
     */
    public Observable<DocumentProviderData> getDocumentProviderData(@NonNull String fingerprint) {
        Observable<DocumentProviderData> restoredData = restoreDocumentProviderDataListIfAvailable()
                .flatMapObservable(Observable::fromIterable);

        Observable<DocumentProviderData> fetchedData = fetchDocumentProviderDataList()
                .flatMap(documentProviderData -> persistDocumentProviderDataList(documentProviderData)
                        .andThen(Single.just(documentProviderData)))
                .flatMapObservable(Observable::fromIterable)
                .cache();

        return getDocumentProviderData(restoredData, fingerprint) // find fingerprint in previously persisted data
                .switchIfEmpty(getDocumentProviderData(fetchedData, fingerprint)) // find fingerprint in fetched data
                .switchIfEmpty(fetchedData); // fingerprint not found, emit all fetched data
    }

    public Observable<DocumentProviderData> getDocumentProviderData(Observable<DocumentProviderData> providerData, @NonNull String fingerprint) {
        return providerData.filter(documentProviderData -> fingerprint.equals(documentProviderData.getFingerprint()));
    }

    protected Single<DocumentProviderDataList> fetchDocumentProviderDataList() {
        return networkManager.getLucaEndpointsV3()
                .flatMap(LucaEndpointsV3::getDocumentProviders);
    }

    public Maybe<DocumentProviderDataList> restoreDocumentProviderDataListIfAvailable() {
        return preferencesManager.restoreIfAvailable(KEY_PROVIDER_DATA, DocumentProviderDataList.class);
    }

    protected Completable persistDocumentProviderDataList(@NonNull DocumentProviderDataList documentProviderData) {
        return preferencesManager.persist(KEY_PROVIDER_DATA, documentProviderData);
    }

    /**
     * @return true if the given url is a document in the <a href="https://app.luca-app.de/webapp/testresult/#eyJ0eXAi...">luca
     * style</a>
     */
    public static boolean isTestResult(@NonNull String url) {
        return url.contains("luca-app.de/webapp/testresult/#");
    }

    public static boolean isAppointment(@NonNull String url) {
        return url.contains("luca-app.de/webapp/appointment");
    }

    public void setEudccDocumentProvider(EudccDocumentProvider eudccDocumentProvider) {
        this.eudccDocumentProvider = eudccDocumentProvider;
    }

}
