package com.lcdcode.moodcairns.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.security.LockManager
import com.lcdcode.moodcairns.security.PinUnlockResult
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
import javax.inject.Inject

data class LockUiState(
    val pin: String = "",
    val error: String? = null,
    val attempts: Int = 0,
    /** True while an unlock attempt (PBKDF2 + DB open) is running off the UI thread. */
    val busy: Boolean = false,
    /** Remaining backoff in ms, or null when PIN entry is allowed. */
    val lockoutRemainingMs: Long? = null,
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

    fun onPinChanged(pin: String) {
        _ui.update { it.copy(pin = pin.filter(Char::isDigit).take(MAX_PIN_LEN), error = null) }
    }

    fun submit() {
        val pin = _ui.value.pin
        if (_ui.value.busy || _ui.value.lockoutRemainingMs != null) return
        if (pin.length < MIN_PIN_LEN) {
            _ui.update { it.copy(error = "PIN must be at least $MIN_PIN_LEN digits") }
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
                    it.copy(busy = false, pin = "", error = "Incorrect PIN", attempts = it.attempts + 1)
                }
                is PinUnlockResult.RateLimited -> {
                    _ui.update {
                        it.copy(
                            busy = false,
                            pin = "",
                            error = "Too many attempts",
                            attempts = it.attempts + 1,
                        )
                    }
                    startCountdown(result.retryAfterMs)
                }
            }
        }
    }

    fun canBiometricUnlock(): Boolean = lockManager.canBiometricUnlock()

    /**
     * Handle a successful biometric prompt. The DB open still touches disk, so
     * it runs off the UI thread. If no biometric DB key is stored yet (e.g.
     * upgrading from a build that pre-dates DB encryption) the LockScreen simply
     * stays put and the user falls through to PIN entry.
     */
    fun onBiometricSuccess() {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val ok = try {
                withContext(Dispatchers.Default) { lockManager.unlockViaBiometric() }
            } catch (t: Throwable) {
                _ui.update { it.copy(busy = false, error = unlockErrorMessage(t)) }
                return@launch
            }
            if (ok) {
                countdownJob?.cancel()
                _ui.update { LockUiState() }
            } else {
                _ui.update { it.copy(busy = false) }
            }
        }
    }

    private fun unlockErrorMessage(t: Throwable): String =
        "Couldn't unlock: ${t.message ?: t.javaClass.simpleName}"

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
