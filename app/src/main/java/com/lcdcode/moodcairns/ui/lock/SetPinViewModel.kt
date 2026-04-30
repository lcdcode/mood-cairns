package com.lcdcode.moodcairns.ui.lock

import androidx.lifecycle.ViewModel
import com.lcdcode.moodcairns.security.LockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SetPinUiState(
    val pin: String = "",
    val confirm: String = "",
    val error: String? = null,
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
        when {
            cur.pin.length < MIN_PIN_LEN ->
                _ui.update { it.copy(error = "PIN must be at least $MIN_PIN_LEN digits") }
            cur.pin != cur.confirm ->
                _ui.update { it.copy(error = "PINs don't match") }
            else -> lockManager.completeSetup(cur.pin.toCharArray())
        }
    }

    private fun sanitize(value: String) = value.filter(Char::isDigit).take(MAX_PIN_LEN)

    companion object {
        const val MIN_PIN_LEN = 4
        const val MAX_PIN_LEN = 10
    }
}
