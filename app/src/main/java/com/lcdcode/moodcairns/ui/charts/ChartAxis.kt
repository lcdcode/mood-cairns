package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.ui.common.UiText
import com.lcdcode.moodcairns.ui.common.displayNameArg
import com.lcdcode.moodcairns.ui.common.formatScaleValue
import com.lcdcode.moodcairns.ui.common.rangeLabel
import java.text.NumberFormat
import java.util.Locale

/**
 * Vertical-axis tick label. Auto-fit plots raw logged values, so ticks are just
 * numbers. Absolute normalizes every series onto a shared 0..1 axis, where a raw
 * number would be a lie - 0.5 is 5.5 on a 1-10 scale but 0 on a -5-5 one - so
 * ticks read as a percentage of each scale's own range instead.
 */
internal fun yAxisLabel(value: Double, absoluteY: Boolean, locale: Locale): String =
    if (absoluteY) {
        NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 0 }.format(value)
    } else {
        formatScaleValue(value.toFloat(), locale)
    }

/**
 * One line under the chart saying what the axis ticks mean, since a bare number
 * is ambiguous once several scales share one axis.
 *
 * The absolute caption says "top of range" rather than "best": only scales
 * flagged "lower is better" are flipped, so on a scale where high is bad but
 * the flag is unset (the built-ins, for one) 100% is the worst end.
 */
internal fun yAxisCaption(scales: List<Scale>, absoluteY: Boolean, locale: Locale): UiText = when {
    absoluteY -> UiText.Res(R.string.charts_axis_caption_absolute)
    scales.isEmpty() -> UiText.Res(R.string.charts_axis_caption_values)
    scales.size == 1 -> {
        val s = scales.first()
        UiText.Res(
            R.string.charts_axis_caption_single,
            listOf(s.displayNameArg(), rangeLabel(s.minValue, s.maxValue, locale)),
        )
    }
    else -> UiText.Plural(R.plurals.charts_axis_caption_shared, scales.size)
}
