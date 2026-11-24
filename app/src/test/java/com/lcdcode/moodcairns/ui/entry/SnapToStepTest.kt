package com.lcdcode.moodcairns.ui.entry

import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins slider snapping over negative and fully-negative ranges. */
class SnapToStepTest {

    private fun scale(min: Int, max: Int, step: Float = 1f, default: Float? = null) =
        Scale(
            name = "test", minValue = min, maxValue = max, step = step, colorArgb = 0,
            defaultValue = default,
        )

    @Test
    fun snaps_to_nearest_step_across_zero() {
        val s = scale(-5, 5)
        assertEquals(0f, snapToStep(-0.4f, s))
        assertEquals(-1f, snapToStep(-0.6f, s))
        assertEquals(-5f, snapToStep(-4.7f, s))
        assertEquals(3f, snapToStep(2.6f, s))
    }

    @Test
    fun coerces_to_range_ends() {
        val s = scale(-5, 5)
        assertEquals(-5f, snapToStep(-9f, s))
        assertEquals(5f, snapToStep(9f, s))
    }

    @Test
    fun fully_negative_range_snaps_relative_to_min() {
        val s = scale(-10, -1)
        assertEquals(-10f, snapToStep(-10.4f, s))
        assertEquals(-7f, snapToStep(-7.2f, s))
        assertEquals(-1f, snapToStep(-0.3f, s))
    }

    @Test
    fun fractional_step_stays_on_grid_from_min() {
        val s = scale(-2, 2, step = 0.5f)
        assertEquals(-1.5f, snapToStep(-1.6f, s))
        assertEquals(0.5f, snapToStep(0.6f, s))
    }

    @Test
    fun slider_steps_counts_intermediate_ticks() {
        assertEquals(8, sliderSteps(scale(1, 10)))
        assertEquals(9, sliderSteps(scale(-5, 5)))
        assertEquals(3, sliderSteps(scale(0, 10, step = 2.5f)))
    }

    @Test
    fun slider_steps_rounds_inexact_float_quotients() {
        // 3 / 0.3f is 9.9999996; truncation used to drop a tick (8 instead of 9).
        assertEquals(9, sliderSteps(scale(0, 3, step = 0.3f)))
    }

    @Test
    fun slider_steps_degenerate_inputs_yield_continuous_slider() {
        assertEquals(0, sliderSteps(scale(1, 2)))
        assertEquals(0, sliderSteps(scale(1, 10, step = 0f)))
    }

    @Test
    fun initial_slider_value_without_default_snaps_the_midpoint() {
        // The raw midpoint of 1..10 is 5.5, which is not a slider tick. Ties
        // land on the lower tick because snapToStep rounds half to even.
        assertEquals(5f, scale(1, 10).initialSliderValue())
        assertEquals(6f, scale(2, 11).initialSliderValue())
        assertEquals(5f, scale(0, 10).initialSliderValue())
        assertEquals(0f, scale(-5, 5).initialSliderValue())
    }

    @Test
    fun initial_slider_value_uses_configured_default() {
        assertEquals(3f, scale(1, 10, default = 3f).initialSliderValue())
        assertEquals(-2.5f, scale(-5, 5, step = 0.5f, default = -2.5f).initialSliderValue())
    }

    @Test
    fun initial_slider_value_snaps_and_clamps_a_stale_default() {
        // A default can fall off the grid or out of range after a range edit or
        // a hand-edited backup.
        assertEquals(3f, scale(1, 10, default = 3.4f).initialSliderValue())
        assertEquals(10f, scale(1, 10, default = 99f).initialSliderValue())
        assertEquals(1f, scale(1, 10, default = -99f).initialSliderValue())
    }

    private fun assertEquals(expected: Int, actual: Int) =
        org.junit.Assert.assertEquals(expected.toLong(), actual.toLong())

    private fun assertEquals(expected: Float, actual: Float) =
        assertEquals(expected, actual, 1e-6f)
}
