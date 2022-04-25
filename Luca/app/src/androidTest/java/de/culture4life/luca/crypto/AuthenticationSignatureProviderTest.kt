package de.culture4life.luca.crypto

import com.nexenio.rxkeystore.RxKeyStore
import com.nexenio.rxkeystore.provider.signature.RxSignatureException
import de.culture4life.luca.LucaInstrumentationTest
import de.culture4life.luca.util.decodeFromBase64
import de.culture4life.luca.util.encodeToHex
import org.junit.Before
import org.junit.Test
import java.security.PrivateKey

class AuthenticationSignatureProviderTest : LucaInstrumentationTest() {

    private lateinit var authenticationSignatureProvider: AuthenticationSignatureProvider
    private lateinit var privateKey: PrivateKey

    @Before
    fun setup() {
        CryptoManager.setupSecurityProviders().blockingAwait()
        val keyStore = RxKeyStore(RxKeyStore.TYPE_BKS, RxKeyStore.PROVIDER_BOUNCY_CASTLE)
        authenticationSignatureProvider = AuthenticationSignatureProvider(keyStore)
        privateKey = AsymmetricCipherProvider.decodePrivateKey(ENCODED_DUMMY_PRIVATE_KEY.decodeFromBase64()).blockingGet()
    }

    @Test
    fun sign_authenticationSuccess_emitsSignature() {
        val testProcessor = TestAuthenticationRequestProcessor()
        val testObserver = authenticationSignatureProvider.sign("data".toByteArray(), privateKey, testProcessor)
            .map(ByteArray::encodeToHex)
            .test()
            .assertNotComplete()

        testProcessor.givenAuthenticationSucceeded()

        testObserver.assertComplete()
    }

    @Test
    fun sign_authenticationFailed_emitsError() {
        val testProcessor = TestAuthenticationRequestProcessor()
        val testObserver = authenticationSignatureProvider.sign("data".toByteArray(), privateKey, testProcessor)
            .test()
            .assertNotComplete()

        testProcessor.givenAuthenticationFailed()

        testObserver.assertError(RxSignatureException::class.java)
    }

    class TestAuthenticationRequestProcessor : AuthenticationSignatureProvider.AuthenticationRequestProcessor {

        var authenticationRequest: AuthenticationSignatureProvider.AuthenticationRequest? = null

        override fun onAuthenticationRequest(authenticationRequest: AuthenticationSignatureProvider.AuthenticationRequest) {
            this.authenticationRequest = authenticationRequest
        }

        fun givenAuthenticationSucceeded() {
            val signature = authenticationRequest?.cryptoObject?.signature!!
            authenticationRequest?.authenticationCallback?.onAuthenticationSucceeded(signature)!!
        }

        fun givenAuthenticationFailed() {
            authenticationRequest?.authenticationCallback?.onAuthenticationFailed()!!
        }
    }

    companion object {
        private const val ENCODED_DUMMY_PRIVATE_KEY = "JwlHQ8w3GjM6T94PwgltA7PNvCk1xokk8HcqXG0CXBI="
    }
}
