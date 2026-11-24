package com.lcdcode.moodcairns.ui.scales

import com.lcdcode.moodcairns.data.entity.Scale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the save-time decision that gates the remap prompt. */
class InvertsDirectionTest {

    private fun scale(inverted: Boolean) =
        Scale(id = 1, name = "test", minValue = 1, maxValue = 10, colorArgb = 0, inverted = inverted)

    @Test
    fun new_scale_never_prompts() {
        assertFalse(invertsDirection(base = null, edited = scale(inverted = true)))
    }

    @Test
    fun unchanged_direction_does_not_prompt() {
        assertFalse(invertsDirection(base = scale(false), edited = scale(false)))
        assertFalse(invertsDirection(base = scale(true), edited = scale(true)))
    }

    @Test
    fun flipped_direction_prompts_both_ways() {
        assertTrue(invertsDirection(base = scale(false), edited = scale(true)))
        assertTrue(invertsDirection(base = scale(true), edited = scale(false)))
    }
}
