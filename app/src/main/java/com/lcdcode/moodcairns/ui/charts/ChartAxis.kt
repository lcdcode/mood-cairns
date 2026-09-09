package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.ui.common.formatScaleValue
import com.lcdcode.moodcairns.ui.common.rangeLabel
import kotlin.math.roundToInt

/**
 * Vertical-axis tick label. Auto-fit plots raw logged values, so ticks are just
 * numbers. Absolute normalizes every series onto a shared 0..1 axis, where a raw
 * number would be a lie - 0.5 is 5.5 on a 1-10 scale but 0 on a -5-5 one - so
 * ticks read as a percentage of each scale's own range instead.
 */
internal fun yAxisLabel(value: Double, absoluteY: Boolean): String =
    if (absoluteY) "${(value * 100).roundToInt()}%" else formatScaleValue(value.toFloat())

/**
 * One line under the chart saying what the axis ticks mean, since a bare number
 * is ambiguous once several scales share one axis.
 */
internal fun yAxisCaption(scales: List<Scale>, absoluteY: Boolean): String = when {
    absoluteY -> "Vertical axis: position within each scale's own range (100% = best)"
    scales.isEmpty() -> "Vertical axis: logged values"
    scales.size == 1 -> {
        val s = scales.first()
        "Vertical axis: ${s.name} (${rangeLabel(s.minValue, s.maxValue)})"
    }
    else -> "Vertical axis: logged values - ${scales.size} scales share one axis"
}
