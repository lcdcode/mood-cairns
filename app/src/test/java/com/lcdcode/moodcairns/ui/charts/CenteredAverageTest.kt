package com.lcdcode.moodcairns.ui.charts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the centered triangular-kernel average behind ChartMode.RollingAvg. */
class CenteredAverageTest {

    private fun average(
        sums: List<Float>,
        counts: List<Int>,
        halfWindow: Int = ROLLING_AVERAGE_HALF_WINDOW,
        padding: Int = 0,
    ) = centeredWeightedAverage(sums.toFloatArray(), counts.toIntArray(), halfWindow, padding)

    @Test
    fun windowIsOdd_soItCanCenter() {
        assertEquals(1, ROLLING_AVERAGE_DAYS % 2)
        assertEquals(ROLLING_AVERAGE_DAYS, 2 * ROLLING_AVERAGE_HALF_WINDOW + 1)
    }

    @Test
    fun spike_peaksOnItsOwnDay_andFallsOffSymmetrically() {
        val spikeDay = 7
        val sums = List(15) { if (it == spikeDay) 10f else 0f }
        val points = average(sums, List(15) { 1 }).associate { it.dayIndex to it.value }

        // Weights 1,2,3,4,3,2,1 sum to 16; the spike carries the center weight of 4.
        assertEquals(40f / 16f, points.getValue(spikeDay), 1e-6f)
        assertEquals(points.maxOf { it.value }, points.getValue(spikeDay), 1e-6f)
        for (distance in 1..ROLLING_AVERAGE_HALF_WINDOW) {
            assertEquals(
                points.getValue(spikeDay - distance),
                points.getValue(spikeDay + distance),
                1e-6f,
            )
            assertTrue(points.getValue(spikeDay - distance) < points.getValue(spikeDay - distance + 1))
        }
        // Outside the window the spike has no influence at all.
        assertEquals(0f, points.getValue(spikeDay - ROLLING_AVERAGE_HALF_WINDOW - 1), 1e-6f)
        assertEquals(0f, points.getValue(spikeDay + ROLLING_AVERAGE_HALF_WINDOW + 1), 1e-6f)
    }

    @Test
    fun flatSeries_staysFlat() {
        val points = average(List(10) { 5f }, List(10) { 1 })
        assertEquals(10, points.size)
        points.forEach { assertEquals(5f, it.value, 1e-6f) }
    }

    @Test
    fun weightsPerEntry_notPerDay() {
        // Middle day: three entries averaging 10. Per-entry: (2*30) / (1 + 2*3 + 1) = 7.5.
        // A mean of daily means would give (2*10) / 4 = 5.
        val points = average(listOf(0f, 30f, 0f), listOf(1, 3, 1), halfWindow = 1)
        assertEquals(7.5f, points.single { it.dayIndex == 1 }.value, 1e-6f)
    }

    @Test
    fun paddingDays_feedEdgeAverages_butGetNoPoints() {
        val points = average(
            sums = listOf(10f, 0f, 0f, 0f, 10f),
            counts = listOf(1, 1, 1, 1, 1),
            halfWindow = 1,
            padding = 1,
        )
        assertEquals(listOf(0, 1, 2), points.map { it.dayIndex })
        assertEquals(10f / 4f, points.first().value, 1e-6f)
        assertEquals(0f, points[1].value, 1e-6f)
        assertEquals(10f / 4f, points.last().value, 1e-6f)
    }

    @Test
    fun windowWithoutEntries_emitsNoPoint() {
        val points = average(List(9) { 0f }, listOf(1, 0, 0, 0, 0, 0, 0, 0, 1), halfWindow = 1)
        assertEquals(listOf(0, 1, 7, 8), points.map { it.dayIndex })
    }

    @Test
    fun emptyDayInsideWindow_stillGetsASmoothedPoint() {
        val points = average(listOf(4f, 0f, 8f), listOf(1, 0, 1), halfWindow = 1)
        assertEquals(6f, points.single { it.dayIndex == 1 }.value, 1e-6f)
    }

    @Test
    fun lastDay_usesOnlyThePastSideOfTheWindow() {
        // Weights 1,2,3,4 over the last four days: (4 * 8) / 10.
        val points = average(listOf(0f, 0f, 0f, 8f), listOf(1, 1, 1, 1))
        assertEquals(3.2f, points.last().value, 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedArrays_areRejected() {
        average(listOf(1f, 2f), listOf(1))
    }
}
