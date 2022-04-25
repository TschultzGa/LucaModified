package de.culture4life.luca.ui.idnow

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.culture4life.luca.BuildConfig
import de.culture4life.luca.R
import de.culture4life.luca.ui.dialog.BaseDialogFragment
import timber.log.Timber
import java.security.Signature

class UserAuthenticationRequiredPrompt(private val fragment: Fragment) {

    private val allowedAuthenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL or BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun showForLucaIdEnrollment(onSuccess: () -> Unit, onError: () -> Unit = {}) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragment.getString(R.string.authentication_title))
            .setSubtitle(fragment.getString(R.string.authentication_luca_id_enrollment_subtitle))
            .setDescription(fragment.getString(R.string.authentication_luca_id_enrollment_description))
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        showPrompt(Setup(onSuccess, onError, promptInfo))
    }

    fun showForLucaIdDisplay(onSuccess: () -> Unit, onError: () -> Unit = {}) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragment.getString(R.string.authentication_title))
            .setSubtitle(fragment.getString(R.string.authentication_luca_id_ident_subtitle))
            .setDescription(fragment.getString(R.string.authentication_luca_id_ident_description))
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        showPrompt(Setup(onSuccess, onError, promptInfo))
    }

    fun showForLucaIdIdentification(onSuccess: () -> Unit, onError: () -> Unit = {}, signature: Signature) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(fragment.getString(R.string.authentication_title))
            .setSubtitle(fragment.getString(R.string.authentication_luca_id_ident_subtitle)) // TODO: update texts
            .setDescription(fragment.getString(R.string.authentication_luca_id_ident_description))
            .setAllowedAuthenticators(allowedAuthenticators)
            .build()

        val cryptoObject = BiometricPrompt.CryptoObject(signature)

        showPrompt(Setup(onSuccess, onError, promptInfo, cryptoObject))
    }

    private fun showPrompt(setup: Setup) {
        if (isAuthenticationActivated()) {
            showAuthentication(setup)
        } else {
            showAuthenticationNotActivated(setup)
        }
    }

    private fun isAuthenticationActivated(): Boolean {
        return when (BiometricManager.from(fragment.requireContext()).canAuthenticate(allowedAuthenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> true // docs say "possible fine" you should try
            else -> false
        }
    }

    private fun showAuthentication(setup: Setup) {
        val onErrorWrapper: (errorString: CharSequence) -> Unit = { errorString ->
            if (BuildConfig.DEBUG) {
                // Enable to proceed with testing in environments which don't have screen lock configured.
                // Very handy for emulators, test devices and automated tests.
                skipAuthenticationErrorsOptionForTesting(setup, errorString)
            } else {
                setup.onError()
            }
        }

        with(setup) {
            val biometricPrompt = BiometricPrompt(fragment, AuthenticationCallback(onSuccess, onErrorWrapper))
            if (cryptoObject != null) {
                biometricPrompt.authenticate(promptInfo, cryptoObject)
            } else {
                biometricPrompt.authenticate(promptInfo)
            }
        }
    }

    private fun showAuthenticationNotActivated(setup: Setup) {
        val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.authentication_error_not_activated_title)
            .setMessage(R.string.authentication_error_not_activated_description)
            .setPositiveButton(R.string.action_ok) { _, _ -> setup.onError() }

        if (BuildConfig.DEBUG) {
            // Enable to proceed with testing in environments which don't have screen lock configured.
            // Very handy for emulators, test devices and automated tests.
            dialog.setNeutralButton("Debug Tool: continue") { _, _ -> showAuthentication(setup) }
        }

        BaseDialogFragment(dialog).show()
    }

    /**
     * Show the given authentication error from Android system and gives you the option to ignore it.
     *
     * Only for testing purposes.
     */
    private fun skipAuthenticationErrorsOptionForTesting(setup: Setup, errorString: CharSequence) {
        BaseDialogFragment(
            MaterialAlertDialogBuilder(fragment.requireContext())
                .setTitle("Debug Tool (not in production)")
                .setMessage("${setup.promptInfo.title}\n\n${setup.promptInfo.subtitle}\n\n${setup.promptInfo.description}\n\n>> $errorString <<")
                .setPositiveButton("ignore error") { _, _ -> setup.onSuccess() }
                .setNegativeButton("accept error") { _, _ -> setup.onError() }
        ).show()
    }

    class AuthenticationCallback(val onSuccess: () -> Unit, val onError: (errorString: CharSequence) -> Unit) :
        BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            Timber.d("user authentication succeeded")
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
            Timber.d("user authentication error, code: $errorCode, message: $errorString")
            onError(errorString)
        }
    }

    class Setup(
        val onSuccess: () -> Unit,
        val onError: () -> Unit,
        val promptInfo: BiometricPrompt.PromptInfo,
        val cryptoObject: BiometricPrompt.CryptoObject? = null
    )
}
