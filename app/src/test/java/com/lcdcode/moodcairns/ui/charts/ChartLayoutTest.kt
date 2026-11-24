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
    fun positive_scales_use_base_height() {
        val scales = listOf(scale(1, 10), scale(0, 5))
        assertEquals(360.dp, chartHeight(scales, absoluteY = false))
    }

    @Test
    fun negative_scale_adds_headroom() {
        val scales = listOf(scale(1, 10), scale(-5, 5))
        assertEquals(450.dp, chartHeight(scales, absoluteY = false))
    }

    @Test
    fun absolute_mode_never_adds_headroom() {
        assertEquals(360.dp, chartHeight(listOf(scale(-5, 5)), absoluteY = true))
    }

    @Test
    fun no_scales_use_base_height() {
        assertEquals(360.dp, chartHeight(emptyList(), absoluteY = false))
    }
}
