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
    fun newScale_neverPrompts() {
        assertFalse(invertsDirection(base = null, edited = scale(inverted = true)))
    }

    @Test
    fun unchangedDirection_doesNotPrompt() {
        assertFalse(invertsDirection(base = scale(false), edited = scale(false)))
        assertFalse(invertsDirection(base = scale(true), edited = scale(true)))
    }

    @Test
    fun flippedDirection_prompts_bothWays() {
        assertTrue(invertsDirection(base = scale(false), edited = scale(true)))
        assertTrue(invertsDirection(base = scale(true), edited = scale(false)))
    }
}
