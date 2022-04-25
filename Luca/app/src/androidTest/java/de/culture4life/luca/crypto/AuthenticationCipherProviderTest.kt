package de.culture4life.luca.crypto

import com.nexenio.rxkeystore.RxKeyStore
import com.nexenio.rxkeystore.provider.cipher.RxEncryptionException
import de.culture4life.luca.LucaInstrumentationTest
import de.culture4life.luca.crypto.CryptoManager.Companion.setupSecurityProviders
import org.junit.Before
import org.junit.Test
import java.security.Signature
import java.security.SignatureException

class AuthenticationCipherProviderTest : LucaInstrumentationTest() {

    private lateinit var authenticationCipherProvider: AuthenticationCipherProvider

    @Before
    fun setup() {
        setupSecurityProviders().blockingAwait()
        val keyStore = RxKeyStore(RxKeyStore.TYPE_ANDROID, RxKeyStore.PROVIDER_ANDROID_KEY_STORE)
        authenticationCipherProvider = AuthenticationCipherProvider(keyStore)
    }

    @Test
    fun encrypt_purposeExcludesEncryption_emitsError() {
        authenticationCipherProvider.generateKeyPair("test", application)
            .flatMap { authenticationCipherProvider.encrypt("data".toByteArray(), it.private) }
            .test()
            .assertError(RxEncryptionException::class.java)
    }

    @Test
    fun sign_withoutAuthentication_emitsError() {
        authenticationCipherProvider.generateKeyPair("test", application)
            .map {
                val signature = Signature.getInstance(authenticationCipherProvider.signatureAlgorithm)
                signature.initSign(it.private)
                signature.update("data".toByteArray())
                signature.sign() // not authenticated!
            }
            .test()
            .assertError(SignatureException::class.java)
    }
}
