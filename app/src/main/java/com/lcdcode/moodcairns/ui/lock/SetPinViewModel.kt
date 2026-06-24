package com.lcdcode.moodcairns.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class SetPinUiState(
    val pin: String = "",
    val confirm: String = "",
    val error: String? = null,
    /** True while setup (PBKDF2 + DB create) is running off the UI thread. */
    val saving: Boolean = false,
)

@HiltViewModel
class SetPinViewModel @Inject constructor(
    private val lockManager: LockManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(SetPinUiState())
    val ui: StateFlow<SetPinUiState> = _ui.asStateFlow()

    fun onPinChanged(value: String) =
        _ui.update { it.copy(pin = sanitize(value), error = null) }

    fun onConfirmChanged(value: String) =
        _ui.update { it.copy(confirm = sanitize(value), error = null) }

    fun submit() {
        val cur = _ui.value
        if (cur.saving) return
        when {
            cur.pin.length < MIN_PIN_LEN ->
                _ui.update { it.copy(error = "PIN must be at least $MIN_PIN_LEN digits") }
            cur.pin != cur.confirm ->
                _ui.update { it.copy(error = "PINs don't match") }
            else -> {
                _ui.update { it.copy(saving = true, error = null) }
                viewModelScope.launch {
                    try {
                        // PBKDF2 + the initial SQLCipher DB creation block long
                        // enough to ANR-crash slow devices on the UI thread.
                        withContext(Dispatchers.Default) {
                            lockManager.completeSetup(cur.pin.toCharArray())
                        }
                        // Success flips LockState to Unlocked, which swaps this
                        // screen out; no local UI reset needed.
                    } catch (t: Throwable) {
                        _ui.update {
                            it.copy(
                                saving = false,
                                error = "Couldn't set PIN: ${t.message ?: t.javaClass.simpleName}",
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Set the app up with no PIN. The DB is still encrypted at rest under the
     * keystore-held key; the caller must have shown the risk warning and gotten
     * explicit acceptance first.
     */
    fun continueWithoutPin() {
        if (_ui.value.saving) return
        _ui.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    lockManager.completeSetupWithoutPin()
                }
                // Success flips LockState to Unlocked, which swaps this screen out.
            } catch (t: Throwable) {
                _ui.update {
                    it.copy(
                        saving = false,
                        error = "Couldn't continue without a PIN: ${t.message ?: t.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    private fun sanitize(value: String) = value.filter(Char::isDigit).take(MAX_PIN_LEN)

    companion object {
        const val MIN_PIN_LEN = 4
        const val MAX_PIN_LEN = 10
    }
}
