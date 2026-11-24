package com.lcdcode.moodcairns.ui.entry

import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalTime

/**
 * Covers the slot-picker selection resolution, including the two outliers the
 * picker has to survive: legacy/orphaned entries and entries referencing a
 * disabled (pinned) window.
 */
class NormalizedSelectionTest {

    private fun window(id: Long, slot: PromptSlot, enabled: Boolean = true) = PromptWindow(
        id = id,
        label = "w$id",
        slot = slot,
        startTime = LocalTime.of(8, 0),
        endTime = LocalTime.of(10, 0),
        enabled = enabled,
    )

    private val morning = window(1, PromptSlot.MORNING)
    private val evening = window(2, PromptSlot.EVENING)
    private val windows = listOf(morning, evening)

    @Test
    fun keeps_valid_window_selection() {
        val state = EntryUiState(slot = PromptSlot.MORNING, promptWindowId = 1, windows = windows)
        assertSame(state, state.normalizedSelection())
    }

    @Test
    fun keeps_manual() {
        val state = EntryUiState(slot = PromptSlot.MANUAL, promptWindowId = null, windows = windows)
        assertSame(state, state.normalizedSelection())
    }

    @Test
    fun keeps_custom() {
        val state = EntryUiState(slot = PromptSlot.CUSTOM, promptWindowId = null, windows = windows)
        assertSame(state, state.normalizedSelection())
    }

    @Test
    fun legacy_entry_without_window_falls_back_to_same_slot_window() {
        // Outlier #2: slot set, no window id (old data) -> first enabled window of that slot.
        val state = EntryUiState(slot = PromptSlot.EVENING, promptWindowId = null, windows = windows)
        val result = state.normalizedSelection()
        assertEquals(PromptSlot.EVENING, result.slot)
        assertEquals(2L, result.promptWindowId)
    }

    @Test
    fun orphaned_window_id_falls_back_to_same_slot_window() {
        // Outlier #1, hard-deleted window: dangling id, no pin -> re-points to live same-slot window.
        val state = EntryUiState(slot = PromptSlot.MORNING, promptWindowId = 99, windows = windows)
        val result = state.normalizedSelection()
        assertEquals(1L, result.promptWindowId)
    }

    @Test
    fun falls_back_to_manual_when_no_matching_window() {
        val state = EntryUiState(slot = PromptSlot.MORNING, promptWindowId = null, windows = emptyList())
        val result = state.normalizedSelection()
        assertEquals(PromptSlot.MANUAL, result.slot)
        assertNull(result.promptWindowId)
    }

    @Test
    fun keeps_pinned_disabled_window() {
        // Outlier #1, disabled window: not in enabled list but pinned via extraWindow.
        val disabled = window(3, PromptSlot.CUSTOM, enabled = false)
        val state = EntryUiState(
            slot = PromptSlot.CUSTOM,
            promptWindowId = 3,
            windows = windows,
            extraWindow = disabled,
        )
        assertSame(state, state.normalizedSelection())
    }
}
