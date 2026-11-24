package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.data.entity.Scale
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

internal const val MINUTES_PER_HOUR = 60
internal const val HOURS_PER_DAY = 24
internal const val MINUTES_PER_DAY = MINUTES_PER_HOUR * HOURS_PER_DAY

/** Daily and averaged points sit at local noon, centered within their day on the minute axis. */
internal const val DAY_POINT_MINUTE = MINUTES_PER_DAY / 2

/**
 * Maps a raw value onto the Absolute chart's shared 0..1 axis using the scale's
 * full range. Inverse scales are flipped so "better" always points up; raw
 * values elsewhere (auto-fit mode, tapped-point card) are never flipped.
 */
internal fun normalize(value: Float, scale: Scale): Float {
    val span = (scale.maxValue - scale.minValue).toFloat()
    if (span <= 0f) return 0.5f
    val n = ((value - scale.minValue) / span).coerceIn(0f, 1f)
    return if (scale.inverted) 1f - n else n
}

/**
 * Position on the date chart's minute axis, measured from the start of [startDate].
 * Built from local wall-clock fields so DST days still span exactly one day's worth
 * of minutes and labels line up with midnight.
 */
internal fun minuteX(local: LocalDateTime, startDate: LocalDate): Int {
    val dayIndex = (local.toLocalDate().toEpochDay() - startDate.toEpochDay()).toInt()
    return dayIndex * MINUTES_PER_DAY + local.hour * MINUTES_PER_HOUR + local.minute
}

/** Minute-axis position of a per-day point (daily mean or average). */
internal fun dayPointX(dayIndex: Int): Int = dayIndex * MINUTES_PER_DAY + DAY_POINT_MINUTE

/**
 * Triangular-kernel moving average centered on each day: day i+k weighs
 * `halfWindow + 1 - |k|`, so a one-day spike stays peaked on its own day and tapers
 * symmetrically instead of smearing into the days after it. Weighted per entry, so a
 * day with three entries counts three times as much as a day with one.
 *
 * [sums] and [counts] carry [padding] extra days on each side. Those days feed the
 * edge averages but get no point of their own; result indices are relative to the
 * first unpadded day.
 */
internal fun centeredWeightedAverage(
    sums: FloatArray,
    counts: IntArray,
    halfWindow: Int,
    padding: Int,
): List<DayPoint> {
    require(sums.size == counts.size) { "sums (${sums.size}) and counts (${counts.size}) differ" }
    require(halfWindow >= 0 && padding >= 0) { "halfWindow=$halfWindow padding=$padding" }
    val out = mutableListOf<DayPoint>()
    for (center in padding until sums.size - padding) {
        var weightedSum = 0f
        var weightedCount = 0f
        for (offset in -halfWindow..halfWindow) {
            val day = center + offset
            if (day !in sums.indices) continue
            val weight = (halfWindow + 1 - abs(offset)).toFloat()
            weightedSum += weight * sums[day]
            weightedCount += weight * counts[day]
        }
        if (weightedCount > 0f) out += DayPoint(center - padding, weightedSum / weightedCount)
    }
    return out
}

/** Mean value per hour of day for (hour, value) samples; hours without samples are omitted. */
internal fun hourlyMeans(samples: List<Pair<Int, Float>>): List<HourPoint> {
    val sums = FloatArray(HOURS_PER_DAY)
    val counts = IntArray(HOURS_PER_DAY)
    for ((hour, value) in samples) {
        require(hour in 0 until HOURS_PER_DAY) { "hour $hour outside 0..${HOURS_PER_DAY - 1}" }
        sums[hour] += value
        counts[hour] += 1
    }
    return (0 until HOURS_PER_DAY).mapNotNull { h ->
        if (counts[h] == 0) null else HourPoint(h, sums[h] / counts[h])
    }
}

/**
 * [scale]'s Hourly points. None for a once-per-day scale: the hour its value was
 * logged says nothing about it, so it drops out of Hourly mode entirely.
 */
internal fun hourlyPointsFor(scale: Scale, samples: List<Pair<Int, Float>>): List<HourPoint> =
    if (scale.oncePerDay) emptyList() else hourlyMeans(samples)

/**
 * Day indices on which a once-per-day [scale] has a value above its minimum, from
 * (dayIndex, value) samples. Empty for a regular scale, whose minimum is just a value.
 */
internal fun daysAnsweredAboveMinimum(scale: Scale, samples: List<Pair<Int, Float>>): Set<Int> =
    if (!scale.oncePerDay) {
        emptySet()
    } else {
        samples.filter { (_, value) -> value > scale.minValue }.mapTo(HashSet()) { it.first }
    }

/**
 * A once-per-day minimum can mean "not logged yet", so it is left off the chart when
 * its day also has a higher value. A day with nothing but minimums keeps them.
 */
internal fun isSupersededMinimum(
    value: Float,
    dayIndex: Int,
    scale: Scale,
    answeredDays: Set<Int>,
): Boolean = value <= scale.minValue && dayIndex in answeredDays

/**
 * Share of the full x range on screen. Vico's minimum zoom is the factor that fits
 * all content, so the visible share is that over the current factor. Before the
 * first measure both are zero, which reads as fully zoomed out.
 */
internal fun visibleFraction(contentZoom: Float, zoom: Float): Float =
    if (contentZoom <= 0f || zoom <= 0f) 1f else (contentZoom / zoom).coerceIn(0f, 1f)

/**
 * Finest spacing from [candidates] (ascending, in x units) whose on-screen gap is
 * at least [minGapPx]; falls back to the coarsest when even that is too tight.
 */
internal fun labelSpacing(candidates: List<Int>, pxPerUnit: Float, minGapPx: Float): Int {
    require(candidates.isNotEmpty()) { "no label spacing candidates" }
    if (pxPerUnit <= 0f) return candidates.last()
    return candidates.firstOrNull { it * pxPerUnit >= minGapPx } ?: candidates.last()
}

/**
 * Multiples of [spacing] covering [visibleStart]..[visibleEnd] plus one extra on each
 * side (so labels slide in smoothly while scrolling), clipped to [minX]..[maxX].
 */
internal fun alignedLabelValues(
    spacing: Int,
    visibleStart: Double,
    visibleEnd: Double,
    minX: Double,
    maxX: Double,
): List<Double> {
    require(spacing > 0) { "spacing must be positive, was $spacing" }
    // floor/ceil land on the nearest multiple at or just past each visible edge.
    val first = floor(visibleStart / spacing).toLong() * spacing
    val last = ceil(visibleEnd / spacing).toLong() * spacing
    return (first..last step spacing.toLong())
        .map { it.toDouble() }
        .filter { it in minX..maxX }
}

/** One plotted point in the active mode's x units (minutes, or hours for Hourly). */
internal data class PlotPoint(val x: Int, val value: Float)

/**
 * The points [mode] draws for [series]. Date modes share the minute axis so Raw can
 * swap daily means for per-entry points ([showEntries]) without moving the x range.
 */
internal fun plotPoints(
    series: ScaleSeries,
    mode: ChartMode,
    showEntries: Boolean,
): List<PlotPoint> =
    when (mode) {
        ChartMode.Raw -> if (showEntries) {
            series.entries.map { PlotPoint(it.minuteX, it.value) }
        } else {
            series.daily.map { PlotPoint(dayPointX(it.dayIndex), it.value) }
        }
        ChartMode.RollingAvg -> series.rolling.map { PlotPoint(dayPointX(it.dayIndex), it.value) }
        ChartMode.Hourly -> series.hourly.map { PlotPoint(it.hour, it.value) }
    }

/**
 * Splits x-sorted minute-axis [points] into runs on consecutive days, so a line breaks
 * across a day with no value instead of bridging it. Points on one day stay together.
 */
internal fun splitAtMissingDays(points: List<PlotPoint>): List<List<PlotPoint>> {
    val runs = mutableListOf<MutableList<PlotPoint>>()
    var previousDay: Int? = null
    for (point in points) {
        val day = Math.floorDiv(point.x, MINUTES_PER_DAY)
        if (previousDay == null || day - previousDay > 1) runs += mutableListOf<PlotPoint>()
        runs.last() += point
        previousDay = day
    }
    return runs
}

/** One series as Vico draws it: all of [scale]'s points, or one unbroken run of them. */
internal data class PlotSegment(val scale: Scale, val points: List<PlotPoint>)

/**
 * The series to draw, in order. A once-per-day scale on the date axis is split at
 * missing days, so its line never implies a value for a day that has none; every
 * other scale is one series. Scales with nothing to plot are skipped.
 */
internal fun plotSegments(
    series: List<ScaleSeries>,
    mode: ChartMode,
    showEntries: Boolean,
): List<PlotSegment> = series.flatMap { s ->
    val points = plotPoints(s, mode, showEntries)
    when {
        points.isEmpty() -> emptyList()
        s.scale.oncePerDay && mode != ChartMode.Hourly ->
            splitAtMissingDays(points).map { PlotSegment(s.scale, it) }
        else -> listOf(PlotSegment(s.scale, points))
    }
}
