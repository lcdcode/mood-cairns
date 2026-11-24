package com.lcdcode.moodcairns.ui.entry

import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins when a once-per-day scale (e.g. Sleep) is asked on the entry screen. */
class AskedScalesTest {

    private val happiness = Scale(
        id = 1, name = "Happiness", minValue = 1, maxValue = 10, colorArgb = 0,
    )
    private val sleep = Scale(
        id = 6, name = "Sleep", minValue = 0, maxValue = 12, colorArgb = 0, oncePerDay = true,
    )
    private val scales = listOf(happiness, sleep)

    @Test
    fun first_entry_of_the_day_asks_every_scale() {
        assertEquals(scales, askedScales(scales, answeredElsewhereThatDay = setOf(1L), emptySet()))
    }

    @Test
    fun day_already_has_sleep_hides_it_but_keeps_regular_scales() {
        assertEquals(listOf(happiness), askedScales(scales, setOf(1L, 6L), emptySet()))
    }

    @Test
    fun while_the_day_is_loading_hides_once_per_day_scales() {
        assertEquals(listOf(happiness), askedScales(scales, null, emptySet()))
    }

    @Test
    fun editing_the_entry_that_recorded_sleep_keeps_it_editable() {
        assertEquals(scales, askedScales(scales, setOf(6L), originalScaleIds = setOf(1L, 6L)))
        assertEquals(scales, askedScales(scales, null, originalScaleIds = setOf(6L)))
    }

    @Test
    fun regular_scales_are_always_asked() {
        val regular = listOf(happiness)
        assertEquals(regular, askedScales(regular, setOf(1L), emptySet()))
        assertEquals(regular, askedScales(regular, null, emptySet()))
    }
}
