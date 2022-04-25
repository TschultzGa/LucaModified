package de.culture4life.luca.attestation

import android.content.Context
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.safetynet.SafetyNet
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.Manager
import de.culture4life.luca.crypto.CryptoManager
import de.culture4life.luca.network.LucaApiException
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.attestation.AttestationRegistrationRequestData
import de.culture4life.luca.network.pojo.attestation.AttestationTokenRequestData
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.util.*
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.net.HttpURLConnection.HTTP_NOT_FOUND
import java.security.KeyPair

open class AttestationManager(
    private val preferencesManager: PreferencesManager,
    private val networkManager: NetworkManager,
    private val cryptoManager: CryptoManager // initialization deferred to first use
) : Manager() {

    private var token: String? = null

    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            networkManager.initialize(context)
        )
    }

    override fun dispose() {
        token = null
        super.dispose()
    }

    fun isAttestationPossible(): Boolean {
        return isHardwareKeyAttestationAvailable() && isSafetyNetAvailable()
    }

    /*
        Registration
     */

    /**
     * Performs the initial device registration, during which the attestation
     * results are verified on the backend. After a successful registration,
     * an attestation token can be obtained to authenticate other API requests.
     */
    fun registerDevice(): Completable {
        return deleteKeyAttestationKeyPair()
            .andThen(fetchNonce())
            .flatMap(::createRegistrationRequestData)
            .flatMap { requestData ->
                networkManager.getAttestationEndpoints()
                    .flatMap { it.registerDevice(requestData) }
                    .onErrorResumeNext { Single.error(LucaApiException(it)) }
            }
            .map { it.deviceId }
            .doOnSuccess { Timber.i("Registered device: $it") }
            .flatMapCompletable(::persistDeviceId)
            .onErrorResumeNext { Completable.error(AttestationException("Device registration failed", it)) }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun createRegistrationRequestData(nonce: ByteArray): Single<AttestationRegistrationRequestData> {
        return Single.defer {
            val keyAttestationNonce = generateKeyAttestationNonce(nonce).blockingGet()
            val encodedKeyAttestationPublicKey = getKeyAttestationKeyPair(keyAttestationNonce)
                .map { it.public }
                .map { it.encoded.encodeToBase64() }
                .blockingGet()

            val getEncodedKeyAttestationCertificates = getKeyAttestationResult(keyAttestationNonce)
                .map { it.certificates }
                .map { certificates -> certificates.map { it.encoded.encodeToBase64() } }

            val getEncodedSafetyNetJws = generateSafetyNetNonce(nonce)
                .flatMap(::getSafetyNetAttestationResult)
                .map { it.jws!! }

            Single.zip(
                getEncodedKeyAttestationCertificates.subscribeOn(Schedulers.io()),
                getEncodedSafetyNetJws.subscribeOn(Schedulers.io())
            ) { certificates, jws ->
                AttestationRegistrationRequestData(
                    baseNonce = String(nonce),
                    keyAttestationPublicKey = encodedKeyAttestationPublicKey,
                    keyAttestationCertificates = certificates,
                    safetyNetAttestationJws = jws
                )
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun generateKeyAttestationNonce(baseNonce: ByteArray): Single<ByteArray> {
        return CryptoManager.concatenate(baseNonce, KEY_ATTESTATION_NONCE_SUFFIX)
            .flatMap(cryptoManager::hash)
            .doOnSuccess { Timber.v("Generated key attestation nonce: ${it.encodeToHex()}") }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    fun generateSafetyNetNonce(baseNonce: ByteArray): Single<ByteArray> {
        return CryptoManager.concatenate(baseNonce, KEY_SAFETY_NET_NONCE_SUFFIX)
            .flatMap(cryptoManager::hash)
            .doOnSuccess { Timber.v("Generated SafetyNet nonce: ${it.encodeToHex()}") }
    }

    /*
        Token
     */

    /**
     * Either emits a previously fetched attestation token or requests a new one.
     * Performs device registration if required.
     *
     * Example JWT:
     * ```
     * {
     *   "os": "android",
     *   "sub": "e7310663-a5e3-43d3-a729-3fdc8f01cda8",
     *   "iss": "luca-attestation",
     *   "iat": 1644584400,
     *   "exp": 1644585000
     * }
     * ```
     */
    fun getToken(): Single<String> {
        return getTokenIfAvailable()
            .switchIfEmpty(
                fetchToken()
                    .onErrorResumeNext { Single.error(AttestationException("Unable to get attestation token", it)) }
                    .doOnSuccess { token = it }
            )
    }

    private fun getTokenIfAvailable(): Maybe<String> {
        return Maybe.fromCallable<String> {
            val availableToken = token
            if (availableToken != null) {
                // needs to be validated on every use as it's very short lived
                with(availableToken.parseJwt()) {
                    require(header["alg"] == "ES256")
                    require(body["iss"] == "luca-attestation")
                    require(body["os"] == "android")
                    require(body["exp"] as Int > TimeUtil.getCurrentMillis().toUnixTimestamp())
                }
            }
            availableToken
        }.onErrorComplete()
    }

    private fun fetchToken(): Single<String> {
        return fetchNonce()
            .flatMap(::createTokenRequestData)
            .flatMap { requestData ->
                networkManager.getAttestationEndpoints()
                    .flatMap { it.getAttestationToken(requestData) }
                    .onErrorResumeNext {
                        if (it.isHttpException(HTTP_NOT_FOUND)) {
                            // device is not registered anymore, reset the device ID
                            deleteDeviceId().andThen(Single.error(it))
                        } else {
                            Single.error(it)
                        }
                    }
            }
            .map { it.jwt }
    }

    private fun createTokenRequestData(nonce: ByteArray): Single<AttestationTokenRequestData> {
        return restoreDeviceIdIfAvailable()
            .switchIfEmpty(registerDevice().andThen(restoreDeviceIdIfAvailable()))
            .toSingle()
            .flatMap { createTokenRequestData(it, nonce) }
    }

    fun getEncodedSignature(data: ByteArray): Single<String> {
        return getKeyAttestationKeyPair()
            .flatMap { keyPair ->
                cryptoManager.hash(data)
                    .flatMap { cryptoManager.attestationSignatureProvider.sign(it, keyPair.private) }
            }
            .map { it.encodeToBase64() }
    }

    private fun createTokenRequestData(deviceId: String, nonce: ByteArray): Single<AttestationTokenRequestData> {
        return getEncodedSignature(nonce)
            .map {
                AttestationTokenRequestData(
                    deviceId = deviceId,
                    nonce = String(nonce),
                    signature = it
                )
            }
    }

    /*
        Nonce
     */

    fun fetchNonce(): Single<ByteArray> {
        return networkManager.getAttestationEndpoints()
            .flatMap { it.getNonce }
            .map { it.nonce.toByteArray() }
    }

    /*
        Key attestation
     */

    /**
     * @see [Verifying hardware-backed key pairs with Key Attestation](https://developer.android.com/training/articles/security-key-attestation)
     */
    fun getKeyAttestationResult(nonce: ByteArray, alias: String = ALIAS_ATTESTATION): Single<KeyAttestationResult> {
        return Completable.defer {
            if (!isHardwareKeyAttestationAvailable()) {
                Completable.error(IllegalStateException("Hardware-level key attestation is not available"))
            } else {
                cryptoManager.initialize(application)
            }
        }.andThen(cryptoManager.attestationCipherProvider.getCertificateChain(alias))
            .toList()
            .doOnError { Timber.w("Unable to get certificate chain: $it") }
            .onErrorReturnItem(emptyList())
            .map { KeyAttestationResult(nonce = nonce, certificates = it) }
            .onErrorResumeNext { Single.error(KeyAttestationException(it)) }
    }

    private fun getKeyAttestationKeyPair(nonce: ByteArray? = null): Single<KeyPair> {
        return Completable.defer { cryptoManager.initialize(application) }
            .andThen(cryptoManager.attestationCipherProvider.getKeyPair(ALIAS_ATTESTATION))
            .doOnSuccess { Timber.d("Restored attestation key pair") }
            .onErrorResumeNext {
                if (nonce != null) {
                    cryptoManager.attestationCipherProvider.generateKeyPair(ALIAS_ATTESTATION, nonce, context)
                        .doOnSuccess { Timber.d("Generated attestation key pair with nonce: ${nonce.encodeToHex()}") }
                } else {
                    Single.error(it)
                }
            }
            .doOnError { Timber.w("Unable to get attestation key pair: $it") }
    }

    fun deleteKeyAttestationKeyPair(): Completable {
        return Completable.defer { cryptoManager.initialize(application) }
            .andThen(cryptoManager.androidKeyStore.deleteEntry(ALIAS_ATTESTATION))
            .doOnComplete { Timber.d("Deleted key attestation key pair") }
    }

    /**
     * Checks if hardware-level key attestation could be available.
     *
     * @see [Retrieve and verify a hardware-backed key pair](https://developer.android.com/training/articles/security-key-attestation#verifying)
     */
    private fun isHardwareKeyAttestationAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    /*
        SafetyNet
     */

    /**
     * @see [SafetyNet Attestation API](https://developer.android.com/training/safetynet/attestation)
     */
    fun getSafetyNetAttestationResult(nonce: ByteArray): Single<SafetyNetAttestationResult> {
        // TODO: Add automatic retries to deal with occasional internal errors, e.g. com.google.android.gms.common.api.ApiException: 7
        return Single.defer {
            if (!isSafetyNetAvailable()) {
                Single.error(IllegalStateException("SafetyNet is not available"))
            } else {
                RxTasks.toSingle(SafetyNet.getClient(context).attest(nonce, BuildConfig.SAFETY_NET_API_KEY))
                    .map { SafetyNetAttestationResult(nonce = nonce, jws = it.jwsResult) }
            }
        }.doOnError { Timber.w("Unable to get SafetyNet attestation result: $it") }
            .onErrorResumeNext { Single.error(SafetyNetAttestationException(it)) }
    }

    /**
     * Checks if Google Play Services are available with the required version.
     *
     * @see [Check the Google Play services version](https://developer.android.com/training/safetynet/attestation#check-google-play-services-version)
     */
    private fun isSafetyNetAvailable(): Boolean = GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context, MINIMUM_PLAY_SERVICES_VERSION) == ConnectionResult.SUCCESS

    /*
        Device ID
     */

    private fun restoreDeviceIdIfAvailable(): Maybe<String> {
        return preferencesManager.restoreIfAvailable(KEY_DEVICE_ID, String::class.java)
    }

    private fun persistDeviceId(deviceId: String): Completable {
        return preferencesManager.persist(KEY_DEVICE_ID, deviceId)
    }

    private fun deleteDeviceId(): Completable {
        return preferencesManager.delete(KEY_DEVICE_ID)
    }

    companion object {
        private const val ALIAS_ATTESTATION = "attestation"
        private const val KEY_DEVICE_ID = "attestation_device_id"
        private const val MINIMUM_PLAY_SERVICES_VERSION = 13000000
        private val KEY_SAFETY_NET_NONCE_SUFFIX = byteArrayOf(0x01)
        private val KEY_ATTESTATION_NONCE_SUFFIX = byteArrayOf(0x02)
    }
}
