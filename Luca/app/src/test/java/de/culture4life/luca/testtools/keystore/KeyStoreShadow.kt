package de.culture4life.luca.testtools.keystore

import com.nexenio.rxkeystore.RxKeyStore
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import java.security.Key
import java.security.KeyStore

@Implements(RxKeyStore::class)
class KeyStoreShadow {

    @RealObject
    private val realInstance: RxKeyStore? = null

    private val passwordProtection = KeyStore.PasswordProtection("jvm wants a password for private/secure entries or will fail".toCharArray())

    companion object {

        /**
         * Override AndroidKeyStore usage with Bouncy Castle.
         *
         * Robolectric does not provide AndroidKeyStore yet.
         */
        @Implementation
        @JvmStatic
        fun getInitializedKeyStore(type: String, provider: String?): Single<KeyStore> {
            return Shadow.directlyOn(
                RxKeyStore::class.java,
                "getInitializedKeyStore",
                ReflectionHelpers.ClassParameter.from(String::class.java, RxKeyStore.TYPE_BKS),
                ReflectionHelpers.ClassParameter.from(String::class.java, RxKeyStore.PROVIDER_BOUNCY_CASTLE)
            )
        }
    }

    /**
     * Override AndroidKeyStore usage with Bouncy Castle.
     *
     * Robolectric does not provide AndroidKeyStore yet.
     */
    @Implementation
    fun getProvider(): String {
        return RxKeyStore.PROVIDER_BOUNCY_CASTLE
    }

    /**
     * Ensure password is used to store KeyPairs.
     *
     * On real JVM it is not allowed to store PrivateKeyEntry without a password. But our RxKeyStore does it by default.
     */
    @Implementation
    fun setEntry(alias: String, entry: KeyStore.Entry, protectionParameter: KeyStore.ProtectionParameter?): Completable {
        val realInstanceDirect = Shadow.directlyOn(realInstance, RxKeyStore::class.java)
        return realInstanceDirect.setEntry(alias, entry, passwordProtection)
    }

    /**
     * Since all keys are stored with a password we have to access them with a password.
     */
    @Implementation
    fun getKeyIfAvailable(alias: String): Maybe<Key?> {
        return realInstance!!.initializedKeyStore
            .flatMapMaybe { keyStore: KeyStore ->
                Maybe.fromCallable { keyStore.getKey(alias, passwordProtection.password) }
            }
    }
}
