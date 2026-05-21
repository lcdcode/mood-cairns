package com.lcdcode.moodcairns.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object Biometrics {

    // Only Class 3 (STRONG) biometrics. Some OEM face-unlock implementations
    // shipped as Class 2 (WEAK) have been bypassable with a photograph; refusing
    // WEAK is worth the loss of compatibility on a personal-mood-data app.
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
        onUsePin: () -> Unit,
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Mood Cairns")
            .setSubtitle("Authenticate to continue")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> onUsePin()
                        else -> onFailure(errString.toString())
                    }
                }
            },
        )
        prompt.authenticate(info)
    }
}
