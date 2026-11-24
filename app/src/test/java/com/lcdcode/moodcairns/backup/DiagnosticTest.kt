package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTest {

    @Test
    fun `ordinary text passes through unchanged`() {
        val message = "open failed: EACCES (Permission denied)"
        assertEquals(message, Diagnostic.of(message).text)
        assertEquals("Сбой записи 😀", Diagnostic.of("Сбой записи 😀").text)
    }

    @Test
    fun `line breaks and tabs are escaped so a value cannot start a new log line`() {
        assertEquals("a\\nFAKE b\\r\\tc", Diagnostic.of("a\nFAKE b\r\tc").text)
    }

    @Test
    fun `control characters are escaped`() {
        assertEquals("x\\u0000y\\u001Bz\\u007F", Diagnostic.of("x\u0000y\u001Bz\u007F").text)
    }

    @Test
    fun `bidi overrides and other format characters are escaped`() {
        assertEquals("abc\\u202Edef\\u200B", Diagnostic.of("abc\u202Edef\u200B").text)
        assertEquals("tag\\U000E0041", Diagnostic.of("tag\uDB40\uDC41").text)
    }

    @Test
    fun `line and paragraph separators are escaped`() {
        assertEquals("a\\u2028b\\u2029c", Diagnostic.of("a\u2028b\u2029c").text)
    }

    @Test
    fun `an unpaired surrogate is escaped instead of producing broken UTF-16`() {
        assertEquals("a\\uD800b", Diagnostic.of("a\uD800b").text)
        assertEquals("a\\uDC00", Diagnostic.of("a\uDC00").text)
    }

    @Test
    fun `oversized text is cut to the bound with an ellipsis`() {
        val text = Diagnostic.of("x".repeat(65_547)).text
        assertEquals(Diagnostic.MAX_CHARS + 1, text.length)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun `text exactly at the bound keeps every character and no ellipsis`() {
        val atLimit = "x".repeat(Diagnostic.MAX_CHARS)
        assertEquals(atLimit, Diagnostic.of(atLimit).text)
    }

    @Test
    fun `the cut never splits a surrogate pair or an escape`() {
        val emoji = "😀".repeat(Diagnostic.MAX_CHARS)
        val cut = Diagnostic.of(emoji).text.removeSuffix("…")
        assertFalse(Character.isHighSurrogate(cut.last()))

        val newlines = "\n".repeat(Diagnostic.MAX_CHARS)
        val escaped = Diagnostic.of(newlines).text.removeSuffix("…")
        assertTrue(escaped.chunked(2).all { it == "\\n" })
    }

    @Test
    fun `a throwable without a message falls back to its type`() {
        assertEquals("IllegalStateException", Diagnostic.of(IllegalStateException()).text)
        assertEquals("disk\\nfull", Diagnostic.of(IllegalStateException("disk\nfull")).text)
    }

    @Test
    fun `toString is the sanitized text so string templates stay safe`() {
        assertEquals("a\\nb", "${Diagnostic.of("a\nb")}")
    }
}
