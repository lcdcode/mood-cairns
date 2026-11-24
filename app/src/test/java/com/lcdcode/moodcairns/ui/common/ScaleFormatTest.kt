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
    fun range_label_uses_dash_for_non_negative_ranges() {
        assertEquals(dash("1", "10"), rangeLabel(1, 10, en))
        assertEquals(dash("0", "5"), rangeLabel(0, 5, en))
    }

    @Test
    fun range_label_uses_to_when_min_is_negative() {
        assertEquals(to("-5", "5"), rangeLabel(-5, 5, en))
        assertEquals(to("-10", "-1"), rangeLabel(-10, -1, en))
    }

    @Test
    fun format_scale_value_drops_trailing_zeroes() {
        assertEquals("7", formatScaleValue(7.0f, en))
        assertEquals("-3", formatScaleValue(-3.0f, en))
        assertEquals("2.5", formatScaleValue(2.5f, en))
    }

    @Test
    fun format_scale_value_uses_locale_decimal_separator() {
        assertEquals("2,5", formatScaleValue(2.5f, de))
        assertEquals("7", formatScaleValue(7.0f, de))
    }

    @Test
    fun format_scale_value_rounds_to_two_decimals() {
        assertEquals("3.33", formatScaleValue(10f / 3f, en))
        assertEquals("7", formatScaleValue(6.9999f, en))
    }

    @Test
    fun format_scale_setting_keeps_up_to_four_decimals() {
        assertEquals("0.125", formatScaleSetting(0.125f, en))
        assertEquals("0,125", formatScaleSetting(0.125f, de))
        assertEquals("1", formatScaleSetting(1f, en))
    }

    @Test
    fun format_scale_value_never_groups_digits() {
        assertEquals("1000", formatScaleValue(1000f, en))
    }

    @Test
    fun value_with_range_uses_slash_max_for_non_negative_ranges() {
        val s = Scale(name = "Happiness", minValue = 1, maxValue = 10, colorArgb = 0)
        assertEquals(
            UiText.Res(R.string.common_value_of_max, listOf("7", "10")),
            formatValueWithRange(7f, s, en),
        )
    }

    @Test
    fun value_with_range_shows_full_range_when_min_is_negative() {
        val s = Scale(name = "Balance", minValue = -5, maxValue = 5, colorArgb = 0)
        assertEquals(
            UiText.Res(R.string.common_value_in_range, listOf("-3", rangeLabel(-5, 5, en))),
            formatValueWithRange(-3f, s, en),
        )
    }
}
