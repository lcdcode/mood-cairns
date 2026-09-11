package com.lcdcode.moodcairns.ui.charts

import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the plot height, including the extra headroom signed scales get. */
class ChartLayoutTest {

    private fun scale(min: Int, max: Int) =
        Scale(name = "test", minValue = min, maxValue = max, colorArgb = 0)

    @Test
    fun positiveScales_useBaseHeight() {
        val scales = listOf(scale(1, 10), scale(0, 5))
        assertEquals(360.dp, chartHeight(scales, absoluteY = false))
    }

    @Test
    fun negativeScale_addsHeadroom() {
        val scales = listOf(scale(1, 10), scale(-5, 5))
        assertEquals(450.dp, chartHeight(scales, absoluteY = false))
    }

    @Test
    fun absoluteMode_neverAddsHeadroom() {
        assertEquals(360.dp, chartHeight(listOf(scale(-5, 5)), absoluteY = true))
    }

    @Test
    fun noScales_useBaseHeight() {
        assertEquals(360.dp, chartHeight(emptyList(), absoluteY = false))
    }
}
