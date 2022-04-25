package de.culture4life.luca.testtools.keystore

import android.content.Context
import com.nexenio.rxkeystore.RxKeyStore
import de.culture4life.luca.crypto.AttestationCipherProvider
import io.reactivex.rxjava3.core.Single
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import java.security.KeyPairGenerator
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECGenParameterSpec

@Implements(AttestationCipherProvider::class)
class AttestationCipherProviderShadow : BaseAsymmetricCipherProviderShadow() {

    @RealObject
    private val realInstance: AttestationCipherProvider? = null

    /**
     * Override AndroidKeyStore usage with Bouncy Castle.
     *
     * Robolectric does not provide AndroidKeyStore yet.
     */
    @Implementation
    fun getKeyPairGeneratorInstance(): Single<KeyPairGenerator> {
        return Single.defer { Single.just(KeyPairGenerator.getInstance(realInstance!!.keyAlgorithm, RxKeyStore.PROVIDER_BOUNCY_CASTLE)) }
    }

    /**
     * Otherwise it would produce incompatible ParameterSpec.
     */
    @Implementation
    fun getKeyAlgorithmParameterSpec(alias: String, context: Context): Single<AlgorithmParameterSpec> {
        return Single.just(ECGenParameterSpec("secp256r1"))
    }
}
