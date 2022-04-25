package de.culture4life.luca.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.nexenio.rxkeystore.RxKeyStore
import de.culture4life.luca.util.encodeToHex
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import timber.log.Timber
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.Certificate
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec

open class AttestationCipherProvider(rxKeyStore: RxKeyStore) : AsymmetricCipherProvider(rxKeyStore) {

    var includeDeviceProperties = true
    protected var attestationChallenge: ByteArray? = null

    init {
        useStrongBoxIfAvailable = true
    }

    fun generateKeyPair(alias: String, attestationChallenge: ByteArray?, context: Context): Single<KeyPair> {
        return super.generateKeyPair(alias, context)
            .doOnSubscribe { this.attestationChallenge = attestationChallenge }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun getKeyAlgorithmParameterSpec(alias: String, context: Context): Single<AlgorithmParameterSpec> {
        // needs to be overwritten to add the attestation related properties
        return Single.fromCallable {
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setDefaultProperties()
                .setAttestationProperties(attestationChallenge)
                .build()
        }
    }

    override fun getKeyPairGeneratorInstance(): Single<KeyPairGenerator> {
        // needs to be overwritten to enforce using the AndroidKeyStore provider instead of the default
        return Single.fromCallable { KeyPairGenerator.getInstance(getKeyAlgorithm(), RxKeyStore.PROVIDER_ANDROID_KEY_STORE) }
    }

    /**
     * Emits the certificates associated with the hardware-backed keystore for the specified key.
     * The result should be verified by a trusted server.
     *
     * @see [Verifying hardware-backed key pairs with Key Attestation](https://developer.android.com/training/articles/security-key-attestation)
     */
    fun getCertificateChain(alias: String): Observable<Certificate> {
        // TODO: implement in RxKeyStore library
        return rxKeyStore.initializedKeyStore
            .flatMapMaybe {
                Maybe.fromCallable {
                    it.getCertificateChain(alias)
                }
            }
            .flatMapObservable { Observable.fromIterable(it.asIterable()) }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun KeyGenParameterSpec.Builder.setDefaultProperties(): KeyGenParameterSpec.Builder {
        return this.setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_NAME))
            .setBlockModes(*blockModes)
            .setEncryptionPaddings(*encryptionPaddings)
            .setSignaturePaddings(*signaturePaddings)
            .setDigests(*digests)
    }

    fun KeyGenParameterSpec.Builder.setAttestationProperties(challenge: ByteArray?): KeyGenParameterSpec.Builder {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && challenge != null) {
            Timber.v("Attestation challenge: ${challenge.encodeToHex()}")
            setAttestationChallenge(challenge)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val useStrongBox = shouldUseStrongBox()
            Timber.v("Strongbox backed: $useStrongBox")
            setIsStrongBoxBacked(useStrongBox)
        }
        return this
    }
}
