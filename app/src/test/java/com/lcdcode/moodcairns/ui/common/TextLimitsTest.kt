package com.lcdcode.moodcairns.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TextLimitsTest {

    private val smile = "😀" // one emoji, two UTF-16 chars

    @Test
    fun `short text is unchanged`() {
        assertEquals("abc", "abc".takeWholeChars(5))
    }

    @Test
    fun `text is cut at the limit`() {
        assertEquals("ab", "abcdef".takeWholeChars(2))
    }

    @Test
    fun `an emoji straddling the limit is dropped whole`() {
        assertEquals("ab", "ab$smile".takeWholeChars(3))
    }

    @Test
    fun `an emoji ending exactly at the limit is kept`() {
        assertEquals("ab$smile", "ab${smile}c".takeWholeChars(4))
    }

    @Test
    fun `zero or negative limits give empty text`() {
        assertEquals("", "abc".takeWholeChars(0))
        assertEquals("", "abc".takeWholeChars(-1))
    }
}
