package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** Pins the minute axis, zoom-adaptive label placement, and Hourly bucketing. */
class ChartTimeAxisTest {

    // US spring-forward day: 02:00-02:59 local does not exist.
    private val dstStart: LocalDate = LocalDate.of(2026, 3, 8)

    @Test
    fun minute_x_counts_local_wall_clock_minutes_from_range_start() {
        assertEquals(0, minuteX(dstStart.atStartOfDay(), dstStart))
        assertEquals(MINUTES_PER_DAY - 1, minuteX(LocalDateTime.of(2026, 3, 8, 23, 59), dstStart))
        assertEquals(MINUTES_PER_DAY, minuteX(LocalDateTime.of(2026, 3, 9, 0, 0), dstStart))
    }

    @Test
    fun minute_x_ignores_dst_gaps_so_midnight_stays_aligned() {
        // 03:30 on a 23-hour day is still 3.5 wall-clock hours in, not 2.5.
        assertEquals(210, minuteX(LocalDateTime.of(2026, 3, 8, 3, 30), dstStart))
        assertEquals(2 * MINUTES_PER_DAY, minuteX(LocalDateTime.of(2026, 3, 10, 0, 0), dstStart))
    }

    @Test
    fun day_points_sit_at_noon_of_their_day() {
        assertEquals(720, dayPointX(0))
        assertEquals(3 * MINUTES_PER_DAY + 720, dayPointX(3))
        assertEquals(3, minuteAxisPosition(dayPointX(3).toDouble()).dayIndex)
    }

    @Test
    fun minute_axis_position_splits_day_and_minute() {
        assertEquals(MinuteAxisPosition(1, 0), minuteAxisPosition(1440.0))
        assertEquals(MinuteAxisPosition(1, 720), minuteAxisPosition(2160.0))
        assertTrue(minuteAxisPosition(1440.0).isMidnight)
        assertFalse(minuteAxisPosition(2160.0).isMidnight)
    }

    @Test
    fun minute_axis_position_rounds_and_floors_negatives() {
        assertEquals(MinuteAxisPosition(1, 0), minuteAxisPosition(1439.6))
        assertEquals(MinuteAxisPosition(-1, 1380), minuteAxisPosition(-60.0))
    }

    @Test
    fun label_spacing_picks_finest_that_fits() {
        // 0.01 px per minute needs >= 10,000 minutes for a 100 px gap: one week.
        assertEquals(7 * MINUTES_PER_DAY, labelSpacing(MINUTE_AXIS_LABEL_SPACINGS, 0.01f, 100f))
        // 1 px per minute: 2 hours (120 px) is the first to clear 100 px.
        assertEquals(120, labelSpacing(MINUTE_AXIS_LABEL_SPACINGS, 1f, 100f))
        assertEquals(1, labelSpacing(HOUR_AXIS_LABEL_SPACINGS, 500f, 100f))
    }

    @Test
    fun label_spacing_falls_back_to_coarsest() {
        assertEquals(MINUTE_AXIS_LABEL_SPACINGS.last(), labelSpacing(MINUTE_AXIS_LABEL_SPACINGS, 0f, 1f))
        assertEquals(12, labelSpacing(HOUR_AXIS_LABEL_SPACINGS, 0.0001f, 100f))
    }

    @Test
    fun label_spacings_are_ascending() {
        assertEquals(MINUTE_AXIS_LABEL_SPACINGS.sorted(), MINUTE_AXIS_LABEL_SPACINGS)
        assertEquals(HOUR_AXIS_LABEL_SPACINGS.sorted(), HOUR_AXIS_LABEL_SPACINGS)
    }

    @Test
    fun aligned_label_values_cover_visible_range_plus_overflow_clipped_to_bounds() {
        assertEquals(
            listOf(0.0, 360.0, 720.0, 1080.0),
            alignedLabelValues(360, visibleStart = 100.0, visibleEnd = 1000.0, minX = 0.0, maxX = 1440.0),
        )
        // Clipped: the multiple past the start would be negative.
        assertEquals(
            listOf(0.0, 720.0),
            alignedLabelValues(720, visibleStart = -50.0, visibleEnd = 700.0, minX = 0.0, maxX = 1440.0),
        )
        assertEquals(
            listOf(1440.0, 2880.0, 4320.0),
            alignedLabelValues(1440, visibleStart = 2000.0, visibleEnd = 3000.0, minX = 0.0, maxX = 9e9),
        )
    }

    @Test
    fun visible_fraction_is_content_zoom_over_current_zoom() {
        assertEquals(1f, visibleFraction(0f, 0f), 1e-6f)
        assertEquals(0.25f, visibleFraction(0.5f, 2f), 1e-6f)
        assertEquals(1f, visibleFraction(1f, 0.5f), 1e-6f)
    }

    @Test
    fun hourly_means_average_per_hour_and_skip_empty_hours() {
        val points = hourlyMeans(listOf(9 to 4f, 9 to 6f, 21 to 1f))
        assertEquals(listOf(HourPoint(9, 5f), HourPoint(21, 1f)), points)
        assertTrue(hourlyMeans(emptyList()).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun hourly_means_rejects_out_of_range_hour() {
        hourlyMeans(listOf(24 to 1f))
    }

    @Test
    fun plot_points_map_each_mode_onto_its_axis() {
        val series = ScaleSeries(
            scale = Scale(name = "test", minValue = 1, maxValue = 10, colorArgb = 0),
            rolling = listOf(DayPoint(1, 4f)),
            daily = listOf(DayPoint(0, 3f)),
            entries = listOf(EntryPoint(95, 2f), EntryPoint(800, 4f)),
            hourly = listOf(HourPoint(13, 6f)),
        )
        assertEquals(listOf(PlotPoint(720, 3f)), plotPoints(series, ChartMode.Raw, showEntries = false))
        assertEquals(
            listOf(PlotPoint(95, 2f), PlotPoint(800, 4f)),
            plotPoints(series, ChartMode.Raw, showEntries = true),
        )
        // Only Raw swaps to entries; the average keeps its per-day points when zoomed.
        assertEquals(
            listOf(PlotPoint(2160, 4f)),
            plotPoints(series, ChartMode.RollingAvg, showEntries = true),
        )
        assertEquals(listOf(PlotPoint(13, 6f)), plotPoints(series, ChartMode.Hourly, showEntries = true))
    }
}
