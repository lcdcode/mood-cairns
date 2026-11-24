package com.lcdcode.moodcairns.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TextLimitsTest {

    private val smile = "😀" // one emoji, two UTF-16 chars

    @Test
    fun short_text_is_unchanged() {
        assertEquals("abc", "abc".takeWholeChars(5))
    }

    @Test
    fun text_is_cut_at_the_limit() {
        assertEquals("ab", "abcdef".takeWholeChars(2))
    }

    @Test
    fun an_emoji_straddling_the_limit_is_dropped_whole() {
        assertEquals("ab", "ab$smile".takeWholeChars(3))
    }

    @Test
    fun an_emoji_ending_exactly_at_the_limit_is_kept() {
        assertEquals("ab$smile", "ab${smile}c".takeWholeChars(4))
    }

    @Test
    fun zero_or_negative_limits_give_empty_text() {
        assertEquals("", "abc".takeWholeChars(0))
        assertEquals("", "abc".takeWholeChars(-1))
    }
}
