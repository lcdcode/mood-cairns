package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins Absolute-mode normalization, including the inverse-scale flip. */
class ChartMathTest {

    private fun scale(min: Int, max: Int, inverted: Boolean = false) =
        Scale(name = "test", minValue = min, maxValue = max, colorArgb = 0, inverted = inverted)

    @Test
    fun normalScale_mapsMinToZero_maxToOne() {
        val s = scale(1, 10)
        assertEquals(0f, normalize(1f, s), 1e-6f)
        assertEquals(1f, normalize(10f, s), 1e-6f)
        assertEquals(0.5f, normalize(5.5f, s), 1e-6f)
    }

    @Test
    fun invertedScale_mapsMinToOne_maxToZero() {
        val s = scale(1, 10, inverted = true)
        assertEquals(1f, normalize(1f, s), 1e-6f)
        assertEquals(0f, normalize(10f, s), 1e-6f)
        assertEquals(0.5f, normalize(5.5f, s), 1e-6f)
    }

    @Test
    fun negativeRange_midpointIsHalf() {
        val s = scale(-5, 5)
        assertEquals(0.5f, normalize(0f, s), 1e-6f)
        assertEquals(0f, normalize(-5f, s), 1e-6f)
    }

    @Test
    fun negativeInvertedRange_minIsBest() {
        val s = scale(-5, 5, inverted = true)
        assertEquals(1f, normalize(-5f, s), 1e-6f)
        assertEquals(0f, normalize(5f, s), 1e-6f)
    }

    @Test
    fun outOfRangeValues_clampBeforeFlipping() {
        val s = scale(1, 10, inverted = true)
        assertEquals(1f, normalize(-3f, s), 1e-6f)
        assertEquals(0f, normalize(99f, s), 1e-6f)
    }

    @Test
    fun degenerateSpan_centersInsteadOfDividingByZero() {
        assertEquals(0.5f, normalize(3f, scale(5, 5)), 1e-6f)
    }
}
