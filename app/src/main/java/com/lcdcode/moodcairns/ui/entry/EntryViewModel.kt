package com.lcdcode.moodcairns.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.EntryRepository
import com.lcdcode.moodcairns.data.repo.ScaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class EntryUiState(
    val scales: List<Scale> = emptyList(),
    val values: Map<Long, Float> = emptyMap(),
    val note: String = "",
    val recordedAt: Instant = Instant.now(),
    val slot: PromptSlot = PromptSlot.MANUAL,
    val promptWindowId: Long? = null,
    val saving: Boolean = false,
    val savedId: Long? = null,
    val editingId: Long? = null,
    val editLoaded: Boolean = false,
)

@HiltViewModel
class EntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val scales: ScaleRepository,
    private val entries: EntryRepository,
) : ViewModel() {

    private val editingId: Long? = savedState.get<Long>(ARG_ENTRY_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(
        EntryUiState(
            slot = savedState.get<String>(ARG_SLOT)?.let(PromptSlot::valueOf) ?: PromptSlot.MANUAL,
            promptWindowId = savedState.get<Long>(ARG_WINDOW_ID)?.takeIf { it >= 0 },
            recordedAt = savedState.get<Long>(ARG_RECORDED_AT)
                ?.takeIf { it >= 0 }
                ?.let(Instant::ofEpochMilli)
                ?: Instant.now(),
            editingId = editingId,
            editLoaded = editingId == null,
        ),
    )
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    init {
        if (editingId != null) {
            viewModelScope.launch {
                entries.getById(editingId)?.let { existing ->
                    _state.update {
                        it.copy(
                            recordedAt = existing.entry.recordedAt,
                            slot = existing.entry.slot,
                            promptWindowId = existing.entry.promptWindowId,
                            note = existing.entry.note.orEmpty(),
                            values = existing.values.associate { v -> v.scaleId to v.value },
                            editLoaded = true,
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            scales.observeActive().collect { list ->
                _state.update { cur ->
                    // Default each slider to the midpoint of its scale, but
                    // preserve any value already set (either from the user or,
                    // when editing, from the persisted entry).
                    val defaults = list.associate { s -> s.id to ((s.minValue + s.maxValue) / 2f) }
                    cur.copy(
                        scales = list,
                        values = defaults + cur.values.filterKeys { id -> list.any { it.id == id } },
                    )
                }
            }
        }
    }

    fun setValue(scaleId: Long, value: Float) {
        _state.update { it.copy(values = it.values + (scaleId to value)) }
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun setRecordedAt(instant: Instant) = _state.update { it.copy(recordedAt = instant) }

    fun save() {
        val cur = _state.value
        if (cur.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val id = if (cur.editingId != null) {
                entries.update(
                    id = cur.editingId,
                    recordedAt = cur.recordedAt,
                    slot = cur.slot,
                    promptWindowId = cur.promptWindowId,
                    note = cur.note,
                    values = cur.values,
                )
                cur.editingId
            } else {
                entries.save(
                    recordedAt = cur.recordedAt,
                    slot = cur.slot,
                    promptWindowId = cur.promptWindowId,
                    note = cur.note,
                    values = cur.values,
                )
            }
            _state.update { it.copy(saving = false, savedId = id) }
        }
    }

    companion object {
        const val ARG_SLOT = "slot"
        const val ARG_WINDOW_ID = "windowId"
        const val ARG_RECORDED_AT = "recordedAt"
        const val ARG_ENTRY_ID = "entryId"
    }
}
