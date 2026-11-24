package com.lcdcode.moodcairns.ui.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import com.lcdcode.moodcairns.security.BioCipher
import com.lcdcode.moodcairns.security.BiometricOffer
import com.lcdcode.moodcairns.security.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.inject.Inject

/** Biometric enrollment and the pending offer, shared by the offer dialog and Settings. */
@HiltViewModel
class BiometricEnrollmentViewModel @Inject constructor(
    private val lockManager: LockManager,
) : ViewModel() {

    val offer: StateFlow<BiometricOffer?> = lockManager.biometricOffer

    fun isPinSet(): Boolean = lockManager.isPinSet()

    fun dismissOffer() = lockManager.dismissBiometricOffer()

    /** Keystore key generation can take a moment, so it stays off the main thread. */
    suspend fun prepareCipher(): Cipher? = withContext(Dispatchers.Default) {
        (lockManager.prepareBiometricEnrollment() as? BioCipher.Ready)?.cipher
    }

    suspend fun complete(cipher: Cipher): Boolean = withContext(Dispatchers.Default) {
        lockManager.completeBiometricEnrollment(cipher)
    }
}

/**
 * Runs the whole enrollment: new Keystore key, biometric prompt, then the encrypted DB key is
 * stored. Returns whether biometric unlock is now on. Call from a main-thread coroutine.
 */
suspend fun enrollBiometric(
    activity: FragmentActivity,
    viewModel: BiometricEnrollmentViewModel,
): Boolean {
    val cipher = viewModel.prepareCipher() ?: return false
    val authenticated = Biometrics.promptEnroll(activity, cipher) ?: return false
    return viewModel.complete(authenticated)
}
