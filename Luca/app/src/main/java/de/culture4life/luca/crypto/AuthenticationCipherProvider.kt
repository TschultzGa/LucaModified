package de.culture4life.luca.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.nexenio.rxkeystore.RxKeyStore
import io.reactivex.rxjava3.core.Single
import java.security.spec.AlgorithmParameterSpec
import java.util.concurrent.TimeUnit

class AuthenticationCipherProvider(
    rxKeyStore: RxKeyStore,
    val authenticationTimeoutDuration: Long = AUTHENTICATION_TIMEOUT_NONE
) : AttestationCipherProvider(rxKeyStore) {

    init {
        useStrongBoxIfAvailable = true
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun getKeyAlgorithmParameterSpec(alias: String, context: Context): Single<AlgorithmParameterSpec> {
        // needs to be overwritten to add the authentication and attestation related properties
        return Single.fromCallable {
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setDefaultProperties()
                .setAttestationProperties(attestationChallenge)
                .setAuthenticationProperties(authenticationTimeoutDuration)
                .build()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun KeyGenParameterSpec.Builder.setAuthenticationProperties(timeoutDuration: Long): KeyGenParameterSpec.Builder {
        setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setInvalidatedByBiometricEnrollment(false)
            setIsStrongBoxBacked(shouldUseStrongBox())
        }
        val timeoutSeconds = TimeUnit.MILLISECONDS.toSeconds(timeoutDuration).toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            setUserAuthenticationParameters(
                timeoutSeconds,
                KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            // using 0 is a workaround to allow PIN fallback on older SDK versions
            setUserAuthenticationValidityDurationSeconds(timeoutSeconds)
        }
        return this
    }

    companion object {
        const val AUTHENTICATION_TIMEOUT_NONE = 0L
    }
}
