package de.culture4life.luca.idnow

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.work.PeriodicWorkRequest
import com.nimbusds.jwt.SignedJWT
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.attestation.AttestationManager
import de.culture4life.luca.consent.ConsentManager
import de.culture4life.luca.consent.ConsentManager.Companion.ID_TERMS_OF_SERVICE_LUCA_ID
import de.culture4life.luca.crypto.*
import de.culture4life.luca.idnow.LucaIdData.VerificationStatus
import de.culture4life.luca.idnow.LucaIdData.VerificationStatus.*
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.id.IdentCreationRequestData
import de.culture4life.luca.network.pojo.id.IdentStatusResponseData
import de.culture4life.luca.pow.PowManager
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.rollout.RolloutManager
import de.culture4life.luca.rollout.RolloutManager.Companion.ID_LUCA_ID_ENROLLMENT
import de.culture4life.luca.ui.registration.VerificationException
import de.culture4life.luca.util.*
import de.culture4life.luca.whatisnew.WhatIsNewManager
import de.culture4life.luca.whatisnew.WhatIsNewManager.Companion.ID_LUCA_ID_ENROLLMENT_ERROR_MESSAGE
import de.culture4life.luca.whatisnew.WhatIsNewManager.Companion.ID_LUCA_ID_ENROLLMENT_TOKEN_MESSAGE
import de.culture4life.luca.whatisnew.WhatIsNewManager.Companion.ID_LUCA_ID_VERIFICATION_SUCCESSFUL_MESSAGE
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import org.joda.time.LocalDate
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection.HTTP_CONFLICT
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.util.concurrent.TimeUnit
import kotlin.math.max

class IdNowManager(
    private val preferencesManager: PreferencesManager,
    private val networkManager: NetworkManager,
    private val powManager: PowManager,
    private val cryptoManager: CryptoManager,
    private val whatIsNewManager: WhatIsNewManager,
    private val attestationManager: AttestationManager,
    private val rolloutManager: RolloutManager,
    private val consentManager: ConsentManager
) : Manager() {

    private var verificationStatusSubject = BehaviorSubject.create<VerificationStatus>()
    private var updateVerificationStatusWorker: NetworkWorkerExt? = null
    private var foregroundUpdateDisposable: Disposable? = null
    private var cachedLucaIdData: Maybe<LucaIdData>? = null

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            networkManager.initialize(context),
            powManager.initialize(context),
            whatIsNewManager.initialize(context),
            attestationManager.initialize(context),
            rolloutManager.initialize(context),
            consentManager.initialize(context)
        ).andThen(
            Completable.mergeArray(
                invoke(updateVerificationStatusSubject()),
                invokeDelayed(startUpdatingEnrollmentStatusIfRequired(), MINIMUM_ENROLLMENT_STATUS_UPDATE_DELAY)
            )
        )
    }

    override fun dispose() {
        super.dispose()
        foregroundUpdateDisposable?.dispose()
        updateVerificationStatusWorker = null
        cachedLucaIdData = null
    }

    /*
        Enrollment
     */

    fun isEnrollmentEnabled(): Single<Boolean> {
        return Single.defer {
            if (LucaApplication.isRunningTests()) {
                Single.just(true)
            } else {
                Single.mergeArray(
                    rolloutManager.isRolledOutToThisDevice(ID_LUCA_ID_ENROLLMENT),
                    consentManager.getConsent(ID_TERMS_OF_SERVICE_LUCA_ID).map { it.approved },
                    Single.fromCallable { attestationManager.isAttestationPossible() }
                ).all { it }
            }
        }
    }

    fun isEnrolled(): Single<Boolean> {
        return getVerificationStatus()
            .map { it == SUCCESS }
    }

    fun initiateEnrollment(): Completable {
        return hideEnrollmentErrorMessage()
            .andThen(unEnrollIfRequired())
            .andThen(attestationManager.getToken()) // includes device registration if required
            .flatMap { token -> createIdentCreationRequestData().map { data -> Pair(token, data) } }
            .flatMap { (token, data) -> networkManager.getLucaIdEndpoints().flatMap { it.createIdent(token, data) } }
            .doOnSuccess { Timber.d("Enrollment initiated") }
            .flatMapCompletable {
                Completable.mergeArray(
                    invoke(updateEnrollmentStatus()),
                    startUpdatingEnrollmentStatus(max(it.statusUpdateDelay, MINIMUM_ENROLLMENT_STATUS_UPDATE_DELAY))
                )
            }
            .onErrorResumeNext {
                Timber.w("Unable to initiate enrollment: $it")
                if (it.isHttpException(HTTP_CONFLICT)) {
                    unEnroll()
                        .andThen(initiateEnrollment())
                } else {
                    showEnrollmentErrorMessage()
                        .andThen(Completable.error(it))
                }
            }
            .doOnSubscribe { Timber.d("Initiating enrollment") }
    }

    private fun createIdentCreationRequestData(): Single<IdentCreationRequestData> {
        return cryptoManager.initialize(context)
            .andThen(
                Single.zip(
                    attestationManager.fetchNonce().subscribeOn(Schedulers.io()),
                    attestationManager.fetchNonce().subscribeOn(Schedulers.io()),
                    ::Pair
                )
            )
            .flatMap { (attestationKeyNonce, identificationKeyNonce) ->
                Single.fromCallable {
                    val encryptionPublicKey = generateEncryptionKeyPair()
                        .flatMap(::getBase64EncodedPublicKey)
                        .blockingGet()
                    val identificationPublicKey = generateIdentificationKeyPair(identificationKeyNonce)
                        .flatMap(::getBase64EncodedPublicKey)
                        .blockingGet()
                    val identificationKeyCertificates = attestationManager.getKeyAttestationResult(
                        identificationKeyNonce, IDENTIFICATION_KEY_PAIR_ALIAS
                    )
                        .map { it.certificates }
                        .map { certificates -> certificates.map { it.encoded.encodeToBase64() } }
                        .blockingGet()
                    val attestationKeySignature = CryptoManager.concatenate(attestationKeyNonce, identificationPublicKey.decodeFromBase64())
                        .flatMap { attestationManager.getEncodedSignature(it) }
                        .blockingGet()

                    IdentCreationRequestData(
                        encryptionPublicKey = encryptionPublicKey,
                        identificationPublicKey = identificationPublicKey,
                        identificationKeyNonce = String(identificationKeyNonce),
                        identificationKeyCertificates = identificationKeyCertificates,
                        attestationKeyNonce = String(attestationKeyNonce),
                        attestationKeySignature = attestationKeySignature
                    )
                }
            }
    }

    private fun generateIdentificationKeyPair(attestationChallenge: ByteArray): Single<KeyPair> {
        val identificationKeyCipherProvider = getIdentificationKeyCipherProvider()
        return if (identificationKeyCipherProvider is AuthenticationCipherProvider) {
            identificationKeyCipherProvider.generateKeyPair(IDENTIFICATION_KEY_PAIR_ALIAS, attestationChallenge, context)
        } else {
            identificationKeyCipherProvider.generateKeyPair(IDENTIFICATION_KEY_PAIR_ALIAS, context)
        }
    }

    private fun getIdentificationKeyCipherProvider(): AsymmetricCipherProvider {
        return if (LucaApplication.isRunningTests()) {
            // AndroidKeyStore provider is not available during testing
            cryptoManager.asymmetricCipherProvider
        } else {
            cryptoManager.authenticationCipherProvider
        }
    }

    private fun generateEncryptionKeyPair(): Single<KeyPair> {
        return cryptoManager.generateKeyPair(ENCRYPTION_KEY_PAIR_ALIAS)
    }

    fun getEncryptionBase64EncodedPublicKey(): Single<String> {
        return cryptoManager.initialize(context)
            .andThen(cryptoManager.getKeyPairPublicKey(ENCRYPTION_KEY_PAIR_ALIAS))
            .map { it.toBase64String(compressed = false) }
    }

    fun getIdentificationBase64EncodedPublicKey(): Single<String> {
        return cryptoManager.initialize(context)
            .andThen(getIdentificationKeyCipherProvider().getPublicKey(IDENTIFICATION_KEY_PAIR_ALIAS))
            .cast(ECPublicKey::class.java)
            .map { it.toBase64String(compressed = false) }
    }

    private fun getBase64EncodedPublicKey(keyPair: KeyPair): Single<String> {
        return Single.fromCallable { keyPair.public }
            .cast(ECPublicKey::class.java)
            .map { it.toBase64String(compressed = false) }
    }

    /*
        Un-enroll
     */
    fun unEnrollIfRequired(): Completable {
        return getVerificationStatus()
            .flatMapCompletable { status ->
                // TODO Check if QUEUED should also be deleted with ignoring error cases or not
                if (status == UNINITIALIZED) {
                    Completable.complete()
                } else {
                    unEnroll()
                }
            }
    }

    fun unEnroll(): Completable {
        return attestationManager.getToken()
            .flatMapCompletable { token ->
                networkManager.getLucaIdEndpoints()
                    .flatMapCompletable { it.deleteIdent(token) }
                    .onErrorResumeNext { throwable ->
                        if (throwable.isHttpException(HTTP_NOT_FOUND)) {
                            Completable.complete()
                        } else {
                            Completable.error(throwable)
                        }
                    }
            }
            .andThen(
                Completable.mergeArray(
                    hideEnrollmentSuccessMessage().subscribeOn(Schedulers.io()),
                    hideEnrollmentTokenMessage().subscribeOn(Schedulers.io()),
                    deleteLucaIdData().subscribeOn(Schedulers.io()),
                    deleteLastHandledVerificationStatus().subscribeOn(Schedulers.io()),
                    cryptoManager.initialize(context)
                        .andThen(getIdentificationKeyCipherProvider().keyStore.deleteEntry(IDENTIFICATION_KEY_PAIR_ALIAS))
                        .andThen(cryptoManager.deleteKeyPair(ENCRYPTION_KEY_PAIR_ALIAS))
                        .subscribeOn(Schedulers.io())
                )
            )
            .andThen(updateVerificationStatusSubject())
            .doOnSubscribe { Timber.i("Un-enrolling") }
    }

    private fun deleteIdentDataFromBackend(): Completable {
        return attestationManager.getToken()
            .flatMapCompletable { token -> networkManager.getLucaIdEndpoints().flatMapCompletable { it.deleteIdentData(token) } }
    }

    /*
        Enrollment status updates
     */

    private fun startUpdatingEnrollmentStatusIfRequired(): Completable {
        return restoreEnrollmentStatusUpdatesRequired()
            .filter { it }
            .flatMapCompletable { startUpdatingEnrollmentStatus(MINIMUM_ENROLLMENT_STATUS_UPDATE_DELAY) }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun startUpdatingEnrollmentStatus(delay: Long = 0): Completable {
        return Completable.mergeArray(
            Single.fromCallable { application.isUiCurrentlyVisible }
                .filter { it }
                .flatMapCompletable { startUpdatingEnrollmentStatusInForeground(delay) },
            startUpdatingEnrollmentStatusInBackground(delay)
        ).andThen(persistEnrollmentStatusUpdatesRequired(true))
            .doOnSubscribe { Timber.d("Starting enrollment status updates") }
    }

    private fun stopUpdatingEnrollmentStatus(): Completable {
        return Completable.mergeArray(
            stopUpdatingEnrollmentStatusInForeground(),
            stopUpdatingEnrollmentStatusInBackground()
        ).andThen(persistEnrollmentStatusUpdatesRequired(false))
            .doOnSubscribe { Timber.d("Stopping enrollment status updates") }
    }

    fun updateEnrollmentStatusIfRequired(): Completable {
        return restoreEnrollmentStatusUpdatesRequired()
            .filter { it }
            .flatMapCompletable { updateEnrollmentStatus() }
    }

    internal fun updateEnrollmentStatus(): Completable {
        return restoreLastHandledVerificationStatus()
            .flatMapCompletable { lastHandledVerificationStatus ->
                attestationManager.getToken()
                    .flatMap { token -> networkManager.getLucaIdEndpoints().flatMap { it.getIdentStatus(token) } }
                    .flatMap { responseData ->
                        validateReceiptJWS(responseData)
                            .onErrorResumeNext {
                                if (BuildConfig.DEBUG) {
                                    // Mocked JWS throws java.text.ParseException: Invalid JWS header: Not a JWS header
                                    Timber.w("Accepting mocked JWS although validation failed: $it")
                                    Completable.complete()
                                } else {
                                    unEnrollIfRequired().andThen(Completable.error(it))
                                }
                            }
                            .toSingle { responseData }
                    }
                    .map(::LucaIdData)
                    .filter { it.verificationStatus != lastHandledVerificationStatus }
                    .flatMapCompletable {
                        persistLucaIdData(it)
                            .andThen(handleVerificationStatusChange(lastHandledVerificationStatus, it.verificationStatus))
                    }
            }
            .doOnSubscribe { Timber.v("Updating enrollment status") }
            .doOnError { Timber.w("Enrollment status update failed: $it") }
    }

    private fun validateReceiptJWS(responseData: IdentStatusResponseData): Completable {
        // Validation makes no sense if id now is not involved yet or already failed
        if (responseData.state == IdentStatusResponseData.State.QUEUED || responseData.state == IdentStatusResponseData.State.FAILED) {
            return Completable.complete()
        }
        return cryptoManager.initialize(context)
            .andThen(verifySignedJWTCertificateChain(responseData.receiptJWS!!))
            .andThen(
                Completable.defer {
                    if (LucaApplication.isRunningTests()) Completable.complete()
                    else verifyReceiptJWSKeys(responseData.receiptJWS)
                }
            )
            .doOnError { Timber.e("Receipt JWS validation failed: $it") }
    }

    fun verifyReceiptJWSKeys(receiptJWS: String): Completable {
        return Single.zip(
            getIdentificationBase64EncodedPublicKey(),
            getEncryptionBase64EncodedPublicKey()
        ) { identificationKey, encryptionKey ->
            val parsedReceiptJWT = SignedJWT.parse(receiptJWS)
            val parsedBody = SerializationUtil.fromJson(parsedReceiptJWT.payload.toString(), ReceiptJWTBody::class.java).blockingGet()
            require(identificationKey == parsedBody.inputData.identificationKey) { "Binding key in receiptJWS was not correct!" }
            require(encryptionKey == parsedBody.inputData.encryptionKey) { "Encryption key in receiptJWS was not correct!" }
        }.ignoreElement()
    }

    private fun verifySignedJWTCertificateChain(signedJwt: String): Completable {
        return Completable.fromAction {
            val parsedReceiptJWT = SignedJWT.parse(signedJwt)

            // Check certificates
            val idNowRootCa = CertificateUtil.loadCertificate(IDNOW_ROOT_CA_ALIAS, context)
            val certificateChain =
                parsedReceiptJWT.header.x509CertChain.map { CertificateUtil.loadCertificate(ByteArrayInputStream(it.decode())) }
            CertificateUtil.checkCertificateChain(idNowRootCa, certificateChain)

            // Check JWT signature
            signedJwt.verifyJwt(certificateChain.first().publicKey)
        }
    }

    private fun handleVerificationStatusChange(previousStatus: VerificationStatus, updatedStatus: VerificationStatus): Completable {
        return when (updatedStatus) {
            UNINITIALIZED, QUEUED -> Completable.complete()
            PENDING -> showEnrollmentTokenMessage()
                .andThen(
                    getEnrollmentToken()
                        .doOnSuccess { Timber.i("IDnow verification web-app URL: https://mls.idnow.de/$it") }
                        .ignoreElement()
                )
            FAILED -> showEnrollmentErrorMessage()
                .andThen(stopUpdatingEnrollmentStatus()).andThen(unEnroll())
            SUCCESS -> showVerificationSuccessfulMessage()
                .andThen(stopUpdatingEnrollmentStatus())
                .andThen(deleteIdentDataFromBackend())
        }
            .andThen(persistLastHandledVerificationStatus(updatedStatus))
            .andThen(updateVerificationStatusSubject())
            .doOnSubscribe { Timber.d("Handling verification status update from $previousStatus to $updatedStatus") }
            .doOnComplete { Timber.i("Verification status updated to $updatedStatus") }
    }

    private fun restoreEnrollmentStatusUpdatesRequired(): Single<Boolean> {
        return preferencesManager.restoreOrDefault(ENROLLMENT_STATUS_UPDATES_REQUIRED_KEY, false)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun persistEnrollmentStatusUpdatesRequired(required: Boolean): Completable {
        return preferencesManager.persist(ENROLLMENT_STATUS_UPDATES_REQUIRED_KEY, required)
    }

    private fun restoreLastHandledVerificationStatus(): Single<VerificationStatus> {
        return preferencesManager.restoreOrDefault(LAST_HANDLED_VERIFICATION_STATUS_KEY, UNINITIALIZED)
    }

    private fun persistLastHandledVerificationStatus(verificationStatus: VerificationStatus): Completable {
        return preferencesManager.persist(LAST_HANDLED_VERIFICATION_STATUS_KEY, verificationStatus)
    }

    private fun deleteLastHandledVerificationStatus(): Completable {
        return preferencesManager.delete(LAST_HANDLED_VERIFICATION_STATUS_KEY)
    }

    /*
        Foreground enrollment status updates
     */

    /**
     * Starts polling the backend in the interval of [UPDATE_VERIFICATION_FOREGROUND_INTERVAL].
     * Should only be done when the app UI is visible.
     */
    private fun startUpdatingEnrollmentStatusInForeground(delay: Long = 0): Completable {
        return invokeDelayed(
            Observable.interval(0, UPDATE_VERIFICATION_FOREGROUND_INTERVAL, TimeUnit.MILLISECONDS, Schedulers.io())
                .flatMapCompletable { updateEnrollmentStatus().onErrorComplete() }
                .doOnSubscribe {
                    Timber.v("Starting enrollment status updates in foreground")
                    foregroundUpdateDisposable = it
                },
            delay
        )
    }

    private fun stopUpdatingEnrollmentStatusInForeground(): Completable {
        // needs to be delayed because this completable could actually
        // be part of the foregroundUpdateDisposable
        return invokeDelayed(
            Completable.fromAction {
                foregroundUpdateDisposable?.dispose()
                foregroundUpdateDisposable = null
            },
            TimeUnit.SECONDS.toMillis(1)
        ).doOnSubscribe { Timber.v("Stopping enrollment status updates in foreground") }
    }

    /*
        Background enrollment status updates
     */

    /**
     * Creates a worker to poll the backend in the interval of [UPDATE_VERIFICATION_BACKGROUND_INTERVAL].
     */
    private fun startUpdatingEnrollmentStatusInBackground(delay: Long): Completable {
        return Completable.defer {
            with(NetworkWorkerExt(UPDATE_VERIFICATION_STATUS_WORKER_TAG, context, managerDisposable)) {
                updateVerificationStatusWorker = this
                addWorker(
                    max(delay, UPDATE_VERIFICATION_BACKGROUND_INTERVAL),
                    UPDATE_VERIFICATION_BACKGROUND_INTERVAL,
                    LucaIdEnrollmentStatusWorker::class.java,
                    LucaIdEnrollmentStatusWorker.createWork(application)
                )
            }
        }.doOnSubscribe { Timber.v("Starting enrollment status updates in background after at least $delay ms delay") }
    }

    private fun stopUpdatingEnrollmentStatusInBackground(): Completable {
        return Completable.defer {
            updateVerificationStatusWorker?.run { removeWorker() } ?: Completable.complete()
        }.doOnSubscribe { Timber.v("Stopping enrollment status updates in background") }
    }

    /*
        ID data
     */

    fun getLucaIdDataIfAvailable(): Maybe<LucaIdData> {
        return Maybe.defer {
            if (cachedLucaIdData == null) {
                cachedLucaIdData = restoreLucaIdDataIfAvailable().cache()
            }
            cachedLucaIdData!!
        }
    }

    private fun restoreLucaIdDataIfAvailable(): Maybe<LucaIdData> {
        return preferencesManager.restoreIfAvailable(LUCA_ID_DATA_KEY, LucaIdData::class.java)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun persistLucaIdData(lucaIdData: LucaIdData): Completable {
        return preferencesManager.persist(LUCA_ID_DATA_KEY, lucaIdData)
            .doOnSubscribe { cachedLucaIdData = Maybe.just(lucaIdData) }
            .doOnComplete { Timber.v("Persisted ID data: $lucaIdData") }
    }

    private fun deleteLucaIdData(): Completable {
        return preferencesManager.delete(LUCA_ID_DATA_KEY)
            .doOnSubscribe { cachedLucaIdData = Maybe.empty() }
            .doOnComplete { Timber.v("Deleted ID data") }
    }

    fun getEnrollmentToken(): Single<String> {
        return getLucaIdDataIfAvailable()
            .mapNotNull { it.enrollmentToken }
            .toSingle()
    }

    fun getRevocationCode(): Single<String> {
        return getLucaIdDataIfAvailable()
            .map { it.revocationCode }
            .toSingle()
    }

    fun getVerificationStatus(): Single<VerificationStatus> {
        return getLucaIdDataIfAvailable()
            .mapNotNull { it.verificationStatus }
            .defaultIfEmpty(UNINITIALIZED)
    }

    fun getVerificationStatusAndChanges(): Observable<VerificationStatus> {
        return verificationStatusSubject.distinctUntilChanged()
    }

    private fun updateVerificationStatusSubject(): Completable {
        return getVerificationStatus()
            .doOnSuccess(verificationStatusSubject::onNext)
            .ignoreElement()
    }

    fun getDecryptedIdDataIfAvailable(): Maybe<LucaIdData.DecryptedIdData> {
        return getLucaIdDataIfAvailable()
            .flatMap { lucaIdData ->
                Maybe.defer {
                    when {
                        lucaIdData.decryptedIdData != null -> Maybe.just(lucaIdData.decryptedIdData)
                        lucaIdData.encryptedIdData != null -> decryptLucaIdData(lucaIdData.encryptedIdData)
                            .flatMapMaybe {
                                persistLucaIdData(lucaIdData.copy(decryptedIdData = it))
                                    .andThen(Maybe.just(it))
                            }
                        else -> Maybe.empty()
                    }
                }
            }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun decryptLucaIdData(encryptedIdData: LucaIdData.EncryptedIdData): Single<LucaIdData.DecryptedIdData> {
        return cryptoManager.initialize(context)
            .andThen(cryptoManager.getKeyPairPrivateKey(ENCRYPTION_KEY_PAIR_ALIAS))
            .map { encryptionPrivateKey ->
                // Decrypt the JWEs to get to the JWTs containing the actual VC data
                val identityJwe = CryptoManager.decryptJwe(encryptedIdData.identityJwe, encryptionPrivateKey)
                val faceJwe = CryptoManager.decryptJwe(encryptedIdData.faceJwe, encryptionPrivateKey)

                // Verify the VC JWTs
                if (!BuildConfig.DEBUG) {
                    Completable.mergeArray(
                        verifySignedJWTCertificateChain(identityJwe.payload.toString()),
                        verifySignedJWTCertificateChain(faceJwe.payload.toString())
                    ).blockingAwait()
                }

                // Parse JWTs and get VC out of their payload
                val identityJwtPayload = identityJwe.payload.toSignedJWT().payload.toJSONObject()
                val faceJwtPayload = faceJwe.payload.toSignedJWT().payload.toJSONObject()

                verifyDidKey(identityJwtPayload)
                verifyDidKey(faceJwtPayload)

                val identityVC = identityJwtPayload["vc"].toString().deserializeFromJson(VerifiedCredentialsIdentity::class.java)
                val faceVC = faceJwtPayload["vc"].toString().deserializeFromJson(VerifiedCredentialsImage::class.java)

                LucaIdData.DecryptedIdData(
                    firstName = identityVC.credentialSubject.givenName,
                    lastName = identityVC.credentialSubject.familyName,
                    birthdayTimestamp = LocalDate.parse(identityVC.credentialSubject.birthDate)
                        .toDateTimeAtStartOfDay(TimeUtil.GERMAN_TIMEZONE)
                        .toInstant()
                        .millis,
                    validSinceTimestamp = (identityJwtPayload["nbf"] as Long).fromUnixTimestamp(),
                    image = faceVC.credentialSubject.image
                )
            }
            .doOnSuccess { Timber.i("Decrypted ID data: $it") }
            .doOnSubscribe { Timber.d("Decrypting encrypted ID data: $encryptedIdData") }
    }

    private fun verifyDidKey(identityJwtPayload: MutableMap<String, Any>) {
        val decentralizedIdentifierKey = DecentralizedIdentifierKey(identityJwtPayload["sub"].toString())
        if (BuildConfig.DEBUG && decentralizedIdentifierKey.input == MOCKED_DID_KEY) {
            // if IDnow flow is mocked we only receive a mocked key
            return
        }
        val decodedKeyByteArray = decentralizedIdentifierKey.decodedKey
        val actualKey = AsymmetricCipherProvider.decodePublicKey(decodedKeyByteArray).blockingGet()

        val expectedEncodedKey = getIdentificationBase64EncodedPublicKey().blockingGet()
        val expectedKey = AsymmetricCipherProvider.decodePublicKey(expectedEncodedKey.decodeFromBase64()).blockingGet()

        if (!expectedKey.equals(actualKey)) {
            throw VerificationException("Unable to verify that this ID is meant for you")
        }
    }

    /*
        What's new messages
     */

    private fun showEnrollmentTokenMessage() = showMessage(ID_LUCA_ID_ENROLLMENT_TOKEN_MESSAGE)

    private fun showEnrollmentErrorMessage() = showMessage(ID_LUCA_ID_ENROLLMENT_ERROR_MESSAGE)

    private fun showVerificationSuccessfulMessage() = showMessage(ID_LUCA_ID_VERIFICATION_SUCCESSFUL_MESSAGE)

    private fun hideEnrollmentErrorMessage() =
        whatIsNewManager.updateMessage(ID_LUCA_ID_ENROLLMENT_ERROR_MESSAGE) { copy(enabled = false) }

    private fun hideEnrollmentSuccessMessage() =
        whatIsNewManager.updateMessage(ID_LUCA_ID_VERIFICATION_SUCCESSFUL_MESSAGE) { copy(enabled = false) }

    private fun hideEnrollmentTokenMessage() =
        whatIsNewManager.updateMessage(ID_LUCA_ID_ENROLLMENT_TOKEN_MESSAGE) { copy(enabled = false) }

    private fun showMessage(key: String): Completable {
        return whatIsNewManager.updateMessage(key) {
            copy(
                enabled = true,
                seen = false,
                notified = false,
                timestamp = TimeUtil.getCurrentMillis()
            )
        }
    }

    companion object {
        val MINIMUM_ENROLLMENT_STATUS_UPDATE_DELAY = TimeUnit.SECONDS.toMillis(10)
        val UPDATE_VERIFICATION_BACKGROUND_INTERVAL =
            if (BuildConfig.DEBUG) PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS else TimeUnit.HOURS.toMillis(6)
        val UPDATE_VERIFICATION_FOREGROUND_INTERVAL =
            if (BuildConfig.DEBUG) TimeUnit.SECONDS.toMillis(30) else TimeUnit.MINUTES.toMillis(5)
        private const val UPDATE_VERIFICATION_STATUS_WORKER_TAG = "luca_id_update_verification_status"
        private const val LUCA_ID_DATA_KEY = "luca_id_data"
        private const val ENROLLMENT_STATUS_UPDATES_REQUIRED_KEY = "luca_id_enrollment_status_updates_required"
        private const val LAST_HANDLED_VERIFICATION_STATUS_KEY = "luca_id_last_handled_verification_status"
        private const val IDENTIFICATION_KEY_PAIR_ALIAS = "luca_id_identification_key_pair"
        private const val ENCRYPTION_KEY_PAIR_ALIAS = "luca_id_encryption_key_pair"
        private val IDNOW_ROOT_CA_ALIAS = if (BuildConfig.DEBUG) "idnow_test_root_ca.pem" else "idnow_root_ca.pem"
        private const val MOCKED_DID_KEY = "did:key:zDnaeRpHbgydzda9Ykn8rKeTrCHre4Lk42yk8oCb6nqwVoi9L"
        private const val ID_NOW_PACKAGE_NAME = "io.idnow.autoident"
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        val ID_NOW_PLAY_STORE_URI: Uri = Uri.parse("https://play.google.com/store/apps/details?id=$ID_NOW_PACKAGE_NAME")

        fun createIdNowIntent(context: Context, token: String): Intent {
            val packageIntent = context.getLaunchIntentForPackage(ID_NOW_PACKAGE_NAME, createIdNowDeeplinkUri(token))
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            // if app is not installed, deeplink to the play store
            return packageIntent ?: createIdNowPlayStoreIntent()
        }

        fun createIdNowPlayStoreIntent() = Intent(Intent.ACTION_VIEW, ID_NOW_PLAY_STORE_URI).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        private fun createIdNowDeeplinkUri(token: String): Uri = Uri.parse("https://mls.idnow.de/$token")
    }
}
