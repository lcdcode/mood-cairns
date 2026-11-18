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
    fun firstEntryOfTheDay_asksEveryScale() {
        assertEquals(scales, askedScales(scales, answeredElsewhereThatDay = setOf(1L), emptySet()))
    }

    @Test
    fun dayAlreadyHasSleep_hidesIt_butKeepsRegularScales() {
        assertEquals(listOf(happiness), askedScales(scales, setOf(1L, 6L), emptySet()))
    }

    @Test
    fun whileTheDayIsLoading_hidesOncePerDayScales() {
        assertEquals(listOf(happiness), askedScales(scales, null, emptySet()))
    }

    @Test
    fun editingTheEntryThatRecordedSleep_keepsItEditable() {
        assertEquals(scales, askedScales(scales, setOf(6L), originalScaleIds = setOf(1L, 6L)))
        assertEquals(scales, askedScales(scales, null, originalScaleIds = setOf(6L)))
    }

    @Test
    fun regularScales_areAlwaysAsked() {
        val regular = listOf(happiness)
        assertEquals(regular, askedScales(regular, setOf(1L), emptySet()))
        assertEquals(regular, askedScales(regular, null, emptySet()))
    }
}
