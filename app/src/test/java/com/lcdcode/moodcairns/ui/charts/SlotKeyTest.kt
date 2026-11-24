package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.PromptSlot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * Verifies how chart entries are bucketed onto prompt-slot filter chips,
 * especially the cases that don't map to a current window: Manual/Custom and
 * the Other catch-all (legacy slots and deleted windows).
 */
class SlotKeyTest {

    private val knownWindowIds = setOf(1L, 2L)

    private fun entry(slot: PromptSlot, windowId: Long?) = Entry(
        recordedAt = Instant.EPOCH,
        slot = slot,
        promptWindowId = windowId,
    )

    @Test
    fun live_window_maps_to_window_key() {
        assertEquals(SlotKey.Window(2L), slotKeyFor(entry(PromptSlot.EVENING, 2L), knownWindowIds))
    }

    @Test
    fun manual_maps_to_manual() {
        assertEquals(SlotKey.Manual, slotKeyFor(entry(PromptSlot.MANUAL, null), knownWindowIds))
    }

    @Test
    fun custom_maps_to_custom() {
        assertEquals(SlotKey.Custom, slotKeyFor(entry(PromptSlot.CUSTOM, null), knownWindowIds))
    }

    @Test
    fun deleted_window_buckets_to_other() {
        assertEquals(SlotKey.Other, slotKeyFor(entry(PromptSlot.MORNING, 99L), knownWindowIds))
    }

    @Test
    fun legacy_slot_without_window_buckets_to_other() {
        assertEquals(SlotKey.Other, slotKeyFor(entry(PromptSlot.MORNING, null), knownWindowIds))
    }
}
