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

    @Test
    fun hourlyPoints_areOmittedForOncePerDayScales() {
        val samples = listOf(7 to 8f, 7 to 6f, 21 to 5f)

        assertEquals(
            listOf(HourPoint(7, 7f), HourPoint(21, 5f)),
            hourlyPointsFor(scale(1, 10), samples),
        )
        val sleep = scale(0, 12).copy(oncePerDay = true)
        assertEquals(emptyList<HourPoint>(), hourlyPointsFor(sleep, samples))
    }

    @Test
    fun answeredDays_areDaysWithAValueAboveTheMinimum_forOncePerDayScalesOnly() {
        val samples = listOf(0 to 0f, 0 to 7f, 1 to 0f, 2 to 6.5f)
        val sleep = scale(0, 12).copy(oncePerDay = true)

        assertEquals(setOf(0, 2), daysAnsweredAboveMinimum(sleep, samples))
        assertEquals(emptySet<Int>(), daysAnsweredAboveMinimum(scale(0, 12), samples))
    }

    @Test
    fun minimum_isSupersededOnlyOnAnAnsweredDay() {
        val sleep = scale(0, 12).copy(oncePerDay = true)
        val answered = setOf(0)

        assertEquals(true, isSupersededMinimum(0f, dayIndex = 0, sleep, answered))
        assertEquals(false, isSupersededMinimum(0f, dayIndex = 1, sleep, answered))
        assertEquals(false, isSupersededMinimum(7f, dayIndex = 0, sleep, answered))
    }

    @Test
    fun splitAtMissingDays_breaksOnlyWhereADayIsSkipped() {
        val day0 = PlotPoint(dayPointX(0), 7f)
        val day1Morning = PlotPoint(MINUTES_PER_DAY + 8 * MINUTES_PER_HOUR, 0f)
        val day1Evening = PlotPoint(MINUTES_PER_DAY + 20 * MINUTES_PER_HOUR, 6f)
        val day3 = PlotPoint(dayPointX(3), 8f)
        val day4 = PlotPoint(dayPointX(4), 7.5f)
        val day7 = PlotPoint(dayPointX(7), 5f)

        val runs = splitAtMissingDays(listOf(day0, day1Morning, day1Evening, day3, day4, day7))

        assertEquals(
            listOf(listOf(day0, day1Morning, day1Evening), listOf(day3, day4), listOf(day7)),
            runs,
        )
        assertEquals(emptyList<List<PlotPoint>>(), splitAtMissingDays(emptyList()))
    }

    @Test
    fun plotSegments_splitOncePerDayScalesOnly_andSkipEmptyOnes() {
        val gappy = listOf(DayPoint(0, 7f), DayPoint(2, 8f))
        val sleep = ScaleSeries(
            scale(0, 12).copy(id = 6, oncePerDay = true), rolling = gappy, daily = gappy,
        )
        val mood = ScaleSeries(scale(1, 10).copy(id = 1), rolling = gappy, daily = gappy)
        val empty = ScaleSeries(
            scale(1, 10).copy(id = 2), rolling = emptyList(), daily = emptyList(),
        )

        val segments = plotSegments(listOf(mood, sleep, empty), ChartMode.Raw, showEntries = false)

        assertEquals(listOf(1L, 6L, 6L), segments.map { it.scale.id })
        assertEquals(listOf(2, 1, 1), segments.map { it.points.size })
    }
}
