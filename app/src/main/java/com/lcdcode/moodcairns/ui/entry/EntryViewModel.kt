package com.lcdcode.moodcairns.ui.entry

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.EntryRepository
import com.lcdcode.moodcairns.data.repo.PromptWindowRepository
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
    val windows: List<PromptWindow> = emptyList(),
    // A window referenced by the entry being edited that is no longer in the
    // enabled set (disabled in settings). Pinned so its chip still renders and
    // the value round-trips on save instead of being silently dropped.
    val extraWindow: PromptWindow? = null,
    val saving: Boolean = false,
    val savedId: Long? = null,
    val editingId: Long? = null,
    val editLoaded: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class EntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val scales: ScaleRepository,
    private val entries: EntryRepository,
    private val promptWindows: PromptWindowRepository,
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
                val existing = entries.getById(editingId)
                if (existing == null) {
                    // The entry was deleted between navigation and load — fall
                    // back to a "create new" flow rather than silently calling
                    // update() against a missing id later.
                    _state.update { it.copy(editingId = null, editLoaded = true) }
                } else {
                    val refWindow = existing.entry.promptWindowId
                        ?.let { promptWindows.byId(it) }
                    _state.update {
                        it.copy(
                            recordedAt = existing.entry.recordedAt,
                            slot = existing.entry.slot,
                            promptWindowId = existing.entry.promptWindowId,
                            note = existing.entry.note.orEmpty(),
                            values = existing.values.associate { v -> v.scaleId to v.value },
                            extraWindow = refWindow?.takeUnless(PromptWindow::enabled),
                            editLoaded = true,
                        ).normalizedSelection()
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
        viewModelScope.launch {
            promptWindows.observeAll().collect { all ->
                _state.update { cur ->
                    cur.copy(windows = all.filter(PromptWindow::enabled)).normalizedSelection()
                }
            }
        }
    }

    fun selectWindow(window: PromptWindow) =
        _state.update { it.copy(slot = window.slot, promptWindowId = window.id) }

    fun selectManual() =
        _state.update { it.copy(slot = PromptSlot.MANUAL, promptWindowId = null) }

    fun selectCustom() =
        _state.update { it.copy(slot = PromptSlot.CUSTOM, promptWindowId = null) }

    fun setValue(scaleId: Long, value: Float) {
        _state.update { it.copy(values = it.values + (scaleId to value)) }
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }

    fun setRecordedAt(instant: Instant) = _state.update { it.copy(recordedAt = instant) }

    fun save() {
        val cur = _state.value
        if (cur.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
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
            } catch (t: Throwable) {
                Log.w(TAG, "Save failed", t)
                _state.update {
                    it.copy(
                        saving = false,
                        error = "Couldn't save: ${t.message ?: t.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        private const val TAG = "EntryViewModel"
        const val ARG_SLOT = "slot"
        const val ARG_WINDOW_ID = "windowId"
        const val ARG_RECORDED_AT = "recordedAt"
        const val ARG_ENTRY_ID = "entryId"
    }
}

/**
 * Resolves the slot picker selection to a value that maps onto an actual chip.
 *
 * Idempotent, so it is safe to apply on every windows emission and after the
 * edit load. Leaves an already-valid selection untouched; otherwise it covers
 * two outliers:
 *  - A legacy or orphaned entry (slot set but no live window: missing id, or a
 *    window that was hard-deleted) falls back to the first enabled window of
 *    the same slot, or Manual if none exists.
 *  - A disabled window referenced by the edited entry is kept via [extraWindow]
 *    above (matched here as a valid selection) so the value is not lost.
 */
internal fun EntryUiState.normalizedSelection(): EntryUiState {
    val matchesLiveWindow = promptWindowId != null &&
        (windows.any { it.id == promptWindowId } || extraWindow?.id == promptWindowId)
    if (matchesLiveWindow) return this
    if (promptWindowId == null && (slot == PromptSlot.MANUAL || slot == PromptSlot.CUSTOM)) return this

    val fallback = windows.firstOrNull { it.slot == slot }
    return if (fallback != null) copy(slot = fallback.slot, promptWindowId = fallback.id)
    else copy(slot = PromptSlot.MANUAL, promptWindowId = null)
}
