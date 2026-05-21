package com.lcdcode.moodcairns.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.security.LockManager
import com.lcdcode.moodcairns.security.PinUnlockResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockUiState(
    val pin: String = "",
    val error: String? = null,
    val attempts: Int = 0,
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
        if (_ui.value.lockoutRemainingMs != null) return
        if (pin.length < MIN_PIN_LEN) {
            _ui.update { it.copy(error = "PIN must be at least $MIN_PIN_LEN digits") }
            return
        }
        when (val result = lockManager.tryUnlockWithPin(pin.toCharArray())) {
            PinUnlockResult.Success -> {
                countdownJob?.cancel()
                _ui.update { LockUiState() }
            }
            PinUnlockResult.WrongPin -> _ui.update {
                it.copy(pin = "", error = "Incorrect PIN", attempts = it.attempts + 1)
            }
            is PinUnlockResult.RateLimited -> {
                _ui.update {
                    it.copy(
                        pin = "",
                        error = "Too many attempts",
                        attempts = it.attempts + 1,
                    )
                }
                startCountdown(result.retryAfterMs)
            }
        }
    }

    fun canBiometricUnlock(): Boolean = lockManager.canBiometricUnlock()

    /**
     * Returns true if biometric unlock succeeded (DB key was available);
     * false means the LockScreen should fall through to PIN entry — e.g.
     * upgrading from a build that pre-dates DB encryption, where the
     * biometric key blob hasn't been populated yet.
     */
    fun onBiometricSuccess(): Boolean {
        val ok = lockManager.unlockViaBiometric()
        if (ok) {
            countdownJob?.cancel()
            _ui.update { LockUiState() }
        }
        return ok
    }

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
