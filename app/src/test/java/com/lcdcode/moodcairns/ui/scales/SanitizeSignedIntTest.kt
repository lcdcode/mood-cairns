package com.lcdcode.moodcairns.ui.scales

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the min/max field sanitizer: leading minus only, digits, capped length. */
class SanitizeSignedIntTest {

    @Test
    fun plainDigits_passThrough() {
        assertEquals("10", sanitizeSignedInt("10", maxDigits = 4))
    }

    @Test
    fun leadingMinus_isKept() {
        assertEquals("-5", sanitizeSignedInt("-5", maxDigits = 4))
    }

    @Test
    fun interiorMinus_isDropped() {
        assertEquals("56", sanitizeSignedInt("5-6", maxDigits = 4))
    }

    @Test
    fun repeatedMinus_collapsesToOne() {
        assertEquals("-5", sanitizeSignedInt("--5", maxDigits = 4))
    }

    @Test
    fun nonNumericInput_isStripped() {
        assertEquals("", sanitizeSignedInt("abc", maxDigits = 4))
        assertEquals("-", sanitizeSignedInt("-", maxDigits = 4))
    }

    @Test
    fun digitCount_isCapped_signNotCounted() {
        assertEquals("12345".take(4), sanitizeSignedInt("123456", maxDigits = 4))
        assertEquals("-1234", sanitizeSignedInt("-123456", maxDigits = 4))
    }
}
