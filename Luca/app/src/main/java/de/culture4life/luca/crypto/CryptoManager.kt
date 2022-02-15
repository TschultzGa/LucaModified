package de.culture4life.luca.crypto

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import com.nexenio.rxkeystore.RxKeyStore
import com.nexenio.rxkeystore.provider.mac.RxMacException
import com.nexenio.rxkeystore.provider.signature.RxSignatureException
import de.culture4life.luca.LucaApplication
import de.culture4life.luca.Manager
import de.culture4life.luca.genuinity.GenuinityManager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.KeyIssuerResponseData
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.util.*
import de.culture4life.luca.util.SingleUtil.retryWhen
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.*
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.util.*
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec


/**
 * Provides access to all cryptographic methods, handles Bouncy-Castle key store persistence.
 */
@SuppressLint("NewApi")
open class CryptoManager constructor(
    private val preferencesManager: PreferencesManager,
    private val networkManager: NetworkManager,
    private val genuinityManager: GenuinityManager
) : Manager() {

    private val androidKeyStore: RxKeyStore
    private val bouncyCastleKeyStore: RxKeyStore

    private val secureRandom: SecureRandom
    private val signatureProvider: SignatureProvider
    private val macProvider: MacProvider
    private val hashProvider: HashProvider
    private val wrappingCipherProvider: WrappingCipherProvider
    val symmetricCipherProvider: SymmetricCipherProvider
    val asymmetricCipherProvider: AsymmetricCipherProvider

    private var dailyPublicKeyData: DailyPublicKeyData? = null

    init {
        androidKeyStore = RxKeyStore(RxKeyStore.TYPE_ANDROID, getAndroidKeyStoreProviderName())
        bouncyCastleKeyStore = RxKeyStore(RxKeyStore.TYPE_BKS, RxKeyStore.PROVIDER_BOUNCY_CASTLE)
        secureRandom = SecureRandom()
        signatureProvider = SignatureProvider(bouncyCastleKeyStore)
        macProvider = MacProvider(bouncyCastleKeyStore)
        hashProvider = HashProvider(bouncyCastleKeyStore)
        wrappingCipherProvider = WrappingCipherProvider(androidKeyStore)
        symmetricCipherProvider = SymmetricCipherProvider(bouncyCastleKeyStore)
        asymmetricCipherProvider = AsymmetricCipherProvider(bouncyCastleKeyStore)
    }

    /**
     * Initialize manager, setup security providers and load [.bouncyCastleKeyStore].
     */
    override fun doInitialize(context: Context): Completable {
        return Completable.mergeArray(
            preferencesManager.initialize(context),
            networkManager.initialize(context),
            genuinityManager.initialize(context)
        ).andThen(Completable.fromAction { this.context = context })
            .andThen(setupSecurityProviders())
            .andThen(loadKeyStoreFromFile().onErrorComplete())
            .andThen(deleteOldPreferencesIfRequired())
    }

    /**
     * Delete preferences that have been used instead in app versions before 2.3.0.
     */
    private fun deleteOldPreferencesIfRequired(): Completable {
        return Completable.fromAction {
            Completable.mergeArray(
                preferencesManager.delete(DAILY_KEY_PAIR_PUBLIC_KEY_ID_KEY),
                preferencesManager.delete(DAILY_KEY_PAIR_PUBLIC_KEY_POINT_KEY),
                preferencesManager.delete(DAILY_KEY_PAIR_CREATION_TIMESTAMP_KEY)
            ).onErrorComplete()
                .subscribeOn(Schedulers.io())
                .subscribe()
        }
    }

    private fun getAndroidKeyStoreProviderName(): String? {
        val availableProviders: MutableSet<String> = HashSet()
        for (provider in Security.getProviders()) {
            availableProviders.add(provider.name)
        }
        Timber.i("Available security providers: %s", availableProviders)
        val hasWorkaroundProvider = availableProviders.contains("AndroidKeyStoreBCWorkaround")
        return if (hasWorkaroundProvider && Build.VERSION.SDK_INT != Build.VERSION_CODES.M) {
            // Not using the default provider (null), will cause a NoSuchAlgorithmException.
            // See https://stackoverflow.com/questions/36111452/androidkeystore-nosuchalgorithm-exception
            Timber.d("BC workaround provider present, using default provider")
            null
        } else {
            RxKeyStore.PROVIDER_ANDROID_KEY_STORE
        }
    }

    /**
     * Load [.bouncyCastleKeyStore] from internal file, decrypt it using [.androidKeyStore]-backed password.
     *
     * @see WrappedSecret
     */
    private fun loadKeyStoreFromFile(): Completable {
        return getKeyStorePassword()
            .flatMapCompletable { passwordChars ->
                Single.fromCallable { context.openFileInput(KEYSTORE_FILE_NAME) }
                    .flatMapCompletable { inputStream ->
                        bouncyCastleKeyStore.load(inputStream, passwordChars)
                            .doFinally { inputStream.close() }
                    }
            }
            .retry(1)
            .doOnSubscribe { Timber.d("Loading keystore from file") }
            .doOnError { Timber.w("Unable to load keystore from file: %s", it.toString()) }
    }

    /**
     * Store BC keystore using [.androidKeyStore]-backed password.
     *
     * @see WrappedSecret
     */
    fun persistKeyStoreToFile(): Completable {
        return getKeyStorePassword()
            .observeOn(Schedulers.io())
            .flatMapCompletable { passwordChars ->
                Single.fromCallable { context.openFileOutput(KEYSTORE_FILE_NAME, Context.MODE_PRIVATE) }
                    .flatMapCompletable { outputStream ->
                        bouncyCastleKeyStore.save(outputStream, passwordChars)
                            .doFinally { outputStream.close() }
                    }
            }
            .retry(1)
            .doOnSubscribe { Timber.d("Persisting keystore to file") }
            .doOnError { Timber.e("Unable to persist keystore to file: %s", it.toString()) }
    }

    /**
     * Fetch [.bouncyCastleKeyStore] password if available, otherwise generate new random
     * password and persist it using [.androidKeyStore]-backed [WrappedSecret]}.
     */
    private fun getKeyStorePassword(): Single<CharArray> {
        return restoreWrappedSecretIfAvailable(ALIAS_KEYSTORE_PASSWORD)
            .switchIfEmpty(generateSecureRandomData(128)
                .doOnSuccess { Timber.d("Generated new random key store password") }
                .flatMap { persistWrappedSecret(ALIAS_KEYSTORE_PASSWORD, it).andThen(Single.just(it)) })
            .map(ByteArray::toCharArray)
    }

    /*
        Secret wrapping
     */

    /**
     * Will get or generate a key pair using the [.wrappingCipherProvider] which may be used
     * for restoring or persisting [WrappedSecret]s.
     */
    private fun getSecretWrappingKeyPair(): Single<KeyPair> {
        return wrappingCipherProvider.getKeyPairIfAvailable(ALIAS_SECRET_WRAPPING_KEY_PAIR)
            .switchIfEmpty(wrappingCipherProvider.generateKeyPair(ALIAS_SECRET_WRAPPING_KEY_PAIR, context)
                .doOnSubscribe { Timber.d("Generating new secret wrapping key pair") }
                .doOnError { Timber.e("Unable to generate secret wrapping key pair: %s", it.toString()) })
            .compose(retryWhen(KeyStoreException::class.java, 3))
    }

    /**
     * Will restore the [WrappedSecret] using the [.preferencesManager] and decrypt it
     * using the [.wrappingCipherProvider].
     *
     * [WrappedSecret]s are encrypted using an AndroidKeyStore-backed key.
     */
    fun restoreWrappedSecretIfAvailable(alias: String): Maybe<ByteArray> {
        return preferencesManager.restoreIfAvailable(alias, WrappedSecret::class.java)
            .flatMapSingle { wrappedSecret ->
                getSecretWrappingKeyPair()
                    .flatMap { keyPair ->
                        wrappingCipherProvider.decrypt(wrappedSecret.deserializedEncryptedSecret, wrappedSecret.deserializedIv, keyPair.private)
                            .compose(retryWhen(KeyStoreException::class.java, 3))
                    }
            }
            .doOnError { Timber.e("Unable to restore wrapped secret: %s", it.toString()) }
    }

    /**
     * Will encrypt the specified secret using the [.wrappingCipherProvider] and persist it as
     * a [WrappedSecret] using the [.preferencesManager].
     */
    fun persistWrappedSecret(alias: String, secret: ByteArray): Completable {
        return getSecretWrappingKeyPair()
            .flatMap { keyPair ->
                wrappingCipherProvider.encrypt(secret, keyPair.public)
                    .compose(retryWhen(KeyStoreException::class.java, 3))
            }
            .map(::WrappedSecret)
            .flatMapCompletable { preferencesManager.persist(alias, it) }
            .doOnError { Timber.e("Unable to persist wrapped secret: %s", it.toString()) }
    }

    /*
        Hashing
     */

    fun hash(data: ByteArray?): Single<ByteArray> {
        return hashProvider.hash(data ?: ByteArray(0))
    }

    fun concatenateHashes(vararg data: ByteArray?): Single<ByteArray> {
        return Single.fromCallable { data.map { it ?: ByteArray(0) } }
            .map { Observable.fromIterable(it) }
            .flatMap(::concatenateHashes)
    }

    /**
     * Hash each value using the [.hashProvider] and concatenate the results.
     */
    fun concatenateHashes(data: Observable<ByteArray>): Single<ByteArray> {
        return data.flatMapSingle { hash(it) }
            .toList()
            .map { hashes ->
                var concatenatedHashes = ByteArray(0)
                for (hash in hashes) {
                    concatenatedHashes += hash
                }
                concatenatedHashes
            }
    }

    /*
        Hash message authentication code
     */

    fun hmac(data: ByteArray, secret: ByteArray): Single<ByteArray> {
        return Single.fromCallable { secret.toSecretKey() }
            .flatMap { hmac(data, it) }
    }

    /**
     * Emits a hash-based message authentication code.
     * Can be used instead of signatures if a shared secret is available.
     */
    fun hmac(data: ByteArray, secretKey: SecretKey): Single<ByteArray> {
        return macProvider.sign(data, secretKey)
    }

    fun verifyHmac(data: ByteArray, mac: ByteArray, secret: ByteArray): Completable {
        return Single.fromCallable { secret.toSecretKey() }
            .flatMapCompletable { verifyHmac(data, mac, it) }
    }

    /**
     * Verifies a hash-based message authentication code. See [hmac] fore reference.
     */
    fun verifyHmac(data: ByteArray, mac: ByteArray, secretKey: SecretKey): Completable {
        return macProvider.verify(data, mac, secretKey)
    }

    /*
        HMAC key derivation
     */

    /**
     * Derives a secret using HMAC-SHA256. Can be used to convert shared secrets (see [ecdh])
     * to key material usable for encryption or authentication.
     */
    fun hkdf(ikm: ByteArray, salt: ByteArray?, label: ByteArray, length: Int): Single<ByteArray> {
        return Single.fromCallable {
            val digest = SHA256Digest()
            val parameters = HKDFParameters(ikm, salt, label)
            val generator = HKDFBytesGenerator(digest)
            val output = ByteArray(length)
            generator.init(parameters)
            generator.generateBytes(output, 0, output.size)
            output
        }
    }

    /*
        Elliptic Curve Integrated Encryption Scheme
     */

    /**
     * Convenience method for [eciesEncrypt] that uses the currently available daily key ([getDailyPublicKey]) as receiver.
     */
    fun eciesEncrypt(data: ByteArray): Single<EciesResult> {
        return getDailyPublicKey()
            .flatMap { eciesEncrypt(data, it.publicKey) }
    }

    /**
     * Convenience method for [eciesEncrypt] that uses a new generated ephemeral key pair.
     */
    fun eciesEncrypt(data: ByteArray, receiverPublicKey: PublicKey): Single<EciesResult> {
        return asymmetricCipherProvider.generateKeyPair("ecies_ephemeral", context)
            .flatMap { eciesEncrypt(data, it, receiverPublicKey) }
    }

    /**
     * Encrypt the data and calculate a MAC.
     *
     * The encrypted data is generated using an encryption secret ([generateDataEncryptionSecret])
     * derived from the Diffie–Hellman secret ([ecdh]) of the provided keys.
     *
     * The MAC is generated using an authentication secret ([generateDataAuthenticationSecret])
     * derived from the Diffie–Hellman secret ([ecdh]) of the provided keys.
     *
     * The IV is random.
     */
    fun eciesEncrypt(data: ByteArray, ephemeralKeyPair: KeyPair, receiverPublicKey: PublicKey): Single<EciesResult> {
        return ecdh(ephemeralKeyPair.private, receiverPublicKey)
            .flatMap(::generateEncryptionAndAuthenticationKeys)
            .flatMap { (encryptionKey, authenticationKey) ->
                symmetricCipherProvider.encrypt(data, encryptionKey)
                    .map { Pair(it.first, it.second) }
                    .flatMap { (encryptedData, iv) ->
                        hmac(encryptedData, authenticationKey)
                            .map { mac ->
                                EciesResult(
                                    encryptedData = encryptedData,
                                    iv = iv,
                                    mac = mac,
                                    ephemeralPublicKey = ephemeralKeyPair.public as ECPublicKey
                                )
                            }
                    }
            }
    }

    /**
     * Decrypt data of the [EciesResult] and verify the MAC. Emits the decrypted data
     * if the MAC is valid or an [RxMacException] if not. See [eciesEncrypt] for reference.
     */
    fun eciesDecrypt(eciesResult: EciesResult, receiverPrivateKey: PrivateKey): Single<ByteArray> {
        return ecdh(receiverPrivateKey, eciesResult.ephemeralPublicKey)
            .flatMap(::generateEncryptionAndAuthenticationKeys)
            .flatMap { (encryptionKey, authenticationKey) ->
                symmetricCipherProvider.decrypt(eciesResult.encryptedData, eciesResult.iv, encryptionKey)
                    .flatMap { decryptedData ->
                        verifyHmac(eciesResult.encryptedData, eciesResult.mac, authenticationKey)
                            .andThen(Single.just(decryptedData))
                    }
            }
    }

    private fun generateEncryptionAndAuthenticationKeys(baseSecret: ByteArray): Single<Pair<SecretKey, SecretKey>> {
        return Single.zip(
            generateDataEncryptionSecret(baseSecret).map { it.toSecretKey() },
            generateDataAuthenticationSecret(baseSecret).map { it.toSecretKey() },
            { encryptionKey, authenticationKey -> Pair(encryptionKey, authenticationKey) }
        )
    }

    /**
     * Generate data encryption secret by appending [.DATA_ENCRYPTION_SECRET_SUFFIX] to given
     * baseSecret, hashing it and trimming it to a length of 16.
     */
    fun generateDataEncryptionSecret(baseSecret: ByteArray): Single<ByteArray> {
        return concatenate(baseSecret, DATA_ENCRYPTION_SECRET_SUFFIX)
            .flatMap(::hash)
            .map { it.trim(HashProvider.TRIMMED_HASH_LENGTH) }
    }

    /**
     * Generate data authentication secret by appending [.DATA_AUTHENTICATION_SECRET_SUFFIX]
     * to given baseSecret and hash it.
     */
    fun generateDataAuthenticationSecret(baseSecret: ByteArray): Single<ByteArray> {
        return concatenate(baseSecret, DATA_AUTHENTICATION_SECRET_SUFFIX)
            .flatMap(::hash)
    }

    /*
        Elliptic-curve Diffie–Hellman
     */

    /**
     * Convenience method for [ecdh] that uses the currently available daily key ([getDailyPublicKey]) as public key.
     */
    fun ecdh(privateKey: PrivateKey): Single<ByteArray> {
        return getDailyPublicKey()
            .flatMap { ecdh(privateKey, it.publicKey) }
    }

    /**
     * Emits a shared secret that can be used for deriving other keys.
     * Note that the private and public keys should belong to different key pairs.
     */
    fun ecdh(privateKey: PrivateKey, publicKey: PublicKey): Single<ByteArray> {
        return asymmetricCipherProvider.generateSecret(privateKey, publicKey)
    }

    /*
        Elliptic-curve Digital Signature Algorithm
     */

    fun ecdsa(data: ByteArray, keyAlias: String): Single<ByteArray> {
        return getKeyPairPrivateKey(keyAlias)
            .flatMap { ecdsa(data, it) }
    }

    /**
     * Signs data with the given private key.
     *
     * @return The signature in IEEE P.1363 format (binary concatenation of r and s)
     */
    fun ecdsa(data: ByteArray, privateKey: PrivateKey): Single<ByteArray> {
        return signatureProvider.sign(data, privateKey)
    }

    /**
     * Verifies the given signature. Emits [RxSignatureException] if the signature is invalid.
     *
     * @param [data] The signed data
     * @param [signature] The signature in IEEE P.1363 format
     * @param [publicKey] The public key
     */
    fun verifyEcdsa(data: ByteArray, signature: ByteArray, publicKey: PublicKey): Completable {
        return signatureProvider.verify(data, signature, publicKey)
    }

    /*
        Generic EC key pair CRUD using the [.bouncyCastleKeyStore]
     */

    /**
     * Will attempt to restore a key pair that has previously been persisted with the specified alias.
     * If none is available, a new one will be generated and persisted.
     */
    fun getKeyPair(alias: String = "ephemeral"): Single<KeyPair> {
        return restoreKeyPair(alias)
            .switchIfEmpty(generateKeyPair(alias))
    }

    /**
     * Convenience method that will emit the private key from [.getKeyPair].
     */
    fun getKeyPairPrivateKey(alias: String = "ephemeral"): Single<ECPrivateKey> {
        return getKeyPair(alias).map { it.private }
            .cast(ECPrivateKey::class.java)
    }

    /**
     * Convenience method that will emit the public key from [.getKeyPair].
     */
    fun getKeyPairPublicKey(alias: String = "ephemeral"): Single<ECPublicKey> {
        return getKeyPair(alias).map { it.public }
            .cast(ECPublicKey::class.java)
    }

    /**
     * Will generate a new key pair using the [.asymmetricCipherProvider] and persist it.
     * Existing key store entries with the specified alias will be overwritten.
     */
    fun generateKeyPair(alias: String = "ephemeral"): Single<KeyPair> {
        return asymmetricCipherProvider.generateKeyPair(alias, context)
            .flatMap { persistKeyPair(alias, it).andThen(Single.just(it)) }
            .doOnSuccess { Timber.d("Generated new key pair for alias: %s", alias) }
    }

    /**
     * Will attempt to restore a key pair that has previously been persisted with the specified alias.
     */
    fun restoreKeyPair(alias: String = "ephemeral"): Maybe<KeyPair> {
        return asymmetricCipherProvider.getKeyPairIfAvailable(alias)
    }

    /**
     * Will persist the specified key pair using the specified alias at the [.bouncyCastleKeyStore].
     */
    fun persistKeyPair(alias: String = "ephemeral", keyPair: KeyPair): Completable {
        return asymmetricCipherProvider.setKeyPair(alias, keyPair)
            .andThen(persistKeyStoreToFile())
            .doOnComplete { Timber.d("Persisted key pair for alias: %s", alias) }
    }

    /**
     * Will delete the key store entry with the specified alias from the [.bouncyCastleKeyStore].
     */
    fun deleteKeyPair(alias: String = "ephemeral"): Completable {
        return bouncyCastleKeyStore.deleteEntry(alias)
            .andThen(persistKeyStoreToFile())
            .doOnComplete { Timber.d("Deleted key pair for alias: %s", alias) }
    }

    /*
        Daily public key
    */

    /**
     * Will fetch the latest daily public key from the API, update [.dailyPublicKeyData] and persist it in the preferences.
     *
     * This should be done on each app start, but not required for a successful initialization (e.g.
     * because the user may be offline).
     */
    fun updateDailyPublicKey(): Completable {
        return fetchDailyPublicKeyData()
            .doOnSuccess { dailyPublicKeyData = it }
            .flatMapCompletable(::persistDailyPublicKey)
            .doOnSubscribe { Timber.d("Updating daily public key") }
            .doOnComplete { Timber.d("Daily public key updated: %s", dailyPublicKeyData) }
            .doOnError { Timber.w(it, "Daily public key update failed: %s", it.toString()) }
    }

    /**
     * Get [DailyPublicKeyData], restore it if available or attempt fetching it
     * otherwise using [.updateDailyKeyPairPublicKey].
     */
    open fun getDailyPublicKey(): Single<DailyPublicKeyData> {
        return Maybe.fromCallable<DailyPublicKeyData> { dailyPublicKeyData }
            .switchIfEmpty(restoreDailyPublicKey()
                .doOnSuccess { dailyPublicKeyData = it }
                .switchIfEmpty(
                    updateDailyPublicKey()
                        .andThen(Single.fromCallable { dailyPublicKeyData!! })
                )
            ).onErrorResumeNext { Single.error(DailyKeyUnavailableException(it)) }
    }

    /**
     * Fetch [DailyPublicKeyData] from API, verify its authenticity and ensure
     * its less than 7 days old.
     */
    private fun fetchDailyPublicKeyData(): Single<DailyPublicKeyData> {
        return networkManager.lucaEndpointsV4
            .flatMap { it.dailyPublicKey }
            .map { it.dailyPublicKeyData }
            .flatMap { dailyPublicKeyData ->
                verifyDailyPublicKeyData(dailyPublicKeyData)
                    .andThen(Single.just(dailyPublicKeyData))
            }
    }

    fun getAndVerifyIssuerCertificate(issuerId: String): Single<KeyIssuerResponseData> {
        return networkManager
            .lucaEndpointsV4
            .flatMap { it.getKeyIssuer(issuerId) }
            .flatMap { keyIssuerResponseData ->
                Single.fromCallable {
                    // verify issuer certificate using luca intermediate and root CA
                    val issuerCertificate = keyIssuerResponseData.certificate
                    verifyKeyIssuerCertificate(issuerCertificate).blockingAwait()

                    // verify issuer signing key JWT signature using issuer certificate
                    val signingKeyData = keyIssuerResponseData.signingKeyData
                    signingKeyData.signedJwt!!.verifyJwt(issuerCertificate.publicKey)

                    // verify issuer encryption key JWT signature using issuer certificate
                    val encryptionKeyData = keyIssuerResponseData.encryptionKeyData
                    encryptionKeyData.signedJwt!!.verifyJwt(issuerCertificate.publicKey)

                    // verify signing key JWT subject matches issuer ID
                    require(signingKeyData.id == issuerId)

                    // verify encryption key JWT subject matches issuer ID
                    require(encryptionKeyData.id == issuerId)

                    keyIssuerResponseData
                }
            }
    }

    fun verifyDailyPublicKeyData(dailyPublicKeyData: DailyPublicKeyData): Completable {
        return assertKeyNotExpired(dailyPublicKeyData)
            .andThen(getAndVerifyIssuerCertificate(dailyPublicKeyData.issuerId))
            .flatMapCompletable { keyIssuerResponseData ->
                dailyPublicKeyData.signedJwt!!.verifyJwt(keyIssuerResponseData.signingKeyData.publicKey)
                persistDailyPublicKeyIssuerData(keyIssuerResponseData.signingKeyData)
            }
            .doOnError { Timber.w("Daily public key verification failed: %s", it.toString()) }
    }

    /**
     * Restore [DailyPublicKeyData] from preferences if available.
     */
    private fun restoreDailyPublicKey(): Maybe<DailyPublicKeyData> {
        return preferencesManager.restoreIfAvailable(DAILY_PUBLIC_KEY_DATA_KEY, DailyPublicKeyData::class.java)
            .flatMapSingle { assertKeyNotExpired(it).andThen(Single.just(it)) }
    }

    /**
     * Persist given [DailyPublicKeyData] to preferences.
     */
    private fun persistDailyPublicKey(dailyPublicKeyData: DailyPublicKeyData): Completable {
        return preferencesManager.persist(DAILY_PUBLIC_KEY_DATA_KEY, dailyPublicKeyData)
    }

    /**
     * Will complete if the specified key is not older than seven days
     * or emit a [DailyKeyExpiredException] otherwise.
     * Uses the [.genuinityManager] to make sure the system time is valid.
     */
    fun assertKeyNotExpired(dailyPublicKeyData: DailyPublicKeyData): Completable {
        return genuinityManager.assertIsGenuineTime()
            .andThen(Completable.fromAction {
                val keyAge = TimeUtil.getCurrentMillis() - dailyPublicKeyData.creationTimestamp
                if (keyAge > TimeUnit.DAYS.toMillis(7)) {
                    throw DailyKeyExpiredException("Daily public key is older than 7 days")
                }
            })
    }

    /*
        Daily public key issuer
     */

    fun verifyKeyIssuerCertificate(certificate: X509Certificate): Completable {
        return Completable.fromAction {
            if (!LucaApplication.IS_USING_STAGING_ENVIRONMENT) {
                // staging example:    C=DE,ST=Berlin,L=Berlin,O=luca Dev,CN=Dev Cluster Health Department,SERIALNUMBER=CSM026070939
                // production example: C=DE,ST=Berlin,L=Berlin,O=Bundesdruckerei GmbH,OU=LUCA,CN=1.12.0.60.luca,serialNumber=CSM025852123
                val x500name = certificate.getX500Name()

                // verify organizational unit
                val ouRdn = x500name.getRdnAsString(BCStyle.OU)
                require(ouRdn.equals("luca", true))

                // verify common name
                val cnRdn = x500name.getRdnAsString(BCStyle.CN)
                require(cnRdn.endsWith("luca", true))
            }

            // verify digital signature usage
            require(certificate.keyUsage[0])

            // verify certificate chain
            val namePrefix = if (LucaApplication.IS_USING_STAGING_ENVIRONMENT) "staging" else "production"
            val intermediateCertificate = CertificateUtil.loadCertificate(namePrefix + "_intermediate_ca.pem", application)
            val rootCertificate = CertificateUtil.loadCertificate(namePrefix + "_root_ca.pem", application)
            CertificateUtil.checkCertificateChain(
                rootCertificate,
                listOf(certificate, intermediateCertificate)
            )
        }
    }

    /**
     * Restore [KeyIssuerData] from preferences if available.
     */
    fun restoreDailyPublicKeyIssuerData(): Maybe<KeyIssuerData> {
        return preferencesManager.restoreIfAvailable(DAILY_PUBLIC_KEY_ISSUER_DATA_KEY, KeyIssuerData::class.java)
    }

    /**
     * Persist given [KeyIssuerData] to preferences.
     */
    private fun persistDailyPublicKeyIssuerData(keyIssuerData: KeyIssuerData): Completable {
        return preferencesManager.persist(DAILY_PUBLIC_KEY_ISSUER_DATA_KEY, keyIssuerData)
    }

    /*
        Guest data secret
     */

    /**
     * Get or generate data secret - a cryptographic seed which is used to derive both the data
     * encryption key and the data authentication key. This seed is encrypted twice before being
     * sent to the Luca Server during Check-In and ultimately protects the Guest’s Contact Data.
     *
     * @see [Security Overview: Secrets](https://www.luca-app.de/securityoverview/properties/secrets.html.term-data-secret)
     */
    fun getDataSecret(): Single<ByteArray> {
        return restoreDataSecret()
            .switchIfEmpty(generateDataSecret()
                .observeOn(Schedulers.io())
                .flatMap { persistDataSecret(it).andThen(Single.just(it)) })
    }

    private fun generateDataSecret(): Single<ByteArray> {
        return generateSecureRandomData(HashProvider.TRIMMED_HASH_LENGTH)
            .doOnSuccess { Timber.d("Generated new user data secret") }
    }

    private fun restoreDataSecret(): Maybe<ByteArray> {
        return restoreWrappedSecretIfAvailable(DATA_SECRET_KEY)
    }

    /**
     * Persist data secret to preferences, encrypted as a [WrappedSecret].
     */
    private fun persistDataSecret(secret: ByteArray): Completable {
        return persistWrappedSecret(DATA_SECRET_KEY, secret)
    }

    fun generateSecureRandomData(length: Int): Single<ByteArray> {
        return Single.fromCallable {
            val randomBytes = ByteArray(length)
            secureRandom.nextBytes(randomBytes)
            randomBytes
        }
    }

    /**
     * Delete data stored in the [.androidKeyStore] and [.bouncyCastleKeyStore].
     */
    fun deleteAllKeyStoreEntries(): Completable {
        return androidKeyStore.deleteAllEntries()
            .andThen(bouncyCastleKeyStore.deleteAllEntries())
    }

    companion object {

        private const val KEYSTORE_FILE_NAME = "keys.ks"
        private const val DAILY_PUBLIC_KEY_DATA_KEY = "daily_public_key_data"
        private const val DAILY_PUBLIC_KEY_ISSUER_DATA_KEY = "daily_public_key_issuer_data"
        private const val DATA_SECRET_KEY = "user_data_secret_2"
        private const val ALIAS_KEYSTORE_PASSWORD = "keystore_secret"
        private const val ALIAS_SECRET_WRAPPING_KEY_PAIR = "secret_wrapping_key_pair"
        private val DATA_ENCRYPTION_SECRET_SUFFIX = byteArrayOf(0x01)
        private val DATA_AUTHENTICATION_SECRET_SUFFIX = byteArrayOf(0x02)

        @Deprecated("Replaced by DAILY_PUBLIC_KEY_DATA_KEY")
        private const val DAILY_KEY_PAIR_PUBLIC_KEY_ID_KEY = "daily_key_pair_public_key_id"

        @Deprecated("Replaced by DAILY_PUBLIC_KEY_DATA_KEY")
        private const val DAILY_KEY_PAIR_PUBLIC_KEY_POINT_KEY = "daily_key_pair_public_key"

        @Deprecated("Replaced by DAILY_PUBLIC_KEY_DATA_KEY")
        private const val DAILY_KEY_PAIR_CREATION_TIMESTAMP_KEY = "daily_key_pair_creation_timestamp"

        /**
         * Substitute outdated Android-provided Bouncy Castle with bundled one.
         */
        @JvmStatic
        fun setupSecurityProviders(): Completable {
            return Completable.fromAction {
                val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
                if (provider !is BouncyCastleProvider) {
                    // Android registers its own BC provider. As it might be outdated and might not include
                    // all needed ciphers, we substitute it with a known BC bundled in the app.
                    // Android's BC has its package rewritten to "com.android.org.bouncycastle" and because
                    // of that it's possible to have another BC implementation loaded in VM.
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                    Security.addProvider(BouncyCastleProvider())
                    Timber.i("Inserted bouncy castle provider")
                }
            }
        }

        @Deprecated("Use extension function", ReplaceWith("ByteArray.toSecretKey()"))
        @JvmStatic
        fun createKeyFromSecret(secret: ByteArray): Single<SecretKey> {
            return Single.fromCallable { secret.toSecretKey() }
        }

        @Deprecated("Use extension function", ReplaceWith("UUID.toByteArray()"))
        @JvmStatic
        fun encode(uuid: UUID): Single<ByteArray> {
            return Single.fromCallable { uuid.toByteArray() }
        }

        @Deprecated("Use extension function", ReplaceWith("ByteArray.trim(length: Int)"))
        @JvmStatic
        fun trim(data: ByteArray, length: Int): Single<ByteArray> {
            return Single.fromCallable { data.trim(length) }
        }

        @JvmStatic
        fun concatenate(vararg dataArray: ByteArray?): Single<ByteArray> {
            return Single.fromCallable {
                val outputStream = ByteArrayOutputStream()
                for (data in dataArray) {
                    outputStream.write(data)
                }
                outputStream.toByteArray()
            }
        }

    }

}

fun UUID.toByteArray(): ByteArray {
    val byteBuffer = ByteBuffer.wrap(ByteArray(16))
    byteBuffer.putLong(this.mostSignificantBits)
    byteBuffer.putLong(this.leastSignificantBits)
    return byteBuffer.array()
}

fun ByteArray.trim(length: Int): ByteArray {
    val trimmedLength = kotlin.math.min(length, this.size)
    val trimmedData = ByteArray(trimmedLength)
    System.arraycopy(this, 0, trimmedData, 0, trimmedLength)
    return trimmedData
}

fun ByteArray.toCharArray(): CharArray {
    val chars = CharArray(this.size)
    for (i in this.indices) {
        chars[i] = this[i].toInt().toChar()
    }
    return chars
}

fun ByteArray.toSecretKey(): SecretKey {
    return SecretKeySpec(this, 0, this.size, "AES")
}

fun ECPublicKey.toByteArray(compressed: Boolean = false): ByteArray {
    return AsymmetricCipherProvider.encode(this, compressed).blockingGet()
}

fun ECPublicKey.toCompressedBase64String(): String {
    return this.toByteArray(true).encodeToBase64()
}