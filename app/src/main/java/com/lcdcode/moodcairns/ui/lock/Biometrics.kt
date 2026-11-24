package com.lcdcode.moodcairns.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.lcdcode.moodcairns.R
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

object Biometrics {

    // Only Class 3 (STRONG) biometrics. Some OEM face-unlock implementations
    // shipped as Class 2 (WEAK) have been bypassable with a photograph; refusing
    // WEAK is worth the loss of compatibility on a personal-mood-data app. A
    // CryptoObject also requires STRONG.
    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Unlock prompt bound to [cipher]: the Keystore only lets the cipher run after this
     * authentication, so a successful callback is what actually unlocks the DB key.
     */
    fun promptUnlock(
        activity: FragmentActivity,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFailure: (String) -> Unit,
        onUsePin: () -> Unit,
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.biometric_prompt_title))
            .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(activity.getString(R.string.biometric_prompt_use_pin))
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        authenticate(activity, info, cipher, onSuccess, onFailure, onCancel = onUsePin)
    }

    /**
     * Enrollment prompt bound to an encrypt [cipher]. Resumes with the authenticated cipher, or
     * null if the user cancelled or authentication failed for good. Call on the main thread.
     */
    suspend fun promptEnroll(activity: FragmentActivity, cipher: Cipher): Cipher? =
        suspendCancellableCoroutine { continuation ->
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(R.string.biometric_enroll_prompt_title))
                .setSubtitle(activity.getString(R.string.biometric_enroll_prompt_subtitle))
                .setNegativeButtonText(activity.getString(R.string.common_cancel))
                .setAllowedAuthenticators(AUTHENTICATORS)
                .build()
            val finish: (Cipher?) -> Unit = { if (continuation.isActive) continuation.resume(it) }
            authenticate(
                activity,
                info,
                cipher,
                onSuccess = finish,
                onFailure = { finish(null) },
                onCancel = { finish(null) },
            )
        }

    private fun authenticate(
        activity: FragmentActivity,
        info: BiometricPrompt.PromptInfo,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onFailure: (String) -> Unit,
        onCancel: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // Without the authenticated cipher there is nothing to unlock with.
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated != null) onSuccess(authenticated) else onCancel()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED -> onCancel()
                        else -> onFailure(errString.toString())
                    }
                }
            },
        )
        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
