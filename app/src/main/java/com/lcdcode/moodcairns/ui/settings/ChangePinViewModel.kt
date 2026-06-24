package com.lcdcode.moodcairns.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.security.ChangePinResult
import com.lcdcode.moodcairns.security.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChangePinUiState(
    /** False when entered from no-PIN mode: there is no current PIN, so the
     *  screen acts as "Set PIN" and hides the current-PIN field. */
    val hasExistingPin: Boolean = true,
    val current: String = "",
    val next: String = "",
    val confirm: String = "",
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** Drives the PIN-removal risk dialog (empty new PIN while a PIN exists). */
    val showRemoveWarning: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val lockManager: LockManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePinUiState(hasExistingPin = lockManager.isPinSet()))
    val state: StateFlow<ChangePinUiState> = _state.asStateFlow()

    fun setCurrent(v: String) = _state.update { it.copy(current = digits(v), error = null) }
    fun setNext(v: String) = _state.update { it.copy(next = digits(v), error = null) }
    fun setConfirm(v: String) = _state.update { it.copy(confirm = digits(v), error = null) }

    /**
     * Primary action. Routes to one of three flows depending on state:
     *  - no existing PIN  -> set a PIN (no current-PIN verification)
     *  - empty new PIN    -> remove the PIN (after the risk dialog)
     *  - otherwise        -> change the PIN
     */
    fun save() {
        val cur = _state.value

        if (!cur.hasExistingPin) {
            val err = validateNewPin(cur.next, cur.confirm)
            if (err != null) {
                _state.update { it.copy(error = err) }
                return
            }
            runSecretChange(saving = true) { lockManager.setPinFromNoPin(cur.next.toCharArray()) }
            return
        }

        if (cur.next.isEmpty() && cur.confirm.isEmpty()) {
            // Empty new PIN means "remove PIN". Require the current PIN up front
            // so the destructive dialog isn't shown for an entry we can't honor.
            if (cur.current.isEmpty()) {
                _state.update { it.copy(error = "Enter your current PIN to remove it") }
                return
            }
            _state.update { it.copy(showRemoveWarning = true, error = null) }
            return
        }

        val err = validateNewPin(cur.next, cur.confirm)
        if (err != null) {
            _state.update { it.copy(error = err) }
            return
        }
        runSecretChange(saving = true) {
            lockManager.changePin(cur.current.toCharArray(), cur.next.toCharArray())
        }
    }

    fun confirmRemovePin() {
        val cur = _state.value
        _state.update { it.copy(showRemoveWarning = false) }
        runSecretChange(saving = true) { lockManager.removePin(cur.current.toCharArray()) }
    }

    fun cancelRemovePin() = _state.update { it.copy(showRemoveWarning = false) }

    /** Run a LockManager credential change off the UI thread and map its result. */
    private fun runSecretChange(saving: Boolean, block: suspend () -> ChangePinResult) {
        _state.update { it.copy(saving = saving, error = null) }
        // The PBKDF2 passes (verify and/or wrap) run off the UI thread.
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.Default) { block() }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(saving = false, error = "Couldn't update PIN: ${t.message ?: t.javaClass.simpleName}")
                }
                return@launch
            }
            when (result) {
                ChangePinResult.Success -> _state.update { it.copy(saving = false, saved = true) }
                ChangePinResult.WrongPin ->
                    _state.update { it.copy(saving = false, error = "Current PIN is incorrect") }
                is ChangePinResult.RateLimited ->
                    _state.update {
                        it.copy(saving = false, error = "Too many attempts. Try again in ${ceilSeconds(result.retryAfterMs)}s")
                    }
                ChangePinResult.Locked ->
                    _state.update { it.copy(saving = false, error = "App is locked; unlock and try again") }
            }
        }
    }

    private fun digits(v: String) = v.filter(Char::isDigit).take(MAX_PIN_LEN)

    private fun validateNewPin(next: String, confirm: String): String? = when {
        next.length < MIN_PIN_LEN -> "New PIN must be at least $MIN_PIN_LEN digits"
        next != confirm -> "PINs do not match"
        else -> null
    }

    companion object {
        private const val MIN_PIN_LEN = 4
        private const val MAX_PIN_LEN = 10

        private fun ceilSeconds(ms: Long): Long = ((ms + 999) / 1000).coerceAtLeast(1)
    }
}
