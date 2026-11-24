package com.lcdcode.moodcairns.ui.entry

import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.DayScaleLog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins where a once-per-day slider starts: at the minimum when its day was logged only
 * at the minimum, so scrolling past a re-asked Sleep keeps meaning "ask me later".
 */
class OncePerDayStartTest {

    private val happiness = Scale(
        id = 1, name = "Happiness", minValue = 1, maxValue = 10, colorArgb = 0,
        defaultValue = 5f,
    )
    private val sleep = Scale(
        id = 6, name = "Sleep", minValue = 0, maxValue = 12, step = 0.5f, colorArgb = 0,
        defaultValue = 7.5f, oncePerDay = true,
    )
    private val deferredDay = DayScaleLog(deferred = setOf(6L))

    private fun newEntry(log: DayScaleLog?) = EntryUiState(
        scales = listOf(happiness, sleep),
        values = mapOf(1L to 5f, 6L to 7.5f),
        elsewhereThatDay = log,
    )

    @Test
    fun deferred_day_starts_sleep_at_minimum_and_leaves_regular_scales() {
        val state = newEntry(deferredDay).withOncePerDayStarts()

        assertEquals(mapOf(1L to 5f, 6L to 0f), state.values)
    }

    @Test
    fun fresh_day_or_loading_starts_sleep_at_its_default() {
        assertEquals(7.5f, newEntry(DayScaleLog()).withOncePerDayStarts().values[6L])
        assertEquals(7.5f, newEntry(null).withOncePerDayStarts().values[6L])
    }

    @Test
    fun changing_to_a_fresh_day_restores_the_default() {
        val onDeferredDay = newEntry(deferredDay).withOncePerDayStarts()

        val movedDay = onDeferredDay.copy(elsewhereThatDay = DayScaleLog()).withOncePerDayStarts()

        assertEquals(7.5f, movedDay.values[6L])
    }

    @Test
    fun touched_slider_is_never_reset() {
        val touched = newEntry(deferredDay).copy(
            values = mapOf(1L to 5f, 6L to 9f),
            touchedScaleIds = setOf(6L),
        )

        assertEquals(9f, touched.withOncePerDayStarts().values[6L])
    }

    @Test
    fun edited_entry_is_left_alone() {
        val editing = newEntry(deferredDay).copy(editingId = 42L, values = mapOf(1L to 3f))

        assertEquals(mapOf(1L to 3f), editing.withOncePerDayStarts().values)
        assertEquals(0f, editing.startValue(sleep))
    }

    @Test
    fun reask_hint_shows_only_for_a_once_per_day_scale_on_a_deferred_day() {
        assertEquals(true, newEntry(deferredDay).isReasked(sleep))
        assertEquals(false, newEntry(DayScaleLog(answered = setOf(1L))).isReasked(sleep))
        assertEquals(false, newEntry(null).isReasked(sleep))
        assertEquals(false, newEntry(DayScaleLog(deferred = setOf(1L))).isReasked(happiness))
    }

    @Test
    fun regular_scale_always_starts_at_its_default() {
        val regularInDeferredSet = DayScaleLog(deferred = setOf(1L))

        assertEquals(5f, happiness.startValue(regularInDeferredSet.deferred))
    }
}
