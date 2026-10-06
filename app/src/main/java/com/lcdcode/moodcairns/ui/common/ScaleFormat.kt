package com.lcdcode.moodcairns.ui.common

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Scale
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

private const val MAX_VALUE_FRACTION_DIGITS = 2

/**
 * A logged value in [locale]'s number style, without trailing zeros: "7", "2.5" (or
 * "2,5" in German). No digit grouping: scale values are small.
 */
fun formatScaleValue(v: Float, locale: Locale): String = valueFormat(locale).format(v.toDouble())

/** A scale bound (always whole) in [locale]'s number style. */
fun formatScaleBound(v: Int, locale: Locale): String = valueFormat(locale).format(v.toLong())

/** Range text that stays unambiguous with negative bounds: "1–10" but "-5 to 5". */
fun rangeLabel(min: Int, max: Int, locale: Locale): UiText = UiText.Res(
    if (min < 0) R.string.common_range_to else R.string.common_range_dash,
    listOf(formatScaleBound(min, locale), formatScaleBound(max, locale)),
)

/**
 * A value with its scale context: "7 / 10" for non-negative ranges, but
 * "-3 (-5 to 5)" when the range dips negative, since "v / max" hides the floor.
 */
fun formatValueWithRange(value: Float, scale: Scale, locale: Locale): UiText {
    val shown = formatScaleValue(value, locale)
    return if (scale.minValue < 0) {
        UiText.Res(
            R.string.common_value_in_range,
            listOf(shown, rangeLabel(scale.minValue, scale.maxValue, locale)),
        )
    } else {
        UiText.Res(
            R.string.common_value_of_max,
            listOf(shown, formatScaleBound(scale.maxValue, locale)),
        )
    }
}

private fun valueFormat(locale: Locale): NumberFormat =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        minimumFractionDigits = 0
        maximumFractionDigits = MAX_VALUE_FRACTION_DIGITS
        roundingMode = RoundingMode.HALF_UP
    }
