package com.lcdcode.moodcairns.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.security.BioCipher
import com.lcdcode.moodcairns.security.BiometricOffer
import com.lcdcode.moodcairns.security.LockManager
import com.lcdcode.moodcairns.security.PinUnlockResult
import com.lcdcode.moodcairns.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.inject.Inject

data class LockUiState(
    val pin: String = "",
    val error: UiText? = null,
    val attempts: Int = 0,
    /** True while an unlock attempt (PBKDF2 + DB open) is running off the UI thread. */
    val busy: Boolean = false,
    /** Remaining backoff in ms, or null when PIN entry is allowed. */
    val lockoutRemainingMs: Long? = null,
    /** A hardware-bound biometric enrollment exists and has not failed this session. */
    val biometricAvailable: Boolean = false,
    /** Android invalidated the biometric key (biometrics or screen lock changed). */
    val biometricReset: Boolean = false,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockManager: LockManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(LockUiState())
    val ui: StateFlow<LockUiState> = _ui.asStateFlow()

    private var countdownJob: Job? = null

    init {
        // Restore any in-progress lockout from a prior process death.
        val initial = lockManager.pinLockoutRemainingMs()
        if (initial > 0L) startCountdown(initial)
    }

    /**
     * Call each time the lock screen is shown. This ViewModel is scoped to the activity and
     * outlives every lock/unlock cycle, while the enrollment can change in between (Settings,
     * invalidation), so biometric availability must be re-read on every showing.
     */
    fun onShown() {
        _ui.update {
            it.copy(
                biometricAvailable = lockManager.canBiometricUnlock(),
                biometricReset = lockManager.biometricOffer.value == BiometricOffer.INVALIDATED,
            )
        }
    }

    fun onPinChanged(pin: String) {
        _ui.update { it.copy(pin = pin.filter(Char::isDigit).take(MAX_PIN_LEN), error = null) }
    }

    fun submit() {
        val pin = _ui.value.pin
        if (_ui.value.busy || _ui.value.lockoutRemainingMs != null) return
        if (pin.length < MIN_PIN_LEN) {
            _ui.update {
                it.copy(error = UiText.Plural(R.plurals.error_pin_too_short, MIN_PIN_LEN))
            }
            return
        }
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            // PIN unlock runs ~1.2M PBKDF2 rounds plus the SQLCipher DB open;
            // on the UI thread that freezes the app and ANR-crashes slow devices.
            // Dispatchers.Default keeps it off the main thread.
            val result = try {
                withContext(Dispatchers.Default) {
                    lockManager.tryUnlockWithPin(pin.toCharArray())
                }
            } catch (t: Throwable) {
                _ui.update { it.copy(busy = false, pin = "", error = unlockErrorMessage(t)) }
                return@launch
            }
            when (result) {
                PinUnlockResult.Success -> {
                    countdownJob?.cancel()
                    _ui.update { LockUiState() }
                }
                PinUnlockResult.WrongPin -> _ui.update {
                    it.copy(
                        busy = false,
                        pin = "",
                        error = UiText.Res(R.string.lock_error_wrong_pin),
                        attempts = it.attempts + 1,
                    )
                }
                is PinUnlockResult.RateLimited -> {
                    _ui.update {
                        it.copy(
                            busy = false,
                            pin = "",
                            error = UiText.Res(R.string.lock_error_too_many_attempts),
                            attempts = it.attempts + 1,
                        )
                    }
                    startCountdown(result.retryAfterMs)
                }
            }
        }
    }

    /**
     * The Keystore cipher for the biometric prompt, or null to stay on PIN entry. A dead or
     * missing enrollment is cleared by [LockManager.prepareBiometricUnlock], which also hides
     * the biometric button.
     */
    suspend fun prepareBiometricCipher(): Cipher? {
        val result = withContext(Dispatchers.Default) { lockManager.prepareBiometricUnlock() }
        if (result !is BioCipher.Ready) {
            _ui.update {
                it.copy(
                    biometricAvailable = lockManager.canBiometricUnlock(),
                    biometricReset = it.biometricReset || result == BioCipher.Invalidated,
                )
            }
        }
        return (result as? BioCipher.Ready)?.cipher
    }

    /**
     * Handle a successful biometric prompt: [cipher] is now authenticated and decrypts the DB
     * key. The DB open touches disk, so it runs off the UI thread. On failure the screen stays
     * on PIN entry.
     */
    fun onBiometricSuccess(cipher: Cipher) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.Default) { lockManager.unlockWithBiometric(cipher) }
            } catch (t: Throwable) {
                _ui.update { it.copy(busy = false, error = unlockErrorMessage(t)) }
                return@launch
            }
            if (ok) {
                countdownJob?.cancel()
                _ui.update { LockUiState() }
            } else {
                _ui.update {
                    it.copy(busy = false, biometricAvailable = lockManager.canBiometricUnlock())
                }
            }
        }
    }

    private fun unlockErrorMessage(t: Throwable): UiText =
        UiText.withDetail(R.string.lock_error_unlock_failed, t)

    private fun startCountdown(initialMs: Long) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = initialMs
            while (remaining > 0L) {
                _ui.update { it.copy(lockoutRemainingMs = remaining) }
                val step = remaining.coerceAtMost(1000L)
                delay(step)
                remaining -= step
            }
            _ui.update { it.copy(lockoutRemainingMs = null, error = null) }
        }
    }

    companion object {
        const val MIN_PIN_LEN = 4
        const val MAX_PIN_LEN = 10
    }
}
