package com.lcdcode.moodcairns.ui.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.repo.PromptWindowRepository
import com.lcdcode.moodcairns.work.PromptScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class PromptWindowEditUiState(
    val id: Long = 0,
    val label: String = "",
    val slot: PromptSlot = PromptSlot.CUSTOM,
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val endHour: Int = 10,
    val endMinute: Int = 0,
    val enabled: Boolean = true,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PromptWindowEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: PromptWindowRepository,
    private val scheduler: PromptScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(PromptWindowEditUiState())
    val state: StateFlow<PromptWindowEditUiState> = _state.asStateFlow()

    init {
        val id = savedState.get<Long>(ARG_WINDOW_ID)?.takeIf { it > 0L }
        if (id == null) {
            _state.update { it.copy(loaded = true) }
        } else {
            viewModelScope.launch {
                repo.byId(id)?.let { w ->
                    _state.update {
                        it.copy(
                            id = w.id,
                            label = w.label,
                            slot = w.slot,
                            startHour = w.startTime.hour,
                            startMinute = w.startTime.minute,
                            endHour = w.endTime.hour,
                            endMinute = w.endTime.minute,
                            enabled = w.enabled,
                            loaded = true,
                        )
                    }
                } ?: _state.update { it.copy(loaded = true, error = "Window not found") }
            }
        }
    }

    fun setLabel(v: String) = _state.update { it.copy(label = v, error = null) }
    fun setSlot(v: PromptSlot) = _state.update { it.copy(slot = v) }
    fun setStart(h: Int, m: Int) = _state.update { it.copy(startHour = h, startMinute = m, error = null) }
    fun setEnd(h: Int, m: Int) = _state.update { it.copy(endHour = h, endMinute = m, error = null) }
    fun setEnabled(v: Boolean) = _state.update { it.copy(enabled = v) }

    fun save() {
        val cur = _state.value
        val label = cur.label.trim()
        val start = LocalTime.of(cur.startHour, cur.startMinute)
        val end = LocalTime.of(cur.endHour, cur.endMinute)
        val err = when {
            label.isEmpty() -> "Label required"
            !start.isBefore(end) -> "Start must be before end"
            else -> null
        }
        if (err != null) {
            _state.update { it.copy(error = err) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repo.upsert(
                    PromptWindow(
                        id = cur.id,
                        label = label,
                        slot = cur.slot,
                        startTime = start,
                        endTime = end,
                        enabled = cur.enabled,
                    ),
                )
                scheduler.scheduleNow()
                _state.update { it.copy(saving = false, saved = true) }
            } catch (t: Throwable) {
                _state.update { it.copy(saving = false, error = t.message ?: "Save failed") }
            }
        }
    }

    fun delete() {
        val cur = _state.value
        if (cur.id == 0L) return
        viewModelScope.launch {
            runCatching {
                repo.delete(
                    PromptWindow(
                        id = cur.id,
                        label = cur.label,
                        slot = cur.slot,
                        startTime = LocalTime.of(cur.startHour, cur.startMinute),
                        endTime = LocalTime.of(cur.endHour, cur.endMinute),
                        enabled = cur.enabled,
                    ),
                )
                scheduler.scheduleNow()
            }
            _state.update { it.copy(saved = true) }
        }
    }

    companion object { const val ARG_WINDOW_ID = "windowId" }
}
