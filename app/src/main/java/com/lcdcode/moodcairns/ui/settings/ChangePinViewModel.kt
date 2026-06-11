package com.lcdcode.moodcairns.ui.settings

import androidx.lifecycle.ViewModel
import com.lcdcode.moodcairns.security.ChangePinResult
import com.lcdcode.moodcairns.security.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ChangePinUiState(
    val current: String = "",
    val next: String = "",
    val confirm: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val lockManager: LockManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePinUiState())
    val state: StateFlow<ChangePinUiState> = _state.asStateFlow()

    fun setCurrent(v: String) = _state.update { it.copy(current = v.filter(Char::isDigit).take(10), error = null) }
    fun setNext(v: String) = _state.update { it.copy(next = v.filter(Char::isDigit).take(10), error = null) }
    fun setConfirm(v: String) = _state.update { it.copy(confirm = v.filter(Char::isDigit).take(10), error = null) }

    fun save() {
        val cur = _state.value
        val err = when {
            cur.next.length < MIN_PIN_LEN -> "New PIN must be at least $MIN_PIN_LEN digits"
            cur.next != cur.confirm -> "PINs do not match"
            else -> null
        }
        if (err != null) {
            _state.update { it.copy(error = err) }
            return
        }
        _state.update { it.copy(saving = true) }
        // Routed through LockManager so the DB-key wrap is re-keyed to the new
        // PIN alongside the PIN hash. Verifying the current PIN directly here and
        // calling setPin in isolation would advance the hash while leaving the
        // wrap openable only by the old PIN, locking the user out of their data.
        val result = lockManager.changePin(cur.current.toCharArray(), cur.next.toCharArray())
        when (result) {
            ChangePinResult.Success -> _state.update { it.copy(saving = false, saved = true) }
            ChangePinResult.WrongPin ->
                _state.update { it.copy(saving = false, error = "Current PIN is incorrect") }
            ChangePinResult.Locked ->
                _state.update { it.copy(saving = false, error = "App is locked; unlock and try again") }
        }
    }

    companion object {
        private const val MIN_PIN_LEN = 4
    }
}
