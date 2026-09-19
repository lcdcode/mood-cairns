package com.lcdcode.moodcairns.ui.scales

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
    fun sanitize_keepsSignedDecimals() {
        assertEquals("-5.5", sanitizeSignedDecimal("-5.5", maxLen = 7))
        assertEquals("7", sanitizeSignedDecimal("7", maxLen = 7))
        assertEquals("", sanitizeSignedDecimal("", maxLen = 7))
    }

    @Test
    fun sanitize_dropsInteriorMinusAndExtraDots() {
        assertEquals("56", sanitizeSignedDecimal("5-6", maxLen = 7))
        assertEquals("-5.5", sanitizeSignedDecimal("--5.5", maxLen = 7))
        assertEquals("1.23", sanitizeSignedDecimal("1.2.3", maxLen = 7))
        assertEquals("", sanitizeSignedDecimal("abc", maxLen = 7))
    }

    @Test
    fun sanitize_capsLength_countingTheSign() {
        assertEquals("1234", sanitizeSignedDecimal("12345", maxLen = 4))
        assertEquals("-123", sanitizeSignedDecimal("-12345", maxLen = 4))
    }

    @Test
    fun isOnStepGrid_acceptsMultiplesOnly() {
        assertTrue(isOnStepGrid(0f, 0.5f))
        assertTrue(isOnStepGrid(1.5f, 0.5f))
        assertFalse(isOnStepGrid(0.3f, 0.5f))
    }

    @Test
    fun isOnStepGrid_toleratesInexactFloatSteps() {
        // 0.9f / 0.3f is 2.9999998; an exact comparison would reject it.
        assertTrue(isOnStepGrid(0.9f, 0.3f))
    }

    @Test
    fun isOnStepGrid_rejectsNonPositiveStep() {
        assertFalse(isOnStepGrid(1f, 0f))
        assertFalse(isOnStepGrid(1f, -1f))
    }

    @Test
    fun parse_treatsBlankAsNoDefault() {
        assertNull(parseDefaultValue(""))
        assertNull(parseDefaultValue("   "))
        assertEquals(6f, parseDefaultValue("6"))
        assertEquals(-2.5f, parseDefaultValue(" -2.5 "))
    }

    @Test
    fun validate_blankIsAllowed() {
        assertNull(defaultValueError("", min = 1, max = 10, step = 1f))
        assertNull(defaultValueError("  ", min = 1, max = 10, step = 1f))
    }

    @Test
    fun validate_acceptsOnGridValuesInRange() {
        assertNull(defaultValueError("6", min = 1, max = 10, step = 1f))
        assertNull(defaultValueError("1", min = 1, max = 10, step = 1f))
        assertNull(defaultValueError("10", min = 1, max = 10, step = 1f))
        assertNull(defaultValueError("-2.5", min = -5, max = 5, step = 0.5f))
        assertNull(defaultValueError("-7", min = -10, max = -1, step = 1f))
    }

    @Test
    fun validate_rejectsOutOfRange() {
        assertNotNull(defaultValueError("0", min = 1, max = 10, step = 1f))
        assertNotNull(defaultValueError("11", min = 1, max = 10, step = 1f))
    }

    @Test
    fun validate_rejectsOffGrid() {
        assertNotNull(defaultValueError("5.5", min = 1, max = 10, step = 1f))
        assertNotNull(defaultValueError("2.3", min = 0, max = 10, step = 0.5f))
    }

    @Test
    fun validate_rejectsNonNumeric() {
        // The sanitizer keeps a lone "-", which is not yet a number.
        assertNotNull(defaultValueError("-", min = -5, max = 5, step = 1f))
    }

    private fun assertEquals(expected: Float, actual: Float?) {
        assertNotNull(actual)
        org.junit.Assert.assertEquals(expected, actual!!, 1e-6f)
    }
}
