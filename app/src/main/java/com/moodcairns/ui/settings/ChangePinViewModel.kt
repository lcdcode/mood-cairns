package com.moodcairns.ui.settings

import androidx.lifecycle.ViewModel
import com.moodcairns.security.LockRepository
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
    private val repo: LockRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePinUiState())
    val state: StateFlow<ChangePinUiState> = _state.asStateFlow()

    fun setCurrent(v: String) = _state.update { it.copy(current = v.filter(Char::isDigit).take(10), error = null) }
    fun setNext(v: String) = _state.update { it.copy(next = v.filter(Char::isDigit).take(10), error = null) }
    fun setConfirm(v: String) = _state.update { it.copy(confirm = v.filter(Char::isDigit).take(10), error = null) }

    fun save() {
        val cur = _state.value
        val err = when {
            cur.next.length < 4 -> "New PIN must be at least 4 digits"
            cur.next != cur.confirm -> "PINs do not match"
            !repo.verifyPin(cur.current.toCharArray()) -> "Current PIN is incorrect"
            else -> null
        }
        if (err != null) {
            _state.update { it.copy(error = err) }
            return
        }
        _state.update { it.copy(saving = true) }
        try {
            repo.setPin(cur.next.toCharArray())
            _state.update { it.copy(saving = false, saved = true) }
        } catch (t: Throwable) {
            _state.update { it.copy(saving = false, error = t.message ?: "Save failed") }
        }
    }
}
