package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTest {

    @Test
    fun ordinary_text_passes_through_unchanged() {
        val message = "open failed: EACCES (Permission denied)"
        assertEquals(message, Diagnostic.of(message).text)
        assertEquals("Сбой записи 😀", Diagnostic.of("Сбой записи 😀").text)
    }

    @Test
    fun line_breaks_and_tabs_are_escaped_so_a_value_cannot_start_a_new_log_line() {
        assertEquals("a\\nFAKE b\\r\\tc", Diagnostic.of("a\nFAKE b\r\tc").text)
    }

    @Test
    fun control_characters_are_escaped() {
        assertEquals("x\\u0000y\\u001Bz\\u007F", Diagnostic.of("x\u0000y\u001Bz\u007F").text)
    }

    @Test
    fun bidi_overrides_and_other_format_characters_are_escaped() {
        assertEquals("abc\\u202Edef\\u200B", Diagnostic.of("abc\u202Edef\u200B").text)
        assertEquals("tag\\U000E0041", Diagnostic.of("tag\uDB40\uDC41").text)
    }

    @Test
    fun line_and_paragraph_separators_are_escaped() {
        assertEquals("a\\u2028b\\u2029c", Diagnostic.of("a\u2028b\u2029c").text)
    }

    @Test
    fun an_unpaired_surrogate_is_escaped_instead_of_producing_broken_utf_16() {
        assertEquals("a\\uD800b", Diagnostic.of("a\uD800b").text)
        assertEquals("a\\uDC00", Diagnostic.of("a\uDC00").text)
    }

    @Test
    fun oversized_text_is_cut_to_the_bound_with_an_ellipsis() {
        val text = Diagnostic.of("x".repeat(65_547)).text
        assertEquals(Diagnostic.MAX_CHARS + 1, text.length)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun text_exactly_at_the_bound_keeps_every_character_and_no_ellipsis() {
        val atLimit = "x".repeat(Diagnostic.MAX_CHARS)
        assertEquals(atLimit, Diagnostic.of(atLimit).text)
    }

    @Test
    fun the_cut_never_splits_a_surrogate_pair_or_an_escape() {
        val emoji = "😀".repeat(Diagnostic.MAX_CHARS)
        val cut = Diagnostic.of(emoji).text.removeSuffix("…")
        assertFalse(Character.isHighSurrogate(cut.last()))

        val newlines = "\n".repeat(Diagnostic.MAX_CHARS)
        val escaped = Diagnostic.of(newlines).text.removeSuffix("…")
        assertTrue(escaped.chunked(2).all { it == "\\n" })
    }

    @Test
    fun a_throwable_without_a_message_falls_back_to_its_type() {
        assertEquals("IllegalStateException", Diagnostic.of(IllegalStateException()).text)
        assertEquals("disk\\nfull", Diagnostic.of(IllegalStateException("disk\nfull")).text)
    }

    @Test
    fun tostring_is_the_sanitized_text_so_string_templates_stay_safe() {
        assertEquals("a\\nb", "${Diagnostic.of("a\nb")}")
    }
}
