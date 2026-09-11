package com.lcdcode.moodcairns.ui.common

import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the shared range/value display helpers used by entry, history, and charts. */
class ScaleFormatTest {

    @Test
    fun rangeLabel_usesEnDash_forNonNegativeRanges() {
        assertEquals("1–10", rangeLabel(1, 10))
        assertEquals("0–5", rangeLabel(0, 5))
    }

    @Test
    fun rangeLabel_usesTo_whenMinIsNegative() {
        assertEquals("-5 to 5", rangeLabel(-5, 5))
        assertEquals("-10 to -1", rangeLabel(-10, -1))
    }

    @Test
    fun formatScaleValue_dropsTrailingZeroes() {
        assertEquals("7", formatScaleValue(7.0f))
        assertEquals("-3", formatScaleValue(-3.0f))
        assertEquals("2.5", formatScaleValue(2.5f))
    }

    @Test
    fun valueWithRange_usesSlashMax_forNonNegativeRanges() {
        val s = Scale(name = "Happiness", minValue = 1, maxValue = 10, colorArgb = 0)
        assertEquals("7 / 10", formatValueWithRange(7f, s))
    }

    @Test
    fun valueWithRange_showsFullRange_whenMinIsNegative() {
        val s = Scale(name = "Balance", minValue = -5, maxValue = 5, colorArgb = 0)
        assertEquals("-3 (-5 to 5)", formatValueWithRange(-3f, s))
    }
}
