package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins Absolute-mode normalization, including the inverse-scale flip. */
class ChartMathTest {

    private fun scale(min: Int, max: Int, inverted: Boolean = false) =
        Scale(name = "test", minValue = min, maxValue = max, colorArgb = 0, inverted = inverted)

    @Test
    fun normal_scale_maps_min_to_zero_max_to_one() {
        val s = scale(1, 10)
        assertEquals(0f, normalize(1f, s), 1e-6f)
        assertEquals(1f, normalize(10f, s), 1e-6f)
        assertEquals(0.5f, normalize(5.5f, s), 1e-6f)
    }

    @Test
    fun inverted_scale_maps_min_to_one_max_to_zero() {
        val s = scale(1, 10, inverted = true)
        assertEquals(1f, normalize(1f, s), 1e-6f)
        assertEquals(0f, normalize(10f, s), 1e-6f)
        assertEquals(0.5f, normalize(5.5f, s), 1e-6f)
    }

    @Test
    fun negative_range_midpoint_is_half() {
        val s = scale(-5, 5)
        assertEquals(0.5f, normalize(0f, s), 1e-6f)
        assertEquals(0f, normalize(-5f, s), 1e-6f)
    }

    @Test
    fun negative_inverted_range_min_is_best() {
        val s = scale(-5, 5, inverted = true)
        assertEquals(1f, normalize(-5f, s), 1e-6f)
        assertEquals(0f, normalize(5f, s), 1e-6f)
    }

    @Test
    fun out_of_range_values_clamp_before_flipping() {
        val s = scale(1, 10, inverted = true)
        assertEquals(1f, normalize(-3f, s), 1e-6f)
        assertEquals(0f, normalize(99f, s), 1e-6f)
    }

    @Test
    fun degenerate_span_centers_instead_of_dividing_by_zero() {
        assertEquals(0.5f, normalize(3f, scale(5, 5)), 1e-6f)
    }

    @Test
    fun hourly_points_are_omitted_for_once_per_day_scales() {
        val samples = listOf(7 to 8f, 7 to 6f, 21 to 5f)

        assertEquals(
            listOf(HourPoint(7, 7f), HourPoint(21, 5f)),
            hourlyPointsFor(scale(1, 10), samples),
        )
        val sleep = scale(0, 12).copy(oncePerDay = true)
        assertEquals(emptyList<HourPoint>(), hourlyPointsFor(sleep, samples))
    }

    @Test
    fun answered_days_are_days_with_a_value_above_the_minimum_for_once_per_day_scales_only() {
        val samples = listOf(0 to 0f, 0 to 7f, 1 to 0f, 2 to 6.5f)
        val sleep = scale(0, 12).copy(oncePerDay = true)

        assertEquals(setOf(0, 2), daysAnsweredAboveMinimum(sleep, samples))
        assertEquals(emptySet<Int>(), daysAnsweredAboveMinimum(scale(0, 12), samples))
    }

    @Test
    fun minimum_is_superseded_only_on_an_answered_day() {
        val sleep = scale(0, 12).copy(oncePerDay = true)
        val answered = setOf(0)

        assertEquals(true, isSupersededMinimum(0f, dayIndex = 0, sleep, answered))
        assertEquals(false, isSupersededMinimum(0f, dayIndex = 1, sleep, answered))
        assertEquals(false, isSupersededMinimum(7f, dayIndex = 0, sleep, answered))
    }

    @Test
    fun split_at_missing_days_breaks_only_where_a_day_is_skipped() {
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
    fun plot_segments_split_once_per_day_scales_only_and_skip_empty_ones() {
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
