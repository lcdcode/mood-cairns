package com.lcdcode.moodcairns.ui.common

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Scale
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the shared range/value display helpers used by entry, history, and charts. */
class ScaleFormatTest {

    private val en = Locale.US
    private val de = Locale.GERMANY

    private fun dash(min: String, max: String) =
        UiText.Res(R.string.common_range_dash, listOf(min, max))

    private fun to(min: String, max: String) =
        UiText.Res(R.string.common_range_to, listOf(min, max))

    @Test
    fun rangeLabel_usesDash_forNonNegativeRanges() {
        assertEquals(dash("1", "10"), rangeLabel(1, 10, en))
        assertEquals(dash("0", "5"), rangeLabel(0, 5, en))
    }

    @Test
    fun rangeLabel_usesTo_whenMinIsNegative() {
        assertEquals(to("-5", "5"), rangeLabel(-5, 5, en))
        assertEquals(to("-10", "-1"), rangeLabel(-10, -1, en))
    }

    @Test
    fun formatScaleValue_dropsTrailingZeroes() {
        assertEquals("7", formatScaleValue(7.0f, en))
        assertEquals("-3", formatScaleValue(-3.0f, en))
        assertEquals("2.5", formatScaleValue(2.5f, en))
    }

    @Test
    fun formatScaleValue_usesLocaleDecimalSeparator() {
        assertEquals("2,5", formatScaleValue(2.5f, de))
        assertEquals("7", formatScaleValue(7.0f, de))
    }

    @Test
    fun formatScaleValue_roundsToTwoDecimals() {
        assertEquals("3.33", formatScaleValue(10f / 3f, en))
        assertEquals("7", formatScaleValue(6.9999f, en))
    }

    @Test
    fun formatScaleValue_neverGroupsDigits() {
        assertEquals("1000", formatScaleValue(1000f, en))
    }

    @Test
    fun valueWithRange_usesSlashMax_forNonNegativeRanges() {
        val s = Scale(name = "Happiness", minValue = 1, maxValue = 10, colorArgb = 0)
        assertEquals(
            UiText.Res(R.string.common_value_of_max, listOf("7", "10")),
            formatValueWithRange(7f, s, en),
        )
    }

    @Test
    fun valueWithRange_showsFullRange_whenMinIsNegative() {
        val s = Scale(name = "Balance", minValue = -5, maxValue = 5, colorArgb = 0)
        assertEquals(
            UiText.Res(R.string.common_value_in_range, listOf("-3", rangeLabel(-5, 5, en))),
            formatValueWithRange(-3f, s, en),
        )
    }
}
