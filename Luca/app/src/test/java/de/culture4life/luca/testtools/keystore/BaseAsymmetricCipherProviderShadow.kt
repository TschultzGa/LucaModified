package de.culture4life.luca.testtools.keystore

import android.content.Context
import com.nexenio.rxkeystore.provider.cipher.asymmetric.BaseAsymmetricCipherProvider
import io.reactivex.rxjava3.core.Single
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import java.security.KeyPair

@Implements(BaseAsymmetricCipherProvider::class)
open class BaseAsymmetricCipherProviderShadow {

    @RealObject
    private val realInstance: BaseAsymmetricCipherProvider? = null

    /**
     * Android would automatically store the KeyPair into KeyStore, but our BC replacement does not.
     */
    @Implementation
    fun generateKeyPair(alias: String, context: Context): Single<KeyPair> {
        val realInstanceDirect = Shadow.directlyOn(realInstance, BaseAsymmetricCipherProvider::class.java)
        return realInstanceDirect.generateKeyPair(alias, context)
            .flatMap {
                realInstance!!.setKeyPair(alias, it)
                    .andThen(Single.just(it))
            }
    }
}
