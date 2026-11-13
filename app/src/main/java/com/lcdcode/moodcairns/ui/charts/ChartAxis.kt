package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.ui.common.UiText
import com.lcdcode.moodcairns.ui.common.displayNameArg
import com.lcdcode.moodcairns.ui.common.formatScaleValue
import com.lcdcode.moodcairns.ui.common.rangeLabel
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/** Label spacings for the date chart's minute axis, finest first: hours, then days. */
internal val MINUTE_AXIS_LABEL_SPACINGS: List<Int> =
    listOf(1, 2, 3, 6, 12).map { it * MINUTES_PER_HOUR } +
        listOf(1, 2, 3, 7, 14, 28, 56, 91, 182, 364).map { it * MINUTES_PER_DAY }

/** Label spacings for the Hourly chart's 0-23 axis, finest first. */
internal val HOUR_AXIS_LABEL_SPACINGS: List<Int> = listOf(1, 2, 3, 6, 12)

/** Minimum distance between label centers, as a multiple of the widest label. */
private const val LABEL_GAP_FACTOR = 1.3f

/**
 * Places bottom-axis labels at round multiples (every 6h, every 7 days...) and picks
 * the finest spacing that still fits at the current zoom, so zooming in reveals
 * hour labels and zooming out thins them to days or weeks. Everything except label
 * placement (insets, extreme-label padding) is Vico's aligned placer.
 */
internal class SteppedLabelPlacer(
    private val spacings: List<Int>,
    base: HorizontalAxis.ItemPlacer = HorizontalAxis.ItemPlacer.aligned(),
) : HorizontalAxis.ItemPlacer by base {

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        // xSpacing is already scaled by the current zoom factor.
        val pxPerUnit = context.horizontalDimensions.xSpacing / context.ranges.xStep.toFloat()
        val spacing = labelSpacing(spacings, pxPerUnit, maxLabelWidth * LABEL_GAP_FACTOR)
        return alignedLabelValues(
            spacing = spacing,
            visibleStart = visibleXRange.start,
            visibleEnd = visibleXRange.endInclusive,
            minX = context.ranges.minX,
            maxX = context.ranges.maxX,
        )
    }
}

/** A minute-axis position split into (day index, minute of day). */
internal data class MinuteAxisPosition(val dayIndex: Long, val minuteOfDay: Int) {
    /** Midnight ticks name the day; every other tick names the time. */
    val isMidnight: Boolean get() = minuteOfDay == 0
}

internal fun minuteAxisPosition(x: Double): MinuteAxisPosition {
    val minutes = x.roundToLong()
    return MinuteAxisPosition(
        dayIndex = Math.floorDiv(minutes, MINUTES_PER_DAY.toLong()),
        minuteOfDay = Math.floorMod(minutes, MINUTES_PER_DAY.toLong()).toInt(),
    )
}

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
