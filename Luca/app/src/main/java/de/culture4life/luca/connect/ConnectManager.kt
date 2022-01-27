package de.culture4life.luca.connect

import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.i18n.phonenumbers.PhoneNumberUtil
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.R
import de.culture4life.luca.crypto.*
import de.culture4life.luca.document.Document
import de.culture4life.luca.document.DocumentManager
import de.culture4life.luca.health.HealthDepartmentManager
import de.culture4life.luca.health.ResponsibleHealthDepartment
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.*
import de.culture4life.luca.notification.LucaNotificationManager
import de.culture4life.luca.pow.PowChallenge
import de.culture4life.luca.pow.PowManager
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.registration.Person
import de.culture4life.luca.registration.RegistrationData
import de.culture4life.luca.registration.RegistrationManager
import de.culture4life.luca.ui.ViewError
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.TimeUtil.getReadableDurationWithPlural
import de.culture4life.luca.util.addTo
import de.culture4life.luca.util.fromUnixTimestamp
import de.culture4life.luca.util.isHttpException
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import okhttp3.internal.and
import timber.log.Timber
import java.net.HttpURLConnection
import java.security.PrivateKey
import java.security.interfaces.ECPublicKey
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.max
import kotlin.text.toByteArray

open class ConnectManager(
    private val preferencesManager: PreferencesManager,
    private val notificationManager: LucaNotificationManager,
    private val networkManager: NetworkManager,
    private val powManager: PowManager,
    private val cryptoManager: CryptoManager,
    private val registrationManager: RegistrationManager,
    private val documentManager: DocumentManager,
    private val healthDepartmentManager: HealthDepartmentManager
) : Manager() {

    private var workManager: WorkManager? = null
    private var notificationId: String? = null
    private var powChallenge: PowChallenge? = null
    private var cachedMessages: Observable<ConnectMessage>? = null
    private val enrollmentStatusSubject = BehaviorSubject.create<Boolean>()
    private val enrollmentSupportedStatusSubject = BehaviorSubject.create<Boolean>()
    private val enrollmentSupportRecognizedStatusSubject = BehaviorSubject.create<Boolean>()
    private val hasUnreadMessagesStatusSubject = BehaviorSubject.create<Boolean>()

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            notificationManager.initialize(context),
            networkManager.initialize(context),
            powManager.initialize(context),
            cryptoManager.initialize(context),
            registrationManager.initialize(context),
            documentManager.initialize(context),
            healthDepartmentManager.initialize(context)
        ).andThen(Completable.fromAction {
            if (!LucaApplication.isRunningUnitTests()) {
                this.workManager = WorkManager.getInstance(context)
            }
        }).andThen(
            Completable.mergeArray(
                invokeRemovalOfOldDataFromContactArchive(),
                invokeStatusInitialization(),
                invokeUpdatesInRegularIntervals(),
                invokeReEnrollmentIfRequired(TimeUnit.SECONDS.toMillis(4)),
                invokeUpdateMessagesIfRequired(TimeUnit.SECONDS.toMillis(2)),
                Completable.fromAction {
                    keepStatusOnHealthDepartmentChangeUpdated()
                        .subscribeOn(Schedulers.io())
                        .subscribe()
                        .addTo(managerDisposable)
                }
            )
        )
    }

    /*
        Status
     */

    private fun invokeStatusInitialization(): Completable {
        return Completable.fromAction {
            statusInitialization()
                .doOnError { Timber.e("Unable to initialize enrollment statuses: $it") }
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(managerDisposable)
        }
    }

    private fun statusInitialization() = Completable.mergeArrayDelayError(
        initializeEnrollmentStatus(),
        initializeEnrollmentSupportedStatus(),
        initializeEnrollmentRecognized(),
        updateHasUnreadMessages()
    )

    private fun initializeEnrollmentStatus(): Completable {
        return restoreContactIdIfAvailable()
            .map { true }
            .defaultIfEmpty(false)
            .doOnSuccess { Timber.v("Initialized enrollment status: $it") }
            .doOnSuccess(enrollmentStatusSubject::onNext)
            .ignoreElement()
    }

    /**
     * Emits `true` if the user is currently enrolled in luca connect, `else` otherwise.
     */
    fun getEnrollmentStatusAndChanges(): Observable<Boolean> {
        return enrollmentStatusSubject.distinctUntilChanged()
    }

    private fun initializeEnrollmentSupportedStatus(): Completable {
        return getHealthDepartmentIfAvailable()
            .map { it.connectEnrollmentSupported }
            .defaultIfEmpty(false)
            .doOnSuccess { Timber.v("Initialized enrollment supported status: $it") }
            .doOnSuccess(enrollmentSupportedStatusSubject::onNext)
            .ignoreElement()
    }

    /**
     * Emits `true` if the currently responsible health department supports luca connect, `else` otherwise.
     * Note: Independent of whether the user is already enrolled or not.
     */
    fun getEnrollmentSupportedStatusAndChanges(): Observable<Boolean> {
        return enrollmentSupportedStatusSubject.distinctUntilChanged()
    }

    /**
     * Emits `true` if user can do the enrollment and has not recognized it yet, otherwise `false`.
     *
     * Following preconditions are considered:
     * - enrollment is supported
     * - is not enrolled
     * - enrollment support not recognized yet
     */
    fun getEnrollmentSupportedButNotRecognizedStatusAndChanges(): Observable<Boolean> {
        return Observable.combineLatest(
            getEnrollmentStatusAndChanges(),
            getEnrollmentSupportedStatusAndChanges(),
            getEnrollmentSupportRecognizedStatusAndChanges(),
            { isEnrolled, isEnrollmentSupported, alreadyRecognized ->
                !isEnrolled && isEnrollmentSupported && !alreadyRecognized
            }
        )
    }

    private fun keepStatusOnHealthDepartmentChangeUpdated(): Completable {
        return healthDepartmentManager.getResponsibleHealthDepartmentUpdates()
            .doOnNext { Timber.d("Responsible health department updated. Available: $it") }
            .flatMapCompletable {
                initializeEnrollmentSupportedStatus()
                    .andThen(setEnrollmentSupportRecognized(false))
                    .andThen(cleanUpAfterUnEnroll())
                    .andThen(preferencesManager.persist(SUPPORT_NOTIFIED_KEY, false))
            }
    }

    private fun initializeEnrollmentRecognized(): Completable {
        return restoreEnrollmentSupportRecognized()
            .doOnSuccess(enrollmentSupportRecognizedStatusSubject::onNext)
            .ignoreElement()
    }

    fun setEnrollmentSupportRecognized(recognized: Boolean): Completable {
        return persistEnrollmentSupportRecognized(recognized)
            .doOnComplete { enrollmentSupportRecognizedStatusSubject.onNext(recognized) }
    }

    fun getEnrollmentSupportRecognizedStatusAndChanges(): Observable<Boolean> {
        return enrollmentSupportRecognizedStatusSubject.distinctUntilChanged()
    }

    private fun persistEnrollmentSupportRecognized(recognized: Boolean): Completable {
        return preferencesManager.persist(SUPPORT_RECOGNIZED_KEY, recognized)
    }

    private fun restoreEnrollmentSupportRecognized(): Single<Boolean> {
        return preferencesManager.restoreOrDefault(SUPPORT_RECOGNIZED_KEY, false)
    }

    fun getHasUnreadMessagesStatusAndChanges(): Observable<Boolean> {
        return hasUnreadMessagesStatusSubject.distinctUntilChanged()
    }

    /*
        Enrollment
     */

    fun enroll(): Completable {
        return generateEnrollmentRequestData()
            .flatMap { enrollmentRequestData ->
                networkManager.lucaEndpointsV4
                    .flatMap { it.enrollToLucaConnect(enrollmentRequestData) }
                    .map { it["contactId"].asString }
            }
            .doOnSuccess { Timber.i("Enrolled with contact ID: %s", it) }
            .flatMapCompletable {
                persistContactId(it)
                    .andThen(addToContactArchive(ConnectContactArchive.Entry(it, System.currentTimeMillis())))
                    .andThen(cryptoManager.deleteKeyPair(AUTHENTICATION_KEY_PAIR_ALIAS))
            }
            .andThen(clearPowChallenge())
            .doOnComplete { enrollmentStatusSubject.onNext(true) }
            .doOnError {
                application.showError(
                    ViewError.Builder(application)
                        .withTitle(application.getString(R.string.luca_connect_activation_error_title))
                        .withCause(it)
                        .withResolveAction(enroll())
                        .withResolveLabel(R.string.action_retry)
                        .canBeShownAsNotification()
                        .removeWhenShown()
                        .build()
                )
            }
    }

    fun generateEnrollmentRequestData(): Single<ConnectEnrollmentRequestData> {
        return cryptoManager.deleteKeyPair(AUTHENTICATION_KEY_PAIR_ALIAS)
            .andThen(Single.fromCallable {
                val departmentPublicKey = getHealthDepartmentPublicKey().blockingGet()
                val registrationData = getRegistrationData().blockingGet()
                val simplifiedNameHash = generateSimplifiedNameHash(registrationData.person).blockingGet()
                val phoneNumberHash = generatePhoneNumberHash(registrationData.phoneNumber!!).blockingGet()
                ConnectEnrollmentRequestData(
                    authPublicKey = getCompressedPublicKey(AUTHENTICATION_KEY_PAIR_ALIAS).blockingGet(),
                    departmentId = getHealthDepartmentId().blockingGet(),
                    namePrefix = generateHashPrefix(simplifiedNameHash).blockingGet().encodeToBase64(),
                    phonePrefix = generateHashPrefix(phoneNumberHash).blockingGet().encodeToBase64(),
                    referenceData = generateReferenceData(simplifiedNameHash, phoneNumberHash, departmentPublicKey).blockingGet(),
                    fullData = generateFullData(registrationData, departmentPublicKey).blockingGet(),
                    pow = getPowChallenge().flatMap(powManager::getSolvedChallenge).map(::PowSolutionRequestData)
                        .onErrorResumeNext { clearPowChallenge().andThen(Single.error(it)) }.blockingGet()
                ).apply {
                    signature = generateEnrollmentRequestSignature(this).map(ByteArray::encodeToBase64).blockingGet()
                }
            }.doOnSuccess { Timber.d("Generated enrollment request data: %s", it) })
    }

    private fun generateReferenceData(nameHash: ByteArray, phoneNumberHash: ByteArray, healthDepartmentPublicKey: ECPublicKey): Single<DliesData> {
        return Single.fromCallable { byteArrayOf(1) + nameHash + phoneNumberHash + getNotificationId().map(String::decodeFromBase64).blockingGet() }
            .doOnSuccess { Timber.d("Encrypting reference data: %s", it.encodeToBase64()) }
            .flatMap { cryptoManager.dlies(it, healthDepartmentPublicKey) }
            .map(::DliesData)
    }

    private fun generateFullData(registrationData: RegistrationData, healthDepartmentPublicKey: ECPublicKey): Single<DliesData> {
        return Single.zip(
            getLatestCovidCertificates().map { it.encodedData }.toList(),
            getNotificationId(),
            getCompressedPublicKey(MESSAGE_ENCRYPTION_KEY_PAIR_ALIAS),
            getCompressedPublicKey(MESSAGE_SIGNING_KEY_PAIR_ALIAS)
        ) { certificates, notificationSeed, encryptionKey, signingKey ->
            ConnectContactData(
                wrappedContactData = ContactData(registrationData).apply {
                    // avoid sending empty string, backend wants null if value is not provided
                    email = if (email.isNullOrEmpty()) null else email
                },
                covidCertificates = certificates,
                notificationSeed = notificationSeed,
                encryptionPublicKey = encryptionKey,
                signingPublicKey = signingKey
            )
        }.doOnSuccess { Timber.d("Encrypting full data: %s", it) }
            .map(Gson()::toJson)
            .map(String::toByteArray)
            .flatMap { cryptoManager.dlies(it, healthDepartmentPublicKey) }
            .map(::DliesData)
    }

    private fun generateEnrollmentRequestSignature(requestData: ConnectEnrollmentRequestData): Single<ByteArray> {
        val properties = Observable.fromIterable(
            with(requestData) {
                listOf(
                    "CREATE_CONNECTCONTACT", departmentId, namePrefix, phonePrefix,
                    referenceData.encryptedData, referenceData.ephemeralPublicKey, referenceData.iv, referenceData.mac,
                    fullData.encryptedData, fullData.ephemeralPublicKey, fullData.iv, fullData.mac
                )
            }
        ).map(String::toByteArray)

        return cryptoManager.concatenateHashes(properties)
            .doOnSuccess { Timber.d("Generating enrollment request signature: %s", it.encodeToHex()) }
            .flatMap { data ->
                cryptoManager.getKeyPairPrivateKey(AUTHENTICATION_KEY_PAIR_ALIAS)
                    .flatMap { key -> cryptoManager.signatureProvider.sign(data, key) }
            }
    }

    /*
        Re-enrollment
     */

    fun invokeReEnrollmentIfRequired(delay: Long = 0): Completable {
        return Completable.fromAction {
            reEnrollIfRequired()
                .doOnError { Timber.e("Unable to re-enroll: $it") }
                .onErrorComplete()
                .delaySubscription(delay, TimeUnit.MILLISECONDS, Schedulers.io())
                .subscribe()
                .addTo(managerDisposable)
        }
    }

    fun reEnrollIfRequired(): Completable {
        return Single.fromCallable { application.isUiCurrentlyVisible }.filter { it }
            .flatMap { enrollmentStatusSubject.firstElement().filter { it } }
            .flatMap { enrollmentSupportedStatusSubject.firstElement().filter { it } }
            .flatMap { hasBeenEnrolledToday().filter { !it } }
            .flatMapCompletable { reEnroll() }
            .doOnSubscribe { Timber.v("Re-enrolling if required") }
    }

    fun reEnroll(): Completable {
        return enroll()
            .doOnSubscribe { Timber.d("Re-enrolling") }
    }

    /*
        Un-enrollment
     */

    fun unEnroll(): Completable {
        val unEnrollmentRequests = getContactArchiveEntries()
            .toFlowable(BackpressureStrategy.BUFFER)
            .doOnNext { Timber.d("Un-enrolling %s", it) }
            .map { it.contactId }
            .flatMapSingle(this::generateUnEnrollmentRequestData)
            .map { requestData ->
                networkManager.lucaEndpointsV4
                    .flatMapCompletable { it.unEnrollFromLucaConnect(requestData) }
                    .onErrorResumeNext { throwable ->
                        if (throwable.isHttpException(HttpURLConnection.HTTP_NOT_FOUND)) {
                            Completable.complete() // already un-enrolled
                        } else {
                            Completable.error(throwable)
                        }
                    }
            }
            .subscribeOn(Schedulers.io())

        return Completable.merge(unEnrollmentRequests)
            .doOnComplete { Timber.d("Un-enrollment requests succeeded") }
            .andThen(cleanUpAfterUnEnroll())
            .doOnComplete { Timber.i("Un-enrolled") }
    }

    private fun cleanUpAfterUnEnroll(): Completable {
        return Completable.fromAction { Timber.d("Cleaning local enrollment data") }
            .andThen(clearContactArchive())
            .andThen(deleteNotificationId())
            .andThen(deleteContactId())
            .doOnComplete { enrollmentStatusSubject.onNext(false) }
    }

    fun generateUnEnrollmentRequestData(dataId: String): Single<ConnectUnEnrollmentRequestData> {
        return Single.fromCallable {
            ConnectUnEnrollmentRequestData(
                contactId = dataId,
                timestamp = TimeUtil.getCurrentUnixTimestamp().blockingGet()
            ).apply {
                signature = generateUnEnrollmentRequestSignature(this).map(ByteArray::encodeToBase64).blockingGet()
            }
        }.doOnSuccess { Timber.d("Generated un-enrollment request data: %s", it) }
    }

    private fun generateUnEnrollmentRequestSignature(requestData: ConnectUnEnrollmentRequestData): Single<ByteArray> {
        val properties = Observable.just("DELETE_CONNECTCONTACT", requestData.contactId)
            .map(String::toByteArray)

        return cryptoManager.concatenateHashes(properties)
            .doOnSuccess { Timber.d("Generating un-enrollment request signature: %s", it.encodeToHex()) }
            .flatMap { data ->
                cryptoManager.getKeyPairPrivateKey(getArchivedKeyPairAlias(requestData.contactId, AUTHENTICATION_KEY_PAIR_ALIAS))
                    .flatMap { key -> cryptoManager.signatureProvider.sign(data, key) }
            }
    }

    /*
        Contact ID
     */

    fun restoreContactIdIfAvailable(): Maybe<String> {
        return preferencesManager.restoreIfAvailable(CONTACT_ID_KEY, String::class.java)
    }

    private fun persistContactId(contactId: String): Completable {
        return preferencesManager.persist(CONTACT_ID_KEY, contactId)
    }

    private fun deleteContactId(): Completable {
        return preferencesManager.delete(CONTACT_ID_KEY)
    }

    /*
        Notification ID
     */

    open fun getNotificationId(): Single<String> {
        return Maybe.fromCallable<String> { this.notificationId }
            .switchIfEmpty(restoreNotificationIdIfAvailable())
            .switchIfEmpty(generateNewNotificationId())
    }

    private fun restoreNotificationIdIfAvailable(): Maybe<String> {
        return preferencesManager.restoreIfAvailable(NOTIFICATION_ID_KEY, String::class.java)
            .doOnSuccess { this.notificationId = it }
    }

    private fun persistNotificationId(notificationId: String): Completable {
        return preferencesManager.persist(NOTIFICATION_ID_KEY, notificationId)
            .doOnComplete { this.notificationId = notificationId }
    }

    private fun deleteNotificationId(): Completable {
        return preferencesManager.delete(NOTIFICATION_ID_KEY)
    }

    private fun generateNewNotificationId(): Single<String> {
        return cryptoManager.generateSecureRandomData(16)
            .map(ByteArray::encodeToBase64)
            .flatMap { persistNotificationId(it).andThen(Single.just(it)) }
    }

    /*
        Health department
     */

    open fun getHealthDepartmentIfAvailable(): Maybe<ResponsibleHealthDepartment> {
        return healthDepartmentManager.getResponsibleHealthDepartmentIfAvailable()
    }

    open fun getHealthDepartmentId(): Single<String> {
        return getHealthDepartmentIfAvailable()
            .switchIfEmpty(Single.error(IllegalStateException("No responsible health department available")))
            .map { it.id }
    }

    open fun getHealthDepartmentPublicKey(): Single<ECPublicKey> {
        return getHealthDepartmentIfAvailable()
            .switchIfEmpty(Single.error(IllegalStateException("No responsible health department available")))
            .map(ResponsibleHealthDepartment::encryptionPublicKey)
    }

    /*
        Proof of work
     */

    fun getPowChallenge(): Single<PowChallenge> {
        return Maybe.fromCallable<PowChallenge> { this.powChallenge }
            .switchIfEmpty(powManager.getChallenge(POW_TYPE_ENROLL)
                .doOnSuccess { this.powChallenge = it })
    }

    fun invokePowChallengeSolving(): Completable {
        return Completable.fromAction {
            getPowChallenge()
                .flatMapCompletable { powManager.solveChallenge(it) }
                .doOnSubscribe { Timber.d("Preparing proof of work") }
                .doOnComplete { Timber.d("Proof of work prepared") }
                .doOnError { Timber.w("Unable to prepare proof of work: %s", it.toString()) }
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(managerDisposable)
        }
    }

    private fun clearPowChallenge(): Completable {
        return Completable.fromAction { this.powChallenge = null }
            .doOnComplete { Timber.d("Cleared proof of work") }
    }

    /*
        Contact Archive
     */

    fun getContactArchiveEntries(): Observable<ConnectContactArchive.Entry> {
        return preferencesManager.restoreIfAvailable(CONTACT_ARCHIVE_KEY, ConnectContactArchive::class.java)
            .flattenAsObservable(ConnectContactArchive::entries)
    }

    private fun addToContactArchive(connectContactArchiveEntry: ConnectContactArchive.Entry): Completable {
        return addCurrentKeysToArchive(connectContactArchiveEntry.contactId)
            .andThen(getContactArchiveEntries().mergeWith(Observable.just(connectContactArchiveEntry)))
            .toList()
            .map(::ConnectContactArchive)
            .flatMapCompletable(this::persistContactArchive)
    }

    private fun persistContactArchive(contactArchive: ConnectContactArchive): Completable {
        return preferencesManager.persist(CONTACT_ARCHIVE_KEY, contactArchive)
    }

    private fun invokeRemovalOfOldDataFromContactArchive(): Completable {
        return Completable.fromAction {
            removeOldDataFromContactArchive()
                .doOnComplete { Timber.d("Removed old data from contact archive") }
                .doOnError { Timber.w("Unable to remove old data from contact archive: %s", it.toString()) }
                .onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
                .addTo(managerDisposable)
        }
    }

    private fun removeOldDataFromContactArchive(): Completable {
        return removeDataFromContactArchive { it.timestamp < System.currentTimeMillis() - CONTACT_ARCHIVE_DURATION }
    }

    private fun removeDataFromContactArchive(filter: Predicate<ConnectContactArchive.Entry>): Completable {
        val entries = getContactArchiveEntries().cache()
        val entriesToRemove = entries.filter(filter)
        val entriesToKeep = entries.filter { !filter.test(it) }

        val deleteEntriesToRemove = entriesToRemove
            .flatMapCompletable { deleteArchivedKeys(it.contactId) }

        val persistEntriesToKeep = entriesToKeep.toList()
            .map(::ConnectContactArchive)
            .flatMapCompletable(this::persistContactArchive)

        return deleteEntriesToRemove.andThen(persistEntriesToKeep)
    }

    fun clearContactArchive(): Completable {
        return removeDataFromContactArchive { true }
    }

    private fun getLastContactArchiveEntryIfAvailable(): Maybe<ConnectContactArchive.Entry> {
        return getContactArchiveEntries()
            .sorted { o1, o2 -> compareValues(o1.timestamp, o2.timestamp) }
            .lastElement()
    }

    private fun getArchivedKeyPairAlias(contactId: String, aliasPrefix: String) = "${aliasPrefix}_${contactId}"

    private fun addCurrentKeysToArchive(contactId: String): Completable {
        return Observable.just(AUTHENTICATION_KEY_PAIR_ALIAS)
            .map { Pair(getArchivedKeyPairAlias(contactId, it), cryptoManager.getKeyPair(it)) }
            .flatMapCompletable { (alias, getKeyPair) ->
                getKeyPair.flatMapCompletable { cryptoManager.persistKeyPair(alias, it) }
            }
    }

    private fun deleteCurrentKeys(): Completable {
        return Observable.just(
            AUTHENTICATION_KEY_PAIR_ALIAS,
            MESSAGE_ENCRYPTION_KEY_PAIR_ALIAS,
            MESSAGE_SIGNING_KEY_PAIR_ALIAS
        ).flatMapCompletable { cryptoManager.deleteKeyPair(it) }
    }

    private fun deleteArchivedKeys(contactId: String): Completable {
        return Observable.just(AUTHENTICATION_KEY_PAIR_ALIAS)
            .map { getArchivedKeyPairAlias(contactId, it) }
            .flatMapCompletable(cryptoManager::deleteKeyPair)
    }

    /*
        Messages
     */

    fun invokeUpdateMessagesIfRequired(delay: Long = 0): Completable {
        return Completable.fromAction {
            updateMessagesIfRequired()
                .doOnError { Timber.e("Unable to update messages: $it") }
                .onErrorComplete()
                .delaySubscription(delay, TimeUnit.MILLISECONDS, Schedulers.io())
                .subscribe()
                .addTo(managerDisposable)
        }
    }

    fun updateMessagesIfRequired(): Completable {
        return Single.fromCallable { !LucaApplication.isRunningUnitTests() }.filter { it }
            .flatMap { getEnrollmentStatusAndChanges().firstElement() }.filter { it }
            .flatMapCompletable { updateMessages() }
            .doOnSubscribe { Timber.v("Updating messages if required") }
    }

    fun updateMessages(): Completable {
        return fetchNewMessages()
            .flatMapCompletable {
                Completable.mergeArray(
                    addToMessageArchive(it),
                    showNewMessageNotification(it)
                )
            }
            .andThen(persistLastUpdateTimestamp(System.currentTimeMillis()))
            .andThen(updateHasUnreadMessages())
            .doOnSubscribe { Timber.d("Updating messages") }
    }

    fun fetchNewMessages(): Observable<ConnectMessage> {
        return Observable.defer {
            val notificationId = getNotificationId().blockingGet()
            val healthDepartmentId = getHealthDepartmentId().blockingGet()

            generateRoundedTimestampsSinceLastUpdate()
                .flatMapSingle { generateMessageId(notificationId, healthDepartmentId, it) }
                .toList() // TODO: LUCA-4861
                .doOnSuccess { Timber.v("Generated ${it.size} message IDs") }
                .map(::ConnectMessageRequestData)
                .flatMapObservable { requestData ->
                    networkManager.lucaEndpointsV4
                        .flatMap { it.getMessages(requestData) }
                        .doOnSuccess { Timber.v("${it.size} message IDs matched") }
                        .flattenAsObservable { it }
                }
                .doOnNext { Timber.v("Fetched encrypted message: $it") }
                .flatMapSingle { responseData ->
                    this.decryptMessageResponseData(responseData)
                        .map { Gson().fromJson(String(it), JsonObject::class.java) }
                        .map { decryptedJson ->
                            ConnectMessage(
                                id = responseData.id,
                                title = decryptedJson["sub"].asString.trim(),
                                content = decryptedJson["msg"].asString.trim(),
                                timestamp = responseData.timestamp.fromUnixTimestamp(),
                                read = false
                            )
                        }
                        .doOnSuccess { Timber.d("Decrypted message: $it") }
                }
        }
    }

    fun decryptMessageResponseData(messageResponseData: ConnectMessageResponseData): Single<ByteArray> {
        return Single.defer {
            val department = getHealthDepartmentIfAvailable().toSingle().blockingGet()
            val messageEncryptionPrivateKey = cryptoManager.getKeyPairPrivateKey(MESSAGE_ENCRYPTION_KEY_PAIR_ALIAS).blockingGet()
            decryptMessageResponseData(messageResponseData, department, messageEncryptionPrivateKey)
        }
    }

    fun decryptMessageResponseData(
        messageResponseData: ConnectMessageResponseData,
        department: ResponsibleHealthDepartment,
        messageEncryptionPrivateKey: PrivateKey
    ): Single<ByteArray> {
        return Single.defer {
            // TODO: LUCA-4872
            val encryptionSecret = cryptoManager.ecdh(messageEncryptionPrivateKey, department.encryptionPublicKey).blockingGet()
            val encryptionKey = cryptoManager.hkdf(
                ikm = encryptionSecret,
                salt = messageResponseData.id.decodeFromBase64(),
                label = MESSAGE_ENCRYPTION_HKDF_LABEL,
                length = 16
            ).map { it.toSecretKey() }.blockingGet()
            val encryptedData = messageResponseData.data.decodeFromBase64()
            val iv = messageResponseData.iv.decodeFromBase64()
            cryptoManager.symmetricCipherProvider.decrypt(encryptedData, iv, encryptionKey)
        }
    }

    fun markMessageAsRead(id: String): Completable {
        return getMessages()
            .filter { it.id == id }
            .flatMapCompletable { markMessageAsRead(it) }
    }

    fun markMessageAsRead(message: ConnectMessage): Completable {
        return Single.fromCallable {
            val unixTimestamp = TimeUtil.getCurrentUnixTimestamp().blockingGet()
            val signatureData = cryptoManager.concatenateHashes(
                Observable.just(
                    "READ_MESSAGE".toByteArray(),
                    message.id.decodeFromBase64(),
                    TimeUtil.encodeUnixTimestamp(unixTimestamp).blockingGet()
                )
            ).blockingGet()
            val signature = cryptoManager.getKeyPairPrivateKey(MESSAGE_SIGNING_KEY_PAIR_ALIAS)
                .flatMap { cryptoManager.signatureProvider.sign(signatureData, it) }
                .blockingGet()
            ConnectMessageReadRequestData(
                id = message.id,
                timestamp = unixTimestamp,
                signature = signature.encodeToBase64()
            )
        }.flatMapCompletable { requestData ->
            networkManager.lucaEndpointsV4
                .flatMapCompletable { it.markMessageAsRead(requestData) }
        }.doOnComplete { message.read = true }
            .andThen(addToMessageArchive(message))
            .andThen(updateHasUnreadMessages())
    }

    fun generateMessageId(notificationId: String, healthDepartmentId: String, roundedTimestamp: Long): Single<String> {
        return Single.defer {
            val encodedTimestamp = TimeUtil.convertToUnixTimestamp(roundedTimestamp)
                .flatMap(TimeUtil::encodeUnixTimestamp)
                .blockingGet()
            val encodedHealthDepartmentId = UUID.fromString(healthDepartmentId).toByteArray()
            cryptoManager.hkdf(
                ikm = notificationId.decodeFromBase64(),
                salt = encodedTimestamp + encodedHealthDepartmentId,
                label = MESSAGE_ID_HKDF_LABEL,
                length = 16
            )
        }.map(ByteArray::encodeToBase64)
    }

    open fun generateRoundedTimestampsSinceLastUpdate(): Observable<Long> {
        return restoreLastUpdateTimestampIfAvailable()
            .defaultIfEmpty(0)
            .map { max(it - ROUNDED_TIMESTAMP_ACCURACY, System.currentTimeMillis() - CONTACT_ARCHIVE_DURATION) }
            .flatMapObservable { generateRoundedTimestamps(it, System.currentTimeMillis() - ROUNDED_TIMESTAMP_ACCURACY) }
    }

    fun generateRoundedTimestamps(startTimestamp: Long, endTimestamp: Long): Observable<Long> {
        return Observable.defer {
            val duration = endTimestamp - startTimestamp
            val count = max(1, (duration / ROUNDED_TIMESTAMP_ACCURACY)).toInt()
            val roundedStartTimestamp = floor(startTimestamp.toDouble() / ROUNDED_TIMESTAMP_ACCURACY).toLong() * ROUNDED_TIMESTAMP_ACCURACY

            Timber.v("Generating $count rounded timestamps in range of $startTimestamp - $endTimestamp")
            Observable.range(0, count)
                .map { roundedStartTimestamp + (it * ROUNDED_TIMESTAMP_ACCURACY) }
        }
    }

    fun restoreLastUpdateTimestampIfAvailable(): Maybe<Long> {
        return preferencesManager.restoreIfAvailable(LAST_MESSAGE_UPDATE_TIMESTAMP_KEY, Long::class.java)
    }

    fun persistLastUpdateTimestamp(timestamp: Long): Completable {
        return preferencesManager.persist(LAST_MESSAGE_UPDATE_TIMESTAMP_KEY, timestamp)
    }

    private fun updateHasUnreadMessages(): Completable {
        return getMessages()
            .filter { !it.read }
            .toList()
            .map { it.isNotEmpty() }
            .doOnSuccess(hasUnreadMessagesStatusSubject::onNext)
            .ignoreElement()
    }

    /*
        Message Archive
     */

    fun getMessages(): Observable<ConnectMessage> {
        return Observable.defer {
            if (cachedMessages == null) {
                cachedMessages = restoreMessageArchiveEntries().cache()
            }
            cachedMessages!!
        }
    }

    fun restoreMessageArchiveEntries(): Observable<ConnectMessage> {
        return preferencesManager.restoreIfAvailable(MESSAGE_ARCHIVE_KEY, ConnectMessageArchive::class.java)
            .flattenAsObservable(ConnectMessageArchive::entries)
    }

    private fun addToMessageArchive(message: ConnectMessage): Completable {
        return restoreMessageArchiveEntries()
            .filter { it.id != message.id } // overwrite existing message
            .mergeWith(Observable.just(message))
            .toList()
            .map(::ConnectMessageArchive)
            .flatMapCompletable(this::persistMessageArchive)
    }

    private fun persistMessageArchive(messageArchive: ConnectMessageArchive): Completable {
        return preferencesManager.persist(MESSAGE_ARCHIVE_KEY, messageArchive)
            .doOnComplete { cachedMessages = Observable.fromIterable(messageArchive.entries).cache() }
    }

    /*
        Updates
     */

    private fun invokeUpdatesInRegularIntervals(): Completable {
        return Completable.fromAction {
            managerDisposable.add(
                startUpdatingInRegularIntervals()
                    .delaySubscription(UPDATE_INITIAL_DELAY, TimeUnit.MILLISECONDS, Schedulers.io())
                    .doOnError { Timber.e("Unable to start updating in regular intervals: $it") }
                    .onErrorComplete()
                    .subscribe()
            )
        }
    }

    private fun startUpdatingInRegularIntervals(): Completable {
        return getNextRecommendedUpdateDelay()
            .flatMapCompletable { initialDelay ->
                Completable.fromAction {
                    if (workManager == null) {
                        Observable.interval(initialDelay, UPDATE_INTERVAL, TimeUnit.MILLISECONDS, Schedulers.io())
                            .flatMapCompletable {
                                ConnectUpdateWorker.createWork(application)
                                    .doOnError { Timber.w("Unable to update: %s", it.toString()) }
                                    .onErrorComplete()
                            }.subscribeOn(Schedulers.io())
                            .subscribe()
                            .addTo(managerDisposable)
                    } else {
                        val constraints = Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                        val updateWorkRequest: WorkRequest = PeriodicWorkRequest.Builder(
                            ConnectUpdateWorker::class.java,
                            UPDATE_INTERVAL, TimeUnit.MILLISECONDS,
                            UPDATE_FLEX_PERIOD, TimeUnit.MILLISECONDS
                        ).setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                            .setConstraints(constraints)
                            .addTag(UPDATE_TAG)
                            .build()
                        workManager!!.cancelAllWorkByTag(UPDATE_TAG)
                        workManager!!.enqueue(updateWorkRequest)
                        Timber.d("Update work request submitted to work manager")
                    }
                }
            }
    }

    open fun getDurationSinceLastUpdate(): Single<Long> {
        return preferencesManager.restoreOrDefault(LAST_MESSAGE_UPDATE_TIMESTAMP_KEY, 0L)
            .map { System.currentTimeMillis() - it }
    }

    open fun getNextRecommendedUpdateDelay(): Single<Long> {
        return getDurationSinceLastUpdate()
            .map { UPDATE_INTERVAL - it }
            .map { max(0, it) }
            .doOnSuccess { recommendedDelay ->
                val readableDelay = getReadableDurationWithPlural(recommendedDelay, context).blockingGet()
                Timber.v("Recommended update delay: %s", readableDelay)
            }
    }

    /*
        Notifications
     */

    /**
     * Will show a notification informing the user about the availability of luca Connect if:
     * - Not yet enrolled
     * - Enrollment supported by responsible health department
     * - Notification has not been shown before
     */
    fun showEnrollmentSupportedNotificationIfRequired(): Completable {
        return enrollmentStatusSubject.firstElement().filter { !it }
            .flatMap { enrollmentSupportedStatusSubject.firstElement().filter { it } }
            .flatMap { preferencesManager.restoreOrDefault(SUPPORT_NOTIFIED_KEY, false).filter { !it } }
            .flatMapCompletable {
                notificationManager.showNotification(
                    LucaNotificationManager.NOTIFICATION_ID_CONNECT_ENROLLMENT_SUPPORTED,
                    notificationManager.createConnectEnrollmentSupportedNotificationBuilder().build()
                ).andThen(preferencesManager.persist(SUPPORT_NOTIFIED_KEY, true))
            }
    }

    fun showNewMessageNotification(connectMessage: ConnectMessage): Completable {
        return Completable.defer {
            val id = LucaNotificationManager.NOTIFICATION_ID_CONNECT_MESSAGE + (connectMessage.id.hashCode() % 1000)
            val notification = notificationManager.createConnectMessageNotificationBuilder(
                connectMessage.title,
                connectMessage.content
            ).build()
            notificationManager.showNotification(id, notification)
        }
    }

    /*
        Utilities
     */

    open fun getRegistrationData(): Single<RegistrationData> {
        return registrationManager.getRegistrationData()
    }

    fun generateSimplifiedNameHash(person: Person): Single<ByteArray> {
        return Single.fromCallable { person.getSimplifiedFullName() }
            .map(String::toByteArray)
            .flatMap(cryptoManager.hashProvider::hash)
            .map { it.trim(32) }
    }

    fun generateHashPrefix(hash: ByteArray): Single<ByteArray> {
        return Single.fromCallable {
            hash.take(2).toByteArray() + (hash[2] and 224).toByte()
        }
    }

    fun generatePhoneNumberHash(phoneNumber: String): Single<ByteArray> {
        return Single.fromCallable {
            val phoneNumberUtil = PhoneNumberUtil.getInstance()
            val parsedPhoneNumber = phoneNumberUtil.parse(phoneNumber, "DE")
            phoneNumberUtil.format(parsedPhoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164)
        }.onErrorReturnItem(phoneNumber)
            .map(String::toByteArray)
            .flatMap(cryptoManager.hashProvider::hash)
            .map { it.trim(32) }
    }

    open fun getLatestCovidCertificates(): Observable<Document> {
        val getDocuments = documentManager.getOrRestoreDocuments()
            .sorted { first, second -> first.resultTimestamp.compareTo(second.resultTimestamp) }
            .cache()

        val getLatestVaccinationCertificate = getDocuments
            .filter { it.isValidVaccination }
            .switchIfEmpty(getDocuments.filter { it.type == Document.TYPE_VACCINATION })
            .lastElement()

        val getLatestRecoveryCertificate = getDocuments
            .filter { it.isValidRecovery }
            .switchIfEmpty(getDocuments.filter { it.type == Document.TYPE_RECOVERY })
            .lastElement()

        return Maybe.concat(
            getLatestVaccinationCertificate,
            getLatestRecoveryCertificate
        ).toObservable()
    }

    private fun getCompressedPublicKey(alias: String): Single<String> =
        cryptoManager.getKeyPairPublicKey(alias)
            .map(ECPublicKey::toCompressedBase64String)

    private fun getLastEnrollmentTimestampIfAvailable(): Maybe<Long> {
        return getLastContactArchiveEntryIfAvailable()
            .map { it.timestamp }
    }

    private fun hasBeenEnrolledToday(): Single<Boolean> {
        return getLastEnrollmentTimestampIfAvailable()
            .map { it >= TimeUtil.getStartOfCurrentDayTimestamp().blockingGet() }
            .defaultIfEmpty(false)
    }
}

private const val NOTIFICATION_ID_KEY = "connect_notification_id"
private const val CONTACT_ID_KEY = "connect_contact_id"
private const val CONTACT_ARCHIVE_KEY = "connect_contact_archive"
private const val MESSAGE_ARCHIVE_KEY = "connect_message_archive"
private const val LAST_MESSAGE_UPDATE_TIMESTAMP_KEY = "connect_message_update_timestamp"
private const val SUPPORT_RECOGNIZED_KEY = "connect_support_recognized"
private const val SUPPORT_NOTIFIED_KEY = "connect_support_notified"
private const val AUTHENTICATION_KEY_PAIR_ALIAS = "connect_authentication_key_pair"
private const val MESSAGE_ENCRYPTION_KEY_PAIR_ALIAS = "message_encryption_key_pair"
private const val MESSAGE_SIGNING_KEY_PAIR_ALIAS = "message_signing_key_pair"
private const val POW_TYPE_ENROLL = "createConnectContact"
private val CONTACT_ARCHIVE_DURATION = TimeUnit.DAYS.toMillis(7)
private val ROUNDED_TIMESTAMP_ACCURACY = TimeUnit.MINUTES.toMillis(5)
private val MESSAGE_ID_HKDF_LABEL = "messageId".toByteArray()
private val MESSAGE_ENCRYPTION_HKDF_LABEL = "encryption".toByteArray()
private const val UPDATE_TAG = "connect_update"
private val UPDATE_INTERVAL = if (BuildConfig.DEBUG) PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS else TimeUnit.HOURS.toMillis(6)
private val UPDATE_FLEX_PERIOD = if (BuildConfig.DEBUG) PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS else TimeUnit.HOURS.toMillis(2)
private val UPDATE_INITIAL_DELAY = TimeUnit.SECONDS.toMillis(10)
