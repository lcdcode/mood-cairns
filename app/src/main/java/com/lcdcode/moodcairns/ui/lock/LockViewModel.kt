package com.lcdcode.moodcairns.ui.lock

import androidx.lifecycle.ViewModel
import com.lcdcode.moodcairns.security.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LockUiState(
    val pin: String = "",
    val error: String? = null,
    val attempts: Int = 0,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val lockManager: LockManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(LockUiState())
    val ui: StateFlow<LockUiState> = _ui.asStateFlow()

    fun onPinChanged(pin: String) {
        _ui.update { it.copy(pin = pin.filter(Char::isDigit).take(MAX_PIN_LEN), error = null) }
    }

    fun submit() {
        val pin = _ui.value.pin
        if (pin.length < MIN_PIN_LEN) {
            _ui.update { it.copy(error = "PIN must be at least $MIN_PIN_LEN digits") }
            return
        }
        val ok = lockManager.tryUnlockWithPin(pin.toCharArray())
        if (!ok) {
            _ui.update { it.copy(pin = "", error = "Incorrect PIN", attempts = it.attempts + 1) }
        }
    }

    fun onBiometricSuccess() = lockManager.unlockViaBiometric()

    companion object {
        const val MIN_PIN_LEN = 4
        const val MAX_PIN_LEN = 10
    }
}
