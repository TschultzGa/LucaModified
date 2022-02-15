package de.culture4life.luca.registration

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.archive.Archiver
import de.culture4life.luca.checkin.CheckInManager
import de.culture4life.luca.crypto.*
import de.culture4life.luca.crypto.CryptoManager.Companion.concatenate
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.endpoints.LucaEndpointsV3
import de.culture4life.luca.network.pojo.*
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.util.SerializationUtil
import de.culture4life.luca.util.TimeUtil
import de.culture4life.luca.util.encodeToBase64
import de.culture4life.luca.util.toUnixTimestamp
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import timber.log.Timber
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Handles initial registration of a guest, after phone number verification appropriate secrets are
 * created, encrypting [ContactData] before it is uploaded to the Luca Server.
 *
 * @see [Security
 * Overview: Guest Registration](https://www.luca-app.de/securityoverview/processes/guest_registration.html)
 */
class RegistrationManager(
    private val preferencesManager: PreferencesManager,
    private val networkManager: NetworkManager,
    private val cryptoManager: CryptoManager // initialization deferred to first use
) : Manager() {

    // we can't pass the check-in manager via the constructor
    // because we have a cyclic dependency
    // initialization deferred to first use
    private lateinit var checkInManager: CheckInManager

    private val archiver = Archiver(preferencesManager, KEY_ARCHIVED_REGISTRATION_DATA, RegistrationArchive::class.java) { it.registrationTimestamp }

    private var cachedRegistrationData: RegistrationData? = null

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            networkManager.initialize(context),
        ).andThen(
            Completable.fromAction {
                checkInManager = application.checkInManager
            }
        ).andThen(
            Completable.mergeArray(
                invokeReportActiveUserIfRequired(),
                invokeRemovalOfOldDataFromArchive(),
            )
        )
    }

    override fun dispose() {
        archiver.clearCachedData()
        super.dispose()
    }

    /**
     * Delete account on the backend.
     */
    fun deleteRegistrationOnBackend(): Completable {
        return getUserIdIfAvailable()
            .toSingle()
            .flatMapCompletable { userId ->
                createDeletionData(userId)
                    .flatMapCompletable { deletionRequestData ->
                        networkManager.lucaEndpointsV3
                            .flatMapCompletable { endpoint: LucaEndpointsV3 -> endpoint.deleteUser(userId.toString(), deletionRequestData) }
                            .onErrorResumeNext {
                                if (NetworkManager.isHttpException(it, HttpURLConnection.HTTP_FORBIDDEN) &&
                                    !LucaApplication.IS_USING_STAGING_ENVIRONMENT
                                ) {
                                    // The deletion failed because the signature verification failed.
                                    // However, this is an unrecoverable error that prevents the user
                                    // from deleting the account manually, thus we treat it as success.
                                    Completable.complete()
                                } else {
                                    Completable.error(it)
                                }
                            }
                    }
            }
    }

    private fun createDeletionData(userIdParam: UUID): Single<UserDeletionRequestData> {
        return cryptoManager.initialize(context)
            .andThen(Single.just(userIdParam))
            .map(UUID::toByteArray)
            .flatMap { encodedUserId -> concatenate(UserDeletionRequestData.DELETE_USER, encodedUserId) }
            .flatMap { data ->
                cryptoManager.ecdsa(data, ALIAS_GUEST_KEY_PAIR)
                    .flatMap(SerializationUtil::toBase64)
                    .onErrorReturnItem("")
                    .map(::UserDeletionRequestData)
            }
    }

    fun deleteRegistrationData(): Completable {
        return Completable.mergeArray(
            preferencesManager.persist(REGISTRATION_COMPLETED_KEY, false),
            preferencesManager.delete(REGISTRATION_DATA_KEY).doOnComplete { cachedRegistrationData = null }
        )
    }

    fun hasCompletedRegistration(): Single<Boolean> {
        return preferencesManager.restoreOrDefault(REGISTRATION_COMPLETED_KEY, false)
    }

    fun getRegistrationData(): Single<RegistrationData> {
        return Maybe.fromCallable<RegistrationData> { cachedRegistrationData }
            .switchIfEmpty(preferencesManager.restoreIfAvailable(REGISTRATION_DATA_KEY, RegistrationData::class.java))
            .switchIfEmpty(createRegistrationData())
    }

    private fun createRegistrationData(): Single<RegistrationData> {
        return Single.just(RegistrationData())
            .flatMap { registrationData ->
                persistRegistrationData(registrationData)
                    .andThen(Single.just(registrationData))
            }
    }

    fun persistRegistrationData(registrationData: RegistrationData): Completable {
        return preferencesManager.persist(REGISTRATION_DATA_KEY, registrationData)
            .doOnComplete { this.cachedRegistrationData = registrationData }
    }

    fun getUserIdIfAvailable(): Maybe<UUID> {
        return preferencesManager.restoreIfAvailable(USER_ID_KEY, UUID::class.java)
    }

    fun hasProvidedRequiredContactData(): Single<Boolean> {
        return getRegistrationData()
            .doOnSuccess { Timber.v("Checking if required contact data has been provided: %s", it) }
            .flatMapObservable { registrationData ->
                with(registrationData) {
                    Observable.fromIterable(
                        listOf(
                            firstName.isNullOrBlank(),
                            lastName.isNullOrBlank(),
                            phoneNumber.isNullOrBlank(),
                            street.isNullOrBlank(),
                            houseNumber.isNullOrBlank(),
                            postalCode.isNullOrBlank(),
                            city.isNullOrBlank()
                        )
                    )
                }
            }
            .any { it }
            .map { !it }
    }

    /*
        Phone number verification requests
     */

    /**
     * Request a TAN for the given phone number.
     *
     * @param formattedPhoneNumber Phone number in E.164 (FQTN) format
     */
    fun requestPhoneNumberVerificationTan(formattedPhoneNumber: String): Single<String> {
        return Single.defer {
            val requestData = JsonObject().apply { addProperty("phone", formattedPhoneNumber) }
            networkManager.lucaEndpointsV3
                .flatMap { lucaEndpointsV3 -> lucaEndpointsV3.requestPhoneNumberVerificationTan(requestData) }
                .doOnSubscribe { Timber.i("Requesting TAN for %s", formattedPhoneNumber) }
                .map { jsonObject -> jsonObject["challengeId"].asString }
        }
    }

    fun verifyPhoneNumberWithVerificationTan(verificationTan: String, challengeIds: List<String>): Completable {
        return Completable.defer {
            val challengeIdArray = JsonArray(challengeIds.size)
            for (challengeId in challengeIds) {
                challengeIdArray.add(challengeId)
            }
            val requestData = JsonObject().apply {
                add("challengeIds", challengeIdArray)
                addProperty("tan", verificationTan)
            }
            networkManager.lucaEndpointsV3
                .flatMapCompletable { lucaEndpointsV3 -> lucaEndpointsV3.verifyPhoneNumberBulk(requestData) }
        }
    }

    /*
        Registration and update requests
     */

    fun registerUser(): Completable {
        return createUserRegistrationRequestData()
            .doOnSuccess { data -> Timber.d("User registration request data: %s", data) }
            .flatMap { registrationRequestData ->
                networkManager.lucaEndpointsV3
                    .flatMap { lucaEndpointsV3 -> lucaEndpointsV3.registerUser(registrationRequestData) }
                    .map { it["userId"].asString }
                    .map(UUID::fromString)
            }
            .doOnSuccess { userId -> Timber.i("Registered user for ID: %s", userId) }
            .flatMapCompletable { userId ->
                Completable.mergeArray(
                    preferencesManager.persist(REGISTRATION_COMPLETED_KEY, true),
                    preferencesManager.persist(USER_ID_KEY, userId),
                    getRegistrationData()
                        .doOnSuccess {
                            it.id = userId
                            it.registrationTimestamp = TimeUtil.getCurrentMillis()
                        }
                        .flatMapCompletable {
                            Completable.mergeArray(
                                persistRegistrationData(it),
                                archiver.addData(it)
                            )
                        }
                        .andThen(preferencesManager.persist(LAST_USER_ACTIVITY_REPORT_TIMESTAMP_KEY, TimeUtil.getCurrentMillis()))
                )
            }
    }

    /**
     * Update contact data on the server side by encrypting the new data using the already present
     * guest keypair and uploading it to the luca server.
     *
     * @see [Security
     * Overview: Updating the Contact Data](https://luca-app.de/securityoverview/processes/guest_registration.html.updating-the-contact-data)
     *
     * @see [Security
     * Overview: Encrypting the Contact Data](https://luca-app.de/securityoverview/processes/guest_registration.html.process-guest-registration-encryption)
     *
     * @see [Security
     * Overview: Secrets](https://luca-app.de/securityoverview/properties/secrets.html.term-guest-keypair)
     */
    fun updateUser(): Completable {
        return createUserRegistrationRequestData()
            .doOnSuccess { it.guestKeyPairPublicKey = null } // not part of update request
            .doOnSuccess { Timber.d("User update request data: %s", it) }
            .flatMapCompletable { registrationRequestData ->
                getUserIdIfAvailable()
                    .flatMapCompletable { userId ->
                        networkManager.lucaEndpointsV3
                            .flatMapCompletable { lucaEndpointsV3 ->
                                lucaEndpointsV3.updateUser(userId.toString(), registrationRequestData)
                            }
                            .andThen(preferencesManager.persist(LAST_USER_ACTIVITY_REPORT_TIMESTAMP_KEY, TimeUtil.getCurrentMillis()))
                    }
            }
            .doOnComplete { Timber.i("Updated user") }
            .onErrorResumeNext { throwable ->
                if (NetworkManager.isHttpException(throwable, HttpURLConnection.HTTP_FORBIDDEN, HttpURLConnection.HTTP_NOT_FOUND)) {
                    // User keystore changed (403) or data already removed from database (404).
                    registerUser()
                } else {
                    Completable.error(throwable)
                }
            }
    }

    private fun invokeReportActiveUserIfRequired(): Completable {
        return invokeDelayed(
            hasCompletedRegistration()
                .filter { it }
                .flatMapCompletable { reportActiveUser() }
                .doOnError { throwable -> Timber.w("Unable to report active user: %s", throwable.toString()) }
                .retryWhen { error -> error.delay(10, TimeUnit.SECONDS) },
            TimeUnit.SECONDS.toMillis(2)
        )
    }

    /**
     * Report active app user. Backend will regularly delete inactive user. Here we report in
     * meaningful time periods that the user has still installed the app. Usually we try to
     * have as less backend communication as possible, here we ensure that minimum necessary
     * communication will happen.
     */
    fun reportActiveUser(): Completable {
        return preferencesManager.restoreOrDefault(LAST_USER_ACTIVITY_REPORT_TIMESTAMP_KEY, 0L)
            .map { TimeUtil.getCurrentMillis() - it }
            .flatMapCompletable {
                if (it >= USER_ACTIVITY_REPORT_INTERVAL) {
                    registerUser()
                } else {
                    Completable.complete()
                }
            }
    }

    /**
     * Generate data required to register a user. contact data is encrypted and authenticated using
     * the guest keypair.
     *
     * @return encrypted guest data including IV, MAC, signature and the guest keypair's public key
     * @see [Security
     * Overview: Registering to the Luca Server](https://luca-app.de/securityoverview/processes/guest_registration.html.registering-to-the-luca-server)
     *
     * @see [Security
     * Overview: Secrets](https://luca-app.de/securityoverview/properties/secrets.html.term-guest-keypair)
     */
    fun createUserRegistrationRequestData(): Single<UserRegistrationRequestData> {
        return cryptoManager.initialize(context)
            .andThen(getRegistrationData())
            .map(::ContactData)
            .flatMap(::encryptContactData)
            .flatMap { (encryptedData, iv) ->
                Single.fromCallable {
                    val mac = createContactDataMac(encryptedData).blockingGet()
                    val signature = createContactDataSignature(encryptedData, mac, iv).blockingGet()
                    val publicKey = cryptoManager.getKeyPairPublicKey(ALIAS_GUEST_KEY_PAIR).blockingGet()
                    UserRegistrationRequestData().apply {
                        this.encryptedContactData = encryptedData.encodeToBase64()
                        this.iv = iv.encodeToBase64()
                        this.mac = mac.encodeToBase64()
                        this.signature = signature.encodeToBase64()
                        this.guestKeyPairPublicKey = AsymmetricCipherProvider.encode(publicKey)
                            .map(ByteArray::encodeToBase64).blockingGet()
                    }
                }
            }
    }

    /**
     * Encrypts given contact data with a symmetric key derived from the primary data secret.
     *
     * @param contactData to be encrypted
     * @return IV and ciphertext of contact data
     * @see [Security
     * Overview: Secrets](https://www.luca-app.de/securityoverview/properties/secrets.html.term-data-secret)
     *
     * @see [Security
     * Overview: Encrypting the Contact Data](https://luca-app.de/securityoverview/processes/guest_registration.html.encrypting-the-contact-data)
     */
    private fun encryptContactData(contactData: ContactData): Single<Pair<ByteArray, ByteArray>> {
        return SerializationUtil.toJson(contactData)
            .map { contactDataJson -> contactDataJson.toByteArray(StandardCharsets.UTF_8) }
            .flatMap { encodedContactData ->
                Single.zip(
                    cryptoManager.getDataSecret()
                        .flatMap(cryptoManager::generateDataEncryptionSecret)
                        .map(ByteArray::toSecretKey),
                    cryptoManager.generateSecureRandomData(HashProvider.TRIMMED_HASH_LENGTH),
                    ::Pair
                ).flatMap { (key, iv) ->
                    cryptoManager.symmetricCipherProvider
                        .encrypt(encodedContactData, iv, key)
                        .map { encryptedData -> Pair(encryptedData, iv) }
                }
            }
    }

    /**
     * Creates HMAC of encrypted contact data using the data authentication key, which is stored on
     * the Luca server as part of the encrypted guest data.
     *
     * @see [Security
     * Overview: Secrets](https://www.luca-app.de/securityoverview/properties/secrets.html.term-data-authentication-key)
     */
    private fun createContactDataMac(encryptedContactData: ByteArray): Single<ByteArray> {
        return cryptoManager.getDataSecret()
            .flatMap(cryptoManager::generateDataAuthenticationSecret)
            .flatMap { cryptoManager.hmac(encryptedContactData, it) }
    }

    /**
     * Sign given encrypted contact data using the guest's private key.
     *
     * @see [Security
     * Overview: Secrets](https://luca-app.de/securityoverview/properties/secrets.html.term-guest-keypair)
     */
    private fun createContactDataSignature(encryptedContactData: ByteArray, mac: ByteArray, iv: ByteArray): Single<ByteArray> {
        return concatenate(encryptedContactData, mac, iv)
            .flatMap { cryptoManager.ecdsa(it, ALIAS_GUEST_KEY_PAIR) }
    }
    /*
        Data transfer request
     */

    /**
     * Upload encrypted [TransferData] to the luca server, yielding a TAN, allowing a health
     * department to initiate tracing.
     *
     * @return tracing TAN to be shown to the user / communicated to a health department by
     * telephone
     * @see [Security
     * Overview: Tracing the Check-In History of an Infected Guest](https://www.luca-app.de/securityoverview/processes/tracing_access_to_history.html.process)
     */
    fun transferUserData(days: Int): Single<String> {
        return createDataTransferRequestData(days)
            .doOnSuccess { Timber.d("User data transfer request data: %s", it) }
            .flatMap { transferRequestData ->
                networkManager.lucaEndpointsV3
                    .flatMap { lucaEndpointsV3 -> lucaEndpointsV3.getTracingTan(transferRequestData) }
            }
            .map { it["tan"].asString }
    }

    /**
     * Create, encrypt and authenticate [TransferData] to be uploaded to the luca server.
     *
     * @return encrypted guest data transfer object
     * @see [Security
     * Overview: Accessing the Infected Guest’s Tracing Secrets](https://www.luca-app.de/securityoverview/processes/tracing_access_to_history.html.accessing-the-infected-guest-s-tracing-secrets)
     *
     * @see [Security
     * Overview: Secrets](https://www.luca-app.de/securityoverview/properties/secrets.html.term-guest-data-transfer-object)
     */
    private fun createDataTransferRequestData(days: Int): Single<DataTransferRequestData> {
        return cryptoManager.initialize(context)
            .andThen(createTransferData(days))
            .doOnSuccess { Timber.i("Encrypting transfer data: %s", it) }
            .flatMap(SerializationUtil::toJson)
            .map(String::toByteArray)
            .flatMap(cryptoManager::eciesEncrypt)
            .map { eciesData ->
                DataTransferRequestData().apply {
                    this.encryptedContactData = eciesData.encryptedData.encodeToBase64()
                    this.iv = eciesData.iv.encodeToBase64()
                    this.mac = eciesData.mac.encodeToBase64()
                    this.guestKeyPairPublicKey = AsymmetricCipherProvider.encode(eciesData.ephemeralPublicKey)
                        .map(ByteArray::encodeToBase64)
                        .blockingGet()
                    this.dailyKeyPairPublicKeyId = cryptoManager.getDailyPublicKey()
                        .map(DailyPublicKeyData::id)
                        .blockingGet()
                }
            }
    }

    /**
     * Create guest data transfer object containing tracing secrets, data secrets and user IDs.
     *
     * @see [Tracing the Check-In History of an Infected Guest](https://luca-app.de/securityoverview/processes/tracing_access_to_history.html?highlight=transfer#accessing-the-infected-guest-s-tracing-secrets)
     */
    private fun createTransferData(days: Int): Single<TransferData> {
        return cryptoManager.initialize(context)
            .andThen(
                Single.fromCallable {
                    val dataSecret = cryptoManager.getDataSecret()
                        .map(ByteArray::encodeToBase64)
                        .blockingGet()

                    val userDataWrappers = archiver.getData()
                        .map {
                            UserDataWrapper(
                                id = it.id.toString(),
                                registrationTimestamp = it.registrationTimestamp.toUnixTimestamp(),
                                dataSecret = dataSecret,
                                traceSecretWrappers = mutableListOf()
                            )
                        }.toList().blockingGet()

                    val getTracingSecrets = checkInManager.initialize(context)
                        .andThen(checkInManager.restoreRecentTracingSecrets(TimeUnit.DAYS.toMillis(days.toLong())))
                        .map {
                            TraceSecretWrapper(
                                timestamp = it.first.toUnixTimestamp(),
                                traceSecret = it.second.encodeToBase64()
                            )
                        }
                        .cache()

                    userDataWrappers.forEachIndexed { index, element ->
                        val startUnixTimestamp = element.registrationTimestamp
                        val endUnixTimestamp = if (index < userDataWrappers.lastIndex) {
                            userDataWrappers[index + 1].registrationTimestamp
                        } else {
                            TimeUtil.getCurrentMillis().toUnixTimestamp()
                        }
                        Timber.d("Filtering trace secret wrappers between $startUnixTimestamp and $endUnixTimestamp for ${element.id}")
                        val wrappers = getTracingSecrets
                            .doOnNext { Timber.v("Checking trace secret wrapper: $it") }
                            .filter { it.timestamp in startUnixTimestamp until endUnixTimestamp }
                            .switchIfEmpty(
                                // can happen for the very first check-in due to timestamp rounding
                                getTracingSecrets.lastElement().toObservable()
                            )
                            .toList()
                            .blockingGet()
                        element.traceSecretWrappers.addAll(wrappers)
                    }

                    TransferData(userDataWrappers)
                }
            )
    }

    private fun invokeRemovalOfOldDataFromArchive(): Completable {
        return invokeDelayed(archiver.deleteDataOlderThan(ARCHIVE_DURATION), TimeUnit.SECONDS.toMillis(3))
    }

    companion object {
        const val REGISTRATION_COMPLETED_KEY = "registration_completed_2"
        const val REGISTRATION_DATA_KEY = "registration_data_2"
        const val USER_ID_KEY = "user_id"
        const val LAST_USER_ACTIVITY_REPORT_TIMESTAMP_KEY = "last_user_activity_report_timestamp"
        const val ALIAS_GUEST_KEY_PAIR = "user_master_key_pair"
        val USER_ACTIVITY_REPORT_INTERVAL = TimeUnit.DAYS.toMillis((365 / 2).toLong())
        private val ARCHIVE_DURATION = USER_ACTIVITY_REPORT_INTERVAL
        private const val KEY_ARCHIVED_REGISTRATION_DATA = "registration_archive"
    }
}
