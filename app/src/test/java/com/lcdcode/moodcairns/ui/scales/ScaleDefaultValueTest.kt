package com.lcdcode.moodcairns.ui.scales

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the optional default-slider-value field: its sanitizer (signed decimals,
 * unlike min/max which are integers and step which is unsigned), its step-grid
 * check, and the validation save() runs before snapshotting the scale.
 */
class ScaleDefaultValueTest {

    @Test
    fun sanitize_keeps_signed_decimals() {
        assertEquals("-5.5", sanitizeSignedDecimal("-5.5", maxLen = 7))
        assertEquals("7", sanitizeSignedDecimal("7", maxLen = 7))
        assertEquals("", sanitizeSignedDecimal("", maxLen = 7))
    }

    @Test
    fun sanitize_drops_interior_minus_and_extra_dots() {
        assertEquals("56", sanitizeSignedDecimal("5-6", maxLen = 7))
        assertEquals("-5.5", sanitizeSignedDecimal("--5.5", maxLen = 7))
        assertEquals("1.23", sanitizeSignedDecimal("1.2.3", maxLen = 7))
        assertEquals("", sanitizeSignedDecimal("abc", maxLen = 7))
    }

    @Test
    fun sanitize_accepts_comma_as_decimal_point() {
        assertEquals("2.5", sanitizeSignedDecimal("2,5", maxLen = 7))
        assertEquals("-0.25", sanitizeSignedDecimal("-0,25", maxLen = 7))
        // Only the first separator, comma or point, is kept.
        assertEquals("1.50", sanitizeDecimal("1,5,0", maxLen = 5))
        assertEquals("1.50", sanitizeDecimal("1.5,0", maxLen = 5))
    }

    @Test
    fun sanitize_caps_length_counting_the_sign() {
        assertEquals("1234", sanitizeSignedDecimal("12345", maxLen = 4))
        assertEquals("-123", sanitizeSignedDecimal("-12345", maxLen = 4))
    }

    @Test
    fun is_on_step_grid_accepts_multiples_only() {
        assertTrue(isOnStepGrid(0f, 0.5f))
        assertTrue(isOnStepGrid(1.5f, 0.5f))
        assertFalse(isOnStepGrid(0.3f, 0.5f))
    }

    @Test
    fun is_on_step_grid_tolerates_inexact_float_steps() {
        // 0.9f / 0.3f is 2.9999998; an exact comparison would reject it.
        assertTrue(isOnStepGrid(0.9f, 0.3f))
    }

    @Test
    fun is_on_step_grid_rejects_non_positive_step() {
        assertFalse(isOnStepGrid(1f, 0f))
        assertFalse(isOnStepGrid(1f, -1f))
    }

    @Test
    fun parse_treats_blank_as_no_default() {
        assertNull(parseDefaultValue(""))
        assertNull(parseDefaultValue("   "))
        assertEquals(6f, parseDefaultValue("6"))
        assertEquals(-2.5f, parseDefaultValue(" -2.5 "))
    }

    @Test
    fun validate_blank_is_allowed() {
        assertNull(defaultValueError("", min = 1, max = 10, step = 1f))
        assertNull(defaultValueError("  ", min = 1, max = 10, step = 1f))
    }

    @Test
    fun validate_accepts_on_grid_values_in_range() {
        assertNull(defaultValueError("6", min = 1, max = 10, step = 1f))
        assertNull(defaultValueError("1", min = 1, max = 10, step = 1f))
        assertNull(defaultValueError("10", min = 1, max = 10, step = 1f))
        assertNull(defaultValueError("-2.5", min = -5, max = 5, step = 0.5f))
        assertNull(defaultValueError("-7", min = -10, max = -1, step = 1f))
    }

    @Test
    fun validate_rejects_out_of_range() {
        val expected = UiText.Res(R.string.scale_edit_error_default_out_of_range, listOf(1, 10))
        assertEquals(expected, defaultValueError("0", min = 1, max = 10, step = 1f))
        assertEquals(expected, defaultValueError("11", min = 1, max = 10, step = 1f))
    }

    @Test
    fun validate_rejects_off_grid() {
        assertEquals(
            UiText.Res(R.string.scale_edit_error_default_off_step, listOf("1", 1)),
            defaultValueError("5.5", min = 1, max = 10, step = 1f),
        )
        assertEquals(
            UiText.Res(R.string.scale_edit_error_default_off_step, listOf("0.5", 0)),
            defaultValueError("2.3", min = 0, max = 10, step = 0.5f),
        )
    }

    @Test
    fun validate_rejects_non_numeric() {
        // The sanitizer keeps a lone "-", which is not yet a number.
        assertEquals(
            UiText.Res(R.string.scale_edit_error_default_not_number),
            defaultValueError("-", min = -5, max = 5, step = 1f),
        )
    }

    private fun assertEquals(expected: Float, actual: Float?) {
        assertNotNull(actual)
        org.junit.Assert.assertEquals(expected, actual!!, 1e-6f)
    }
}
