package de.culture4life.luca.crypto

import androidx.biometric.BiometricPrompt
import com.nexenio.rxkeystore.RxKeyStore
import com.nexenio.rxkeystore.provider.signature.RxSignatureException
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.security.PrivateKey
import java.security.Signature
import java.util.concurrent.TimeUnit

/**
 * Uses biometric prompts to authenticate signing requests.
 *
 * TODO: implement in RxKeyStore library
 */
class AuthenticationSignatureProvider(rxKeyStore: RxKeyStore) : SignatureProvider(rxKeyStore) {

    /**
     * The default listener for authentication requests. Must be set prior to subscribing to [sign]
     * without specifying an [AuthenticationRequestProcessor].
     */
    var authenticationRequestProcessor: AuthenticationRequestProcessor? = null

    override fun sign(data: ByteArray, privateKey: PrivateKey): Single<ByteArray> {
        return Single.fromCallable { authenticationRequestProcessor!! }
            .flatMap { sign(data, privateKey, it) }
    }

    fun sign(data: ByteArray, privateKey: PrivateKey, authenticationRequestProcessor: AuthenticationRequestProcessor): Single<ByteArray> {
        return prepareSignatureInstanceForAuthentication(data, privateKey)
            .flatMap { authenticateSignatureInstance(authenticationRequestProcessor, it) }
            .flatMap(::getSignatureFromAuthenticatedSignatureInstance)
            .onErrorResumeNext { Single.error(RxSignatureException("Unable to create signature", it)) }
    }

    /**
     * Step 1: Initializes the signature instance with the key and data required for signature generation.
     */
    private fun prepareSignatureInstanceForAuthentication(data: ByteArray, privateKey: PrivateKey): Single<Signature> {
        return signatureInstance
            .doOnSuccess {
                it.initSign(privateKey)
                it.update(data)
            }
    }

    /**
     * Step 2: Requests a biometric authentication prompt, authorizing the signing key to be used for the current operation.
     */
    private fun authenticateSignatureInstance(
        authenticationRequestProcessor: AuthenticationRequestProcessor,
        signatureInstance: Signature
    ): Single<Signature> {
        return Single.create<Signature> { emitter ->
            val authenticationRequest = AuthenticationRequest(
                cryptoObject = BiometricPrompt.CryptoObject(signatureInstance),
                authenticationCallback = AuthenticationSignatureCallback(emitter::onSuccess, emitter::tryOnError)
            )
            authenticationRequestProcessor.onAuthenticationRequest(authenticationRequest)
        }.timeout(AUTHENTICATION_REQUEST_TIMEOUT, TimeUnit.MILLISECONDS, Schedulers.io())
    }

    /**
     * Step 3: Performs the actual signature generation after a successful authentication.
     */
    private fun getSignatureFromAuthenticatedSignatureInstance(signatureInstance: Signature): Single<ByteArray> {
        return Single.fromCallable { signatureInstance.sign() }
    }

    class AuthenticationSignatureCallback(
        val onSuccess: (signature: Signature) -> Unit,
        val onError: (throwable: Throwable) -> Unit
    ) : BiometricPrompt.AuthenticationCallback() {

        fun onAuthenticationSucceeded(signature: Signature) {
            onSuccess(signature)
        }

        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Timber.d("Authentication succeeded using authentication type ${result.authenticationType}")
            val signature = result.cryptoObject?.signature
            if (signature != null) {
                onAuthenticationSucceeded(signature)
            } else {
                onError(IllegalStateException("Signature crypto object not available"))
            }
        }

        override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
            Timber.d("Authentication error. Code: $errorCode, message: $errorString")
            onError(AuthenticationException(errorCode, errorString))
        }

        override fun onAuthenticationFailed() {
            Timber.d("Authentication failed")
            onError(AuthenticationException())
        }
    }

    /**
     * Wraps errors from [BiometricPrompt.AuthenticationResult]. Error codes can be found in [BiometricPrompt].
     */
    class AuthenticationException : Exception {
        constructor() : super("Authentication failed")
        constructor(errorCode: Int, errorString: CharSequence) : super("Authentication error. Code: $errorCode, message: $errorString")
    }

    data class AuthenticationRequest(
        val cryptoObject: BiometricPrompt.CryptoObject,
        val authenticationCallback: AuthenticationSignatureCallback
    )

    fun interface AuthenticationRequestProcessor {
        fun onAuthenticationRequest(authenticationRequest: AuthenticationRequest)
    }

    companion object {
        val AUTHENTICATION_REQUEST_TIMEOUT = TimeUnit.MINUTES.toMillis(1)
    }
}
