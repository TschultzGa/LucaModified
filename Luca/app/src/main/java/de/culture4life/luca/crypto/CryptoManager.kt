package de.culture4life.luca.crypto

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Base64
import com.nexenio.rxkeystore.RxKeyStore
import com.nexenio.rxkeystore.util.RxBase64
import de.culture4life.luca.Manager
import de.culture4life.luca.genuinity.GenuinityManager
import de.culture4life.luca.network.NetworkManager
import de.culture4life.luca.network.pojo.DailyKeyPairIssuer
import de.culture4life.luca.preference.PreferencesManager
import de.culture4life.luca.util.SerializationUtil
import de.culture4life.luca.util.SingleUtil.retryWhen
import de.culture4life.luca.util.TimeUtil.convertFromUnixTimestamp
import de.culture4life.luca.util.TimeUtil.encodeUnixTimestamp
import io.reactivex.rxjava3.core.*
import io.reactivex.rxjava3.schedulers.Schedulers
import org.bouncycastle.asn1.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.*
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

    val androidKeyStore: RxKeyStore
    val bouncyCastleKeyStore: RxKeyStore

    val wrappingCipherProvider: WrappingCipherProvider
    val symmetricCipherProvider: SymmetricCipherProvider
    val asymmetricCipherProvider: AsymmetricCipherProvider
    val signatureProvider: SignatureProvider
    val macProvider: MacProvider
    val hashProvider: HashProvider
    val secureRandom: SecureRandom

    private val context: Context? = null
    private var dailyKeyPairPublicKeyWrapper: DailyKeyPairPublicKeyWrapper? = null

    init {
        androidKeyStore = RxKeyStore(RxKeyStore.TYPE_ANDROID, getAndroidKeyStoreProviderName())
        bouncyCastleKeyStore = RxKeyStore(RxKeyStore.TYPE_BKS, RxKeyStore.PROVIDER_BOUNCY_CASTLE)
        wrappingCipherProvider = WrappingCipherProvider(androidKeyStore)
        symmetricCipherProvider = SymmetricCipherProvider(bouncyCastleKeyStore)
        asymmetricCipherProvider = AsymmetricCipherProvider(bouncyCastleKeyStore)
        signatureProvider = SignatureProvider(bouncyCastleKeyStore)
        macProvider = MacProvider(bouncyCastleKeyStore)
        hashProvider = HashProvider(bouncyCastleKeyStore)
        secureRandom = SecureRandom()
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
            .flatMapCompletable { passwordChars ->
                Single.fromCallable { context.openFileOutput(KEYSTORE_FILE_NAME, Context.MODE_PRIVATE) }
                    .flatMapCompletable { outputStream ->
                        bouncyCastleKeyStore.save(outputStream, passwordChars)
                            .doFinally { outputStream.close() }
                    }
            }
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
        Daily key pair public key
    */

    /**
     * Will fetch the latest daily key pair public key from the API, update [ ][.dailyKeyPairPublicKeyWrapper] and persist it in the preferences.
     *
     *
     * This should be done on each app start, but not required for a successful initialization (e.g.
     * because the user may be offline).
     */
    fun updateDailyKeyPairPublicKey(): Completable {
        return fetchDailyKeyPairPublicKeyWrapperFromBackend()
            .doOnSuccess { dailyKeyPairPublicKeyWrapper = it }
            .flatMapCompletable(::persistDailyKeyPairPublicKeyWrapper)
            .doOnSubscribe { Timber.d("Updating daily key pair public key") }
    }

    /**
     * Get [DailyKeyPairPublicKeyWrapper], restore it if available, attempt fetching it from
     * server otherwise using [.updateDailyKeyPairPublicKey].
     */
    open fun getDailyKeyPairPublicKeyWrapper(): Single<DailyKeyPairPublicKeyWrapper> {
        return Maybe.fromCallable<DailyKeyPairPublicKeyWrapper> { dailyKeyPairPublicKeyWrapper }
            .switchIfEmpty(restoreDailyKeyPairPublicKeyWrapper()
                .doOnSuccess { dailyKeyPairPublicKeyWrapper = it }
                .switchIfEmpty(
                    updateDailyKeyPairPublicKey()
                        .andThen(Single.fromCallable { dailyKeyPairPublicKeyWrapper!! })
                )
            ).onErrorResumeNext { Single.error(DailyKeyUnavailableException(it)) }
    }

    fun getDailyKeyPair(): Single<DailyKeyPairIssuer> {
        return networkManager.lucaEndpointsV3.flatMap { lucaEndpointsV3 ->
            lucaEndpointsV3.dailyKeyPair.flatMap { dailyKeyPair ->
                lucaEndpointsV3.getIssuer(dailyKeyPair.issuerId)
                    .map { issuer -> DailyKeyPairIssuer(dailyKeyPair, issuer) }
            }
        }
    }


    fun verifyDailyKeyPair(dailyKeyPairIssuer: DailyKeyPairIssuer): Completable {
        return Completable.defer {
            val issuerSigningKey = dailyKeyPairIssuer.issuer.publicHDSKPToPublicKey()
            val signedData = concatenate(
                dailyKeyPairIssuer.dailyKeyPair.getEncodedKeyId(),
                dailyKeyPairIssuer.dailyKeyPair.getEncodedCreatedAt(),
                dailyKeyPairIssuer.dailyKeyPair.publicKey.decodeFromBase64()
            ).blockingGet()
            val signature = dailyKeyPairIssuer.dailyKeyPair.signature.decodeFromBase64()
            signatureProvider.verify(signedData, signature, issuerSigningKey)
        }
    }

    /**
     * Fetch [DailyKeyPairPublicKeyWrapper] from server, verify its authenticity and ensure
     * its less than 7 days old.
     */
    private fun fetchDailyKeyPairPublicKeyWrapperFromBackend(): Single<DailyKeyPairPublicKeyWrapper> {
        return getDailyKeyPair()
            .flatMap { (dailyKeyPair, issuer) ->
                Single.fromCallable {
                    val encodedPublicKey = dailyKeyPair.publicKey.decodeFromBase64()
                    val publicKey = AsymmetricCipherProvider.decodePublicKey(encodedPublicKey).blockingGet()
                    val creationUnixTimestamp = dailyKeyPair.createdAt
                    val encodedCreationTimestamp = encodeUnixTimestamp(creationUnixTimestamp).blockingGet()
                    val id = dailyKeyPair.keyId
                    val encodedId = ByteBuffer.allocate(4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(id)
                        .array()
                    val issuerSigningKey = issuer.publicHDSKPToPublicKey()
                    val signature = dailyKeyPair.signature.decodeFromBase64()
                    val signedData = concatenate(encodedId, encodedCreationTimestamp, encodedPublicKey).blockingGet()
                    signatureProvider.verify(signedData, signature, issuerSigningKey).blockingAwait()
                    val creationTimestamp = convertFromUnixTimestamp(creationUnixTimestamp).blockingGet()
                    DailyKeyPairPublicKeyWrapper(id, publicKey, creationTimestamp)
                }
            }
            .flatMap { assertKeyNotExpired(it).andThen(Single.just(it)) }
            .doOnSuccess { Timber.d("Fetched daily key pair public key from backend: %s", it) }
    }

    /**
     * Restore [DailyKeyPairPublicKeyWrapper] if available.
     */
    private fun restoreDailyKeyPairPublicKeyWrapper(): Maybe<DailyKeyPairPublicKeyWrapper> {
        val restoreId = preferencesManager.restoreIfAvailable(DAILY_KEY_PAIR_PUBLIC_KEY_ID_KEY, Int::class.java)
        val restoreKey = preferencesManager.restoreIfAvailable(DAILY_KEY_PAIR_PUBLIC_KEY_POINT_KEY, String::class.java)
            .map(String::decodeFromBase64)
            .flatMapSingle(AsymmetricCipherProvider::decodePublicKey)
        val restoreCreationTimestamp = preferencesManager.restoreIfAvailable(DAILY_KEY_PAIR_CREATION_TIMESTAMP_KEY, Long::class.java)
        return Maybe.zip(restoreId, restoreKey, restoreCreationTimestamp, ::DailyKeyPairPublicKeyWrapper)
            .flatMapSingle { restoredKey: DailyKeyPairPublicKeyWrapper -> assertKeyNotExpired(restoredKey).andThen(Single.just(restoredKey)) }
            .doOnError { Timber.w("Unable to restore daily key pair: %s", it.toString()) }
            .onErrorComplete()
    }

    /**
     * Persist given daily public key wrapper to preferences.
     *
     * @param wrapper [DailyKeyPairPublicKeyWrapper]
     */
    fun persistDailyKeyPairPublicKeyWrapper(wrapper: DailyKeyPairPublicKeyWrapper): Completable {
        val persistId = preferencesManager.persist(DAILY_KEY_PAIR_PUBLIC_KEY_ID_KEY, wrapper.id)
        val persistKey = AsymmetricCipherProvider.encode(wrapper.publicKey, false)
            .map(ByteArray::encodeToBase64)
            .flatMapCompletable { preferencesManager.persist(DAILY_KEY_PAIR_PUBLIC_KEY_POINT_KEY, it) }
        val persistCreationTimestamp = preferencesManager.persist(DAILY_KEY_PAIR_CREATION_TIMESTAMP_KEY, wrapper.creationTimestamp)
        return Completable.mergeArray(persistId, persistKey, persistCreationTimestamp)
    }

    /**
     * Will complete if the specified key is not older than seven days
     * or emit a [DailyKeyExpiredException] otherwise.
     * Uses the [.genuinityManager] to make sure the system time is valid.
     */
    fun assertKeyNotExpired(wrapper: DailyKeyPairPublicKeyWrapper): Completable {
        return genuinityManager.assertIsGenuineTime()
            .andThen(Completable.fromAction {
                val keyAge = System.currentTimeMillis() - wrapper.creationTimestamp
                if (keyAge > TimeUnit.DAYS.toMillis(7)) {
                    throw DailyKeyExpiredException("Daily key pair public key is older than 7 days")
                }
            })
    }

    /*
        Guest key pair
     */

    /**
     * Get guest key pair or create if not yet generated. The keypair’s private key is used to sign
     * the encrypted guest data and guest data transfer object. The public key is uploaded to the
     * luca Server.
     */
    open fun getGuestKeyPair(): Single<KeyPair> {
        return restoreGuestKeyPair()
            .switchIfEmpty(generateGuestKeyPair()
                .observeOn(Schedulers.io())
                .flatMap { persistGuestKeyPair(it).andThen(Single.just(it)) })
    }

    fun getGuestKeyPairPrivateKey(): Single<ECPrivateKey> {
        return getGuestKeyPair().map { it.private }
            .cast(ECPrivateKey::class.java)
    }

    fun getGuestKeyPairPublicKey(): Single<ECPublicKey> {
        return getGuestKeyPair().map { it.public }
            .cast(ECPublicKey::class.java)
    }

    /**
     * Generate guest key pair, used to sign encrypted [MeetingGuestData].
     *
     * @see [Security
     * Overview: Secrets](https://luca-app.de/securityoverview/properties/secrets.html.term-guest-keypair)
     */
    private fun generateGuestKeyPair(): Single<KeyPair> {
        return asymmetricCipherProvider.generateKeyPair(ALIAS_GUEST_KEY_PAIR, context)
            .doOnSuccess { Timber.d("Generated new guest key pair: %s", it.public) }
    }

    /**
     * Restore Guest key pair from [.bouncyCastleKeyStore] if available.
     */
    private fun restoreGuestKeyPair(): Maybe<KeyPair> {
        return asymmetricCipherProvider.getKeyPairIfAvailable(ALIAS_GUEST_KEY_PAIR)
    }

    /**
     * Persist given guest keypair to [.bouncyCastleKeyStore].
     */
    private fun persistGuestKeyPair(keyPair: KeyPair): Completable {
        return asymmetricCipherProvider.setKeyPair(ALIAS_GUEST_KEY_PAIR, keyPair)
            .andThen(persistKeyStoreToFile())
    }

    /*
        Guest ephemeral key pair
     */

    /**
     * Get or generate guest ephemeral keypair used to encrypt user ID and secret during generation
     * of QR-codes.
     *
     * @see CheckInViewModel.generateQrCodeData
     */
    fun getGuestEphemeralKeyPair(traceId: ByteArray): Single<KeyPair> {
        return restoreGuestEphemeralKeyPair(traceId)
            .switchIfEmpty(generateGuestEphemeralKeyPair(traceId)
                .observeOn(Schedulers.io())
                .flatMap { persistGuestEphemeralKeyPair(traceId, it).andThen(Single.just(it)) })
    }

    /**
     * Generate keypair of given traceId.
     */
    private fun generateGuestEphemeralKeyPair(traceId: ByteArray): Single<KeyPair> {
        return getGuestEphemeralKeyPairAlias(traceId)
            .flatMap { asymmetricCipherProvider.generateKeyPair(it, context) }
            .doOnSuccess {
                Timber.d(
                    "Generated new user ephemeral key pair for trace ID %s: %s",
                    SerializationUtil.serializeToBase64(traceId).blockingGet(),
                    it.public
                )
            }
    }

    private fun restoreGuestEphemeralKeyPair(traceId: ByteArray): Maybe<KeyPair> {
        return getGuestEphemeralKeyPairAlias(traceId)
            .flatMapMaybe { asymmetricCipherProvider.getKeyPairIfAvailable(it) }
    }

    /**
     * Persist given keypair to BC keystore.
     */
    private fun persistGuestEphemeralKeyPair(traceId: ByteArray, keyPair: KeyPair): Completable {
        return getGuestEphemeralKeyPairAlias(traceId)
            .flatMapCompletable { asymmetricCipherProvider.setKeyPair(it, keyPair) }
            .andThen(persistKeyStoreToFile())
    }

    fun deleteGuestEphemeralKeyPair(traceId: ByteArray): Completable {
        return getGuestEphemeralKeyPairAlias(traceId)
            .flatMapCompletable { bouncyCastleKeyStore.deleteEntry(it) }
    }

    private fun getGuestEphemeralKeyPairAlias(traceId: ByteArray): Single<String> {
        return SerializationUtil.serializeToBase64(traceId)
            .map { "$ALIAS_GUEST_EPHEMERAL_KEY_PAIR-$it" }
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

    /*
        Shared Diffie-Hellman secret
     */

    /**
     * Compute shared DH secret of [DailyKeyPairPublicKeyWrapper] and the guest private key.
     */
    fun generateSharedDiffieHellmanSecret(): Single<ByteArray> {
        return getGuestKeyPairPrivateKey()
            .flatMap(this::generateSharedDiffieHellmanSecret)
    }

    /**
     * Compute shared DH secret of [DailyKeyPairPublicKeyWrapper] and a given private key.
     */
    fun generateSharedDiffieHellmanSecret(privateKey: PrivateKey): Single<ByteArray> {
        return getDailyKeyPairPublicKeyWrapper()
            .map { it.publicKey }
            .flatMap { asymmetricCipherProvider.generateSecret(privateKey, it) }
            .doOnSuccess { Timber.d("Generated shared Diffie Hellman secret") }
    }

    /*
        Encryption secret
     */

    /**
     * Generate data encryption secret by appending [.DATA_ENCRYPTION_SECRET_SUFFIX] to given
     * baseSecret, hashing it and trimming it to a length of 16.
     */
    fun generateDataEncryptionSecret(baseSecret: ByteArray): Single<ByteArray> {
        return concatenate(baseSecret, DATA_ENCRYPTION_SECRET_SUFFIX)
            .flatMap(hashProvider::hash)
            .flatMap { trim(it, HashProvider.TRIMMED_HASH_LENGTH) }
    }

    /*
        Authentication secret
     */

    /**
     * Generate data authentication secret by appending [.DATA_AUTHENTICATION_SECRET_SUFFIX]
     * to given baseSecret and hash it.
     */
    fun generateDataAuthenticationSecret(baseSecret: ByteArray): Single<ByteArray> {
        return concatenate(baseSecret, DATA_AUTHENTICATION_SECRET_SUFFIX)
            .flatMap(hashProvider::hash)
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

        @Deprecated("Use extension function", ReplaceWith("ByteArray.encodeToBase64()"))
        @JvmStatic
        fun encodeToString(data: ByteArray): Single<String> {
            return Single.fromCallable { data.encodeToBase64() }
        }

        @Deprecated("Use extension function", ReplaceWith("String.decodeFromBase64()"))
        @JvmStatic
        fun decodeFromString(data: String): Single<ByteArray> {
            return Single.fromCallable { data.decodeFromBase64() }
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

private const val KEYSTORE_FILE_NAME = "keys.ks"
private const val DAILY_KEY_PAIR_PUBLIC_KEY_ID_KEY = "daily_key_pair_public_key_id"
private const val DAILY_KEY_PAIR_PUBLIC_KEY_POINT_KEY = "daily_key_pair_public_key"
private const val DAILY_KEY_PAIR_CREATION_TIMESTAMP_KEY = "daily_key_pair_creation_timestamp"
private const val DATA_SECRET_KEY = "user_data_secret_2"
private const val ALIAS_GUEST_KEY_PAIR = "user_master_key_pair"
private const val ALIAS_GUEST_EPHEMERAL_KEY_PAIR = "user_ephemeral_key_pair"
private const val ALIAS_KEYSTORE_PASSWORD = "keystore_secret"
private const val ALIAS_SECRET_WRAPPING_KEY_PAIR = "secret_wrapping_key_pair"
private val DATA_ENCRYPTION_SECRET_SUFFIX = byteArrayOf(0x01)
private val DATA_AUTHENTICATION_SECRET_SUFFIX = byteArrayOf(0x02)

fun UUID.toByteArray(): ByteArray {
    val byteBuffer = ByteBuffer.wrap(ByteArray(16))
    byteBuffer.putLong(this.mostSignificantBits)
    byteBuffer.putLong(this.leastSignificantBits)
    return byteBuffer.array()
}

fun String.decodeFromBase64(flags: Int = Base64.NO_WRAP): ByteArray {
    return RxBase64.decode(this, flags).blockingGet()
}

fun ByteArray.encodeToBase64(flags: Int = Base64.NO_WRAP): String {
    return RxBase64.encode(this, flags).blockingGet()
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
