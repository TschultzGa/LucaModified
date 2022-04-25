package de.culture4life.luca.document

import android.content.Context
import android.util.Base64
import androidx.annotation.VisibleForTesting
import com.google.gson.JsonObject
import com.nexenio.rxkeystore.util.RxBase64
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.children.Children
import de.culture4life.luca.children.ChildrenManager
import de.culture4life.luca.crypto.CryptoManager
import de.culture4life.luca.crypto.HashProvider
import de.culture4life.luca.document.DocumentUtils.isBoostered
import de.culture4life.luca.document.provider.DocumentProvider
import de.culture4life.luca.document.provider.ProvidedDocument
import de.culture4life.luca.document.provider.appointment.AppointmentProvider
import de.culture4life.luca.document.provider.baercode.BaercodeDocumentProvider
import de.culture4life.luca.document.provider.eudcc.EudccDocumentProvider
import de.culture4life.luca.document.provider.opentestcheck.OpenTestCheckDocumentProvider
import de.culture4life.luca.history.HistoryManager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.NetworkManager.Companion.isHttpException
import de.culture4life.luca.network.pojo.DocumentProviderData
import de.culture4life.luca.network.pojo.DocumentProviderDataList
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.Person
import de.culture4life.luca.registration.RegistrationData
import de.culture4life.luca.registration.RegistrationManager
import de.culture4life.luca.util.LucaUrlUtil.isTestResult
import de.culture4life.luca.util.TimeUtil
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

class DocumentManager(
    private val preferencesManager: PreferencesManager,
    private val networkManager: NetworkManager,
    private val historyManager: HistoryManager,
    // initialization deferred to first use
    private val cryptoManager: CryptoManager,
    private val registrationManager: RegistrationManager,
    private val childrenManager: ChildrenManager
) : Manager() {
    private val appointmentProvider = AppointmentProvider()
    private val openTestCheckDocumentProvider = OpenTestCheckDocumentProvider(this)
    private lateinit var eudccDocumentProvider: EudccDocumentProvider
    private lateinit var baercodeDocumentProvider: BaercodeDocumentProvider
    private var documents: Documents? = null

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            networkManager.initialize(context),
            historyManager.initialize(context),
            registrationManager.initialize(context),
            childrenManager.initialize(context)
        ).andThen(
            Completable.fromAction {
                eudccDocumentProvider = EudccDocumentProvider(context)
                baercodeDocumentProvider = BaercodeDocumentProvider(context)
            }
        ).andThen(
            Completable.mergeArray(
                invokeDeleteExpiredDocuments(),
                invokeMigrateIsEudccPropertyIfRequired(),
                invokeReVerifyDocumentsIfRequired()
            )
        )
    }

    enum class HasDocumentCheckResult {
        VALID_DOCUMENT, INVALID_DOCUMENT, NO_DOCUMENT
    }

    /**
     * @param filterFunction     Filter to check if any documents exist that should be validated. If none exist, NO_DOCUMENT will be returned
     * @param validationFunction Function that validates all filtered documents and returns VALID_DOCUMENT if any document is validated correctly
     */
    private fun hasMatchingDocument(
        filterFunction: (Document) -> Boolean,
        validationFunction: (Document) -> Boolean
    ): Single<HasDocumentCheckResult> {
        return getOrRestoreDocuments()
            .filter(filterFunction)
            .toList()
            .map { documents ->
                when {
                    documents.isEmpty() -> HasDocumentCheckResult.NO_DOCUMENT
                    documents.any(validationFunction) -> HasDocumentCheckResult.VALID_DOCUMENT
                    else -> HasDocumentCheckResult.INVALID_DOCUMENT
                }
            }
    }

    fun hasBoosterDocument(): Single<HasDocumentCheckResult> {
        return getOrRestoreDocuments()
            .toList()
            .map { documents: List<Document> ->
                val vaccinations = documents.filter { it.type == Document.TYPE_VACCINATION }
                val validVaccinations = vaccinations.filter { it.isValidVaccination && it.isVerified }
                val validRecoveries = vaccinations.filter { it.isValidRecovery }

                when {
                    vaccinations.isEmpty() -> HasDocumentCheckResult.NO_DOCUMENT
                    validVaccinations.isEmpty() -> HasDocumentCheckResult.INVALID_DOCUMENT
                    isBoostered(validVaccinations, validRecoveries) -> HasDocumentCheckResult.VALID_DOCUMENT
                    else -> HasDocumentCheckResult.NO_DOCUMENT
                }
            }
    }

    fun hasVaccinationDocument(): Single<HasDocumentCheckResult> {
        return hasMatchingDocument(
            { document -> document.type == Document.TYPE_VACCINATION },
            { document -> document.isValidVaccination && document.isVerified }
        )
    }

    fun hasRecoveryDocument(): Single<HasDocumentCheckResult> {
        return hasMatchingDocument(
            { document -> document.type == Document.TYPE_RECOVERY || document.type == Document.TYPE_PCR && document.outcome == Document.OUTCOME_POSITIVE },
            { document -> document.isValid && document.isVerified }
        )
    }

    fun hasPcrTestDocument(): Single<HasDocumentCheckResult> {
        return hasMatchingDocument(
            { document -> document.type == Document.TYPE_PCR },
            { document -> document.isValidNegativeTestResult }
        )
    }

    fun hasQuickTestDocument(): Single<HasDocumentCheckResult> {
        return hasMatchingDocument(
            { document -> document.type == Document.TYPE_FAST },
            { document -> document.isValidNegativeTestResult }
        )
    }

    fun parseAndValidateEncodedDocument(encodedDocument: String): Single<Document> {
        val getPerson = registrationManager.getRegistrationData()
            .map(RegistrationData::person)

        val getChildren = childrenManager.getChildren()

        return Single.zip(getPerson, getChildren) { person: Person, children: Children ->
            Single.mergeDelayError(
                getDocumentProvidersFor(encodedDocument)
                    .doOnNext { documentProvider -> Timber.v("Attempting to parse using %s", documentProvider.javaClass.simpleName) }
                    .map { documentProvider ->
                        val verifyParseAndValidate = documentProvider.verifyParseAndValidate(encodedDocument, person, children)
                        verifyParseAndValidate
                            .doOnError { throwable -> Timber.w("Parsing failed: $throwable") }
                            .map(ProvidedDocument::getDocument)
                    }
                    .toFlowable(BackpressureStrategy.BUFFER)
            )
                .firstOrError()
        }
            .flatMap { documentSingle -> documentSingle }
            .onErrorResumeNext { throwable ->
                if (throwable is NoSuchElementException) {
                    Single.error(DocumentParsingException("No parser available for encoded data"))
                } else {
                    Single.error(throwable)
                }
            }
    }

    fun parseEncodedDocument(encodedDocument: String): Single<out ProvidedDocument> {
        return Single.mergeDelayError(
            getDocumentProvidersFor(encodedDocument)
                .doOnNext { documentProvider ->
                    Timber.v("Attempting to parse using %s", documentProvider.javaClass.simpleName)
                }
                .map { documentProvider ->
                    documentProvider.verify(encodedDocument)
                        .andThen(documentProvider.parse(encodedDocument))
                        .doOnSuccess { parsedDocument -> parsedDocument.document.isVerified = true }
                        .doOnError { throwable -> Timber.w("Parsing failed: $throwable") }
                }
                .toFlowable(BackpressureStrategy.BUFFER)
        )
            .firstOrError()
            .onErrorResumeNext { throwable ->
                if (throwable is NoSuchElementException) {
                    Single.error(DocumentParsingException("No parser available for encoded data"))
                } else {
                    Single.error(throwable)
                }
            }
    }

    fun addDocument(document: Document): Completable {
        return getDocumentResultIfAvailable(document.id)
            .isEmpty
            .flatMapCompletable { isNewDocument ->
                when {
                    !isNewDocument -> {
                        Completable.error(DocumentAlreadyImportedException())
                    }
                    document.deleteWhenExpired() && TimeUtil.getCurrentMillis() >= document.deletionTimestamp -> {
                        Completable.error(DocumentExpiredException())
                    }
                    document.outcome == Document.OUTCOME_POSITIVE && !document.isRecovery -> {
                        Completable.error(TestResultPositiveException())
                    }
                    document.outcome == Document.OUTCOME_UNKNOWN && document.type != Document.TYPE_APPOINTMENT -> {
                        Completable.error(DocumentVerificationException(DocumentVerificationException.Reason.OUTCOME_UNKNOWN))
                    }
                    else -> {
                        getOrRestoreDocuments()
                            .mergeWith(Observable.just(document))
                            .toList()
                            .map { documents: List<Document> -> Documents(documents) }
                            .flatMapCompletable { documents: Documents -> this.persistDocuments(documents) }
                            .andThen(addToHistory(document))
                            .doOnSubscribe { Timber.d("Persisting document: %s", document) }
                    }
                }
            }
    }

    private fun getDocumentProvidersFor(encodedDocument: String): Observable<out DocumentProvider<out ProvidedDocument>> {
        return getDocumentProviders()
            .filter { documentProvider -> documentProvider.canParse(encodedDocument).blockingGet() }
    }

    private fun getDocumentProviders(): Observable<DocumentProvider<out ProvidedDocument>> {
        return Observable.just(
            appointmentProvider,
            openTestCheckDocumentProvider,
            baercodeDocumentProvider,
            eudccDocumentProvider
        )
    }

    /**
     * Redeem a document so that it can not be imported on another device.
     *
     * @param document document object to redeem
     */
    fun redeemDocument(document: Document): Completable {
        return networkManager.getLucaEndpointsV3()
            .flatMapCompletable { lucaEndpointsV3 ->
                Single
                    .zip(
                        generateEncodedDocumentHash(document),
                        generateOrRestoreDocumentTag(document)
                    ) { hash, tag -> jsonObjectWith(hash, tag) }
                    .flatMapCompletable { message -> lucaEndpointsV3.redeemDocument(message) }
            }
            .onErrorResumeNext { throwable ->
                if (isHttpException(throwable, HttpURLConnection.HTTP_CONFLICT)) {
                    Completable.error(DocumentAlreadyImportedException(throwable))
                } else {
                    Completable.error(throwable)
                }
            }
    }

    /**
     * Unredeem the given document so it can be imported again on another device.
     *
     * @param document document object to unredeem
     */
    fun unredeemDocument(document: Document): Completable {
        return Single.zip(
            generateEncodedDocumentHash(document),
            generateOrRestoreDocumentTag(document)
        ) { hash, tag -> jsonObjectWith(hash, tag) }
            .flatMapCompletable { jsonObject ->
                networkManager.getLucaEndpointsV3()
                    .flatMapCompletable { it.unredeemDocument(jsonObject) }
            }
            .onErrorResumeNext { throwable ->
                if (isHttpException(throwable, HttpURLConnection.HTTP_NOT_FOUND)) {
                    // The route is not yet available on backend or the document was already unredeemed
                    Completable.complete()
                } else {
                    Completable.error(throwable)
                }
            }
    }

    /**
     * Unredeem and delete all documents stored so they can be imported again on another device.
     */
    fun unredeemAndDeleteAllDocuments(): Completable {
        return getOrRestoreDocuments()
            .flatMapCompletable { document ->
                unredeemDocument(document)
                    .andThen(deleteDocument(document.id))
            }
    }

    /**
     * By default, vaccination certificates with the required number of doses
     * are considered to provide full immunization after two weeks.
     *
     *
     * Booster shots however should be treated as valid immediately,
     * given that a currently valid vaccination or recovery certificate is available.
     */
    fun adjustValidityStartTimestampIfRequired(document: Document): Completable {
        return if (document.type != Document.TYPE_VACCINATION || document.outcome != Document.OUTCOME_FULLY_IMMUNE || document.isValid) {
            Completable.complete()
        } else {
            getOrRestoreDocuments()
                .filter { (it.isValidVaccination || it.isValidRecovery) && it.isSameOwner(document) }
                .lastElement()
                .flatMapCompletable {
                    Completable.fromAction {
                        if (it.expirationTimestamp > document.validityStartTimestamp) {
                            document.validityStartTimestamp = document.testingTimestamp
                        }
                    }
                }
        }
    }

    private fun jsonObjectWith(hash: String, tag: String): JsonObject {
        return JsonObject().apply {
            addProperty("hash", hash)
            addProperty("tag", tag)
        }
    }

    fun generateEncodedDocumentHash(document: Document): Single<String> {
        return Single.fromCallable { document.hashableEncodedData }
            .map { hashableEncodedData -> hashableEncodedData.toByteArray(StandardCharsets.UTF_8) }
            .flatMap { bytes ->
                cryptoManager.initialize(context)
                    .andThen(cryptoManager.hmac(bytes, DOCUMENT_REDEEM_HASH_SUFFIX))
            }
            .flatMap { bytes -> RxBase64.encode(bytes, Base64.NO_WRAP) }
    }

    private fun generateOrRestoreDocumentTag(document: Document): Single<String> {
        return Single.just(KEY_DOCUMENT_TAG + document.id)
            .flatMap { key ->
                preferencesManager.restoreIfAvailable(key, String::class.java)
                    .switchIfEmpty(
                        cryptoManager.initialize(context)
                            .andThen(cryptoManager.generateSecureRandomData(HashProvider.TRIMMED_HASH_LENGTH))
                            .flatMap { data -> RxBase64.encode(data, Base64.NO_WRAP) }
                            .doOnSuccess { tag -> Timber.d("Generated new tag for test: %s: %s", document, tag) }
                            .flatMap { tag ->
                                preferencesManager.persist(key, tag)
                                    .andThen(Single.just(tag))
                            }
                    )
            }
    }

    private fun addToHistory(document: Document): Completable {
        return historyManager.addDocumentImportedItem(document)
    }

    fun getDocumentResultIfAvailable(id: String): Maybe<Document> {
        return getOrRestoreDocuments()
            .filter { document -> id == document.id }
            .firstElement()
    }

    fun getOrRestoreDocuments(): Observable<Document> {
        return Maybe.fromCallable { documents }
            .flatMapObservable { source -> Observable.fromIterable(source) }
            .switchIfEmpty(restoreDocuments())
    }

    private fun restoreDocuments(): Observable<Document> {
        return preferencesManager.restoreOrDefault(KEY_DOCUMENTS, Documents())
            .doOnSuccess { restoredData -> documents = restoredData }
            .flatMapObservable { source -> Observable.fromIterable(source) }
    }

    private fun persistDocuments(): Completable {
        return getOrRestoreDocuments()
            .toList()
            .map { documents -> Documents(documents) }
            .flatMapCompletable { documents -> this.persistDocuments(documents) }
    }

    private fun persistDocuments(documents: Documents): Completable {
        return preferencesManager.persist(KEY_DOCUMENTS, documents)
            .doOnSubscribe { this.documents = documents }
    }

    fun reImportDocuments(): Completable {
        return getOrRestoreDocuments()
            .flatMapSingle { document -> unredeemDocument(document).andThen(Single.just(document)) }
            .map { obj -> obj.encodedData }
            .toList()
            .doOnSuccess { encodedDocuments -> Timber.i("Re-importing ${encodedDocuments.size} documents") }
            .flatMapCompletable { encodedDocuments ->
                clearDocuments()
                    .andThen(
                        Observable.fromIterable(encodedDocuments)
                            .flatMapCompletable { encodedDocument ->
                                parseAndValidateEncodedDocument(encodedDocument)
                                    .doOnSuccess { document -> Timber.d("Re-importing document: $document") }
                                    .flatMapCompletable { document -> redeemDocument(document).andThen(addDocument(document)) }
                                    .doOnError { throwable -> Timber.w("Unable to re-import document: $throwable") }
                                    .onErrorComplete()
                            }
                    )
            }
    }

    private fun invokeReVerifyDocumentsIfRequired(): Completable {
        return invokeDelayed(reVerifyDocumentsIfRequired(), TimeUnit.SECONDS.toMillis(3))
    }

    private fun reVerifyDocumentsIfRequired(): Completable {
        return preferencesManager.restoreOrDefault(KEY_LAST_RE_VERIFICATION_TIMESTAMP, 0L)
            .filter { timestamp -> timestamp < MINIMUM_RE_VERIFICATION_TIMESTAMP }
            .flatMapCompletable { reVerifyDocuments().onErrorComplete() }
    }

    fun reVerifyDocuments(): Completable {
        val updateVerificationStatus = getOrRestoreDocuments()
            .toFlowable(BackpressureStrategy.BUFFER)
            .filter { document -> eudccDocumentProvider.canParse(document.encodedData).blockingGet() }
            .map { document ->
                eudccDocumentProvider.verify(document.encodedData)
                    .andThen(Single.just(true))
                    .onErrorReturnItem(false)
                    .doOnSuccess { verified -> document.isVerified = verified }
                    .doFinally { Timber.d("Re-verified $document") }
                    .ignoreElement()
                    .subscribeOn(Schedulers.io())
            }
        return Completable.mergeDelayError(updateVerificationStatus)
            .andThen(persistDocuments())
            .andThen(preferencesManager.persist(KEY_LAST_RE_VERIFICATION_TIMESTAMP, TimeUtil.getCurrentMillis()))
    }

    private fun invokeMigrateIsEudccPropertyIfRequired(): Completable {
        return Completable.defer {
            if (LucaApplication.isRunningUnitTests()) {
                Completable.complete()
            } else {
                invokeDelayed(migrateIsEudccPropertyIfRequired(), TimeUnit.SECONDS.toMillis(2))
            }
        }
    }

    private fun migrateIsEudccPropertyIfRequired(): Completable {
        return preferencesManager.restoreOrDefault(KEY_LAST_EUDCC_MIGRATION_TIMESTAMP, 0L)
            .filter { timestamp -> timestamp < MINIMUM_EUDCC_MIGRATION_TIMESTAMP }
            .flatMapCompletable { migrateIsEudccProperty().onErrorComplete() }
    }

    private fun migrateIsEudccProperty(): Completable {
        val updateIsEudccProperty = getOrRestoreDocuments()
            .toFlowable(BackpressureStrategy.BUFFER)
            .map { document ->
                eudccDocumentProvider.canParse(document.encodedData)
                    .doOnSuccess { isEudcc -> document.isEudcc = isEudcc }
                    .doFinally { Timber.d("Updated isEudcc: $document") }
                    .ignoreElement()
                    .subscribeOn(Schedulers.computation())
            }
        return Completable.mergeDelayError(updateIsEudccProperty)
            .andThen(persistDocuments())
            .andThen(preferencesManager.persist(KEY_LAST_EUDCC_MIGRATION_TIMESTAMP, TimeUtil.getCurrentMillis()))
    }

    fun clearDocuments(): Completable {
        return preferencesManager.delete(KEY_DOCUMENTS)
            .doOnComplete { documents = null }
    }

    private fun invokeDeleteExpiredDocuments(): Completable {
        return Completable.defer {
            if (LucaApplication.isRunningUnitTests()) {
                Completable.complete()
            } else {
                invokeDelayed(deleteExpiredDocuments(), TimeUnit.SECONDS.toMillis(1))
            }
        }
    }

    fun deleteExpiredDocuments(): Completable {
        return Single.fromCallable { TimeUtil.getCurrentMillis() }
            .flatMapCompletable { timestamp -> deleteDocumentsExpiredBefore(timestamp) }
    }

    private fun deleteDocumentsExpiredBefore(timestamp: Long): Completable {
        return deleteDocuments { document -> document.deleteWhenExpired() && timestamp >= document.deletionTimestamp }
            .doOnSubscribe { Timber.d("Deleting documents expired before $timestamp") }
    }

    fun deleteDocument(id: String): Completable {
        return deleteDocuments { document: Document -> id == document.id }
            .doOnSubscribe { Timber.d("Deleting document: $id") }
    }

    /**
     * Delete matching document(s).
     *
     * @param filterFunction predicate which document(s) should be deleted.
     */
    private fun deleteDocuments(filterFunction: Predicate<Document>): Completable {
        return getOrRestoreDocuments()
            .filter { !filterFunction.test(it) }
            .toList()
            .map { documents -> Documents(documents) }
            .flatMapCompletable { documents -> this.persistDocuments(documents) }
    }

    /**
     * Will emit the [DocumentProviderData] with a matching fingerprint or all available data
     * if no fingerprint matches.
     */
    fun getDocumentProviderData(fingerprint: String): Observable<DocumentProviderData> {
        val restoredData = restoreDocumentProviderDataListIfAvailable()
            .flatMapObservable { source -> Observable.fromIterable(source) }
        val fetchedData = fetchDocumentProviderDataList()
            .flatMap { documentProviderData ->
                persistDocumentProviderDataList(documentProviderData)
                    .andThen(Single.just(documentProviderData))
            }
            .flatMapObservable { source -> Observable.fromIterable(source) }
            .cache()
        return getDocumentProviderData(restoredData, fingerprint) // find fingerprint in previously persisted data
            .switchIfEmpty(getDocumentProviderData(fetchedData, fingerprint)) // find fingerprint in fetched data
            .switchIfEmpty(fetchedData) // fingerprint not found, emit all fetched data
    }

    fun getDocumentProviderData(providerData: Observable<DocumentProviderData>, fingerprint: String): Observable<DocumentProviderData> {
        return providerData.filter { documentProviderData -> fingerprint == documentProviderData.fingerprint }
    }

    fun fetchDocumentProviderDataList(): Single<DocumentProviderDataList> {
        return networkManager.getLucaEndpointsV3()
            .flatMap { it.documentProviders }
    }

    fun restoreDocumentProviderDataListIfAvailable(): Maybe<DocumentProviderDataList> {
        return preferencesManager.restoreIfAvailable(KEY_PROVIDER_DATA, DocumentProviderDataList::class.java)
    }

    private fun persistDocumentProviderDataList(documentProviderData: DocumentProviderDataList): Completable {
        return preferencesManager.persist(KEY_PROVIDER_DATA, documentProviderData)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun setEudccDocumentProvider(eudccDocumentProvider: EudccDocumentProvider) {
        this.eudccDocumentProvider = eudccDocumentProvider
    }

    override fun dispose() {
        super.dispose()
        documents = null
    }

    companion object {
        const val KEY_DOCUMENTS = "test_results"
        const val KEY_DOCUMENT_TAG = "test_result_tag_"
        const val KEY_PROVIDER_DATA = "document_provider_data"
        const val KEY_LAST_RE_VERIFICATION_TIMESTAMP = "last_document_re_verification"
        const val KEY_LAST_EUDCC_MIGRATION_TIMESTAMP = "last_eudcc_migration"
        private val DOCUMENT_REDEEM_HASH_SUFFIX = "testRedeemCheck".toByteArray(StandardCharsets.UTF_8)
        private const val MINIMUM_RE_VERIFICATION_TIMESTAMP = 1634112471927L // 13.10.2021
        private const val MINIMUM_EUDCC_MIGRATION_TIMESTAMP = 1642666115743L // 20.01.2022

        @JvmStatic
        fun getEncodedDocumentFromDeepLink(url: String): Single<String> {
            return Single.fromCallable {
                require(isTestResult(url)) { "Unable to get encoded document from URL" }
                val parts = url.split(delimiters = arrayOf("#"), limit = 2)
                require(parts.size == 2) { "Unable to get encoded document from URL" }
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
            }
        }
    }
}
