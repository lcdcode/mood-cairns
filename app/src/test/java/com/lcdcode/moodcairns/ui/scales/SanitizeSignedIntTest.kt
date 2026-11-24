package com.lcdcode.moodcairns.ui.scales

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the min/max field sanitizer: leading minus only, digits, capped length. */
class SanitizeSignedIntTest {

    @Test
    fun plain_digits_pass_through() {
        assertEquals("10", sanitizeSignedInt("10", maxDigits = 4))
    }

    @Test
    fun leading_minus_is_kept() {
        assertEquals("-5", sanitizeSignedInt("-5", maxDigits = 4))
    }

    @Test
    fun interior_minus_is_dropped() {
        assertEquals("56", sanitizeSignedInt("5-6", maxDigits = 4))
    }

    @Test
    fun repeated_minus_collapses_to_one() {
        assertEquals("-5", sanitizeSignedInt("--5", maxDigits = 4))
    }

    @Test
    fun non_numeric_input_is_stripped() {
        assertEquals("", sanitizeSignedInt("abc", maxDigits = 4))
        assertEquals("-", sanitizeSignedInt("-", maxDigits = 4))
    }

    @Test
    fun digit_count_is_capped_sign_not_counted() {
        assertEquals("12345".take(4), sanitizeSignedInt("123456", maxDigits = 4))
        assertEquals("-1234", sanitizeSignedInt("-123456", maxDigits = 4))
    }
}
