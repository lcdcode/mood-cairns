package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins vertical-axis tick labels and the caption that disambiguates them. */
class ChartAxisTest {

    private fun scale(name: String, min: Int, max: Int, inverted: Boolean = false) =
        Scale(name = name, minValue = min, maxValue = max, colorArgb = 0, inverted = inverted)

    @Test
    fun autoFit_labelsAreRawValues() {
        assertEquals("7", yAxisLabel(7.0, absoluteY = false))
        assertEquals("-3", yAxisLabel(-3.0, absoluteY = false))
        assertEquals("2.5", yAxisLabel(2.5, absoluteY = false))
    }

    @Test
    fun absolute_labelsArePercentages() {
        assertEquals("0%", yAxisLabel(0.0, absoluteY = true))
        assertEquals("50%", yAxisLabel(0.5, absoluteY = true))
        assertEquals("100%", yAxisLabel(1.0, absoluteY = true))
    }

    @Test
    fun singleScale_captionNamesItAndItsRange() {
        val caption = yAxisCaption(listOf(scale("Mood", 1, 10)), absoluteY = false)
        assertEquals("Vertical axis: Mood (1–10)", caption)
    }

    @Test
    fun singleNegativeScale_captionSpellsOutTheFloor() {
        val caption = yAxisCaption(listOf(scale("Energy", -5, 5)), absoluteY = false)
        assertEquals("Vertical axis: Energy (-5 to 5)", caption)
    }

    @Test
    fun multipleScales_captionWarnsTheAxisIsShared() {
        val scales = listOf(scale("Mood", 1, 10), scale("Energy", -5, 5))
        val caption = yAxisCaption(scales, absoluteY = false)
        assertTrue(caption, caption.contains("2 scales share one axis"))
    }

    @Test
    fun absolute_captionIgnoresScalesAndExplainsPercent() {
        val scales = listOf(scale("Mood", 1, 10), scale("Pain", -5, 5, inverted = true))
        assertEquals(
            yAxisCaption(scales, absoluteY = true),
            yAxisCaption(listOf(scale("Mood", 1, 10)), absoluteY = true),
        )
        val caption = yAxisCaption(scales, absoluteY = true)
        assertTrue(caption, caption.contains("100% = top of range"))
        // "best" would be wrong for a high-is-bad scale left unflagged.
        assertFalse(caption, caption.contains("best"))
    }

    @Test
    fun noScales_captionStaysGeneric() {
        assertEquals("Vertical axis: logged values", yAxisCaption(emptyList(), absoluteY = false))
    }
}
