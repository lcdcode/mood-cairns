package com.lcdcode.moodcairns.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `blank input gives an empty query`() {
        assertEquals("", buildFtsQuery(""))
        assertEquals("", buildFtsQuery("   \n\t"))
        assertEquals("", buildFtsQuery("*** \"\" ()"))
    }

    @Test
    fun `each term becomes a quoted prefix query`() {
        assertEquals("\"walk*\" \"park*\"", buildFtsQuery("walk park"))
    }

    @Test
    fun `punctuation separates terms like the tokenizer`() {
        assertEquals("\"don*\" \"t*\" \"stop*\"", buildFtsQuery("don't, stop!"))
    }

    @Test
    fun `operators and syntax are neutralized`() {
        assertEquals("\"rain*\" \"OR*\" \"sun*\"", buildFtsQuery("rain OR sun"))
        assertEquals("\"NOT*\" \"a*\"", buildFtsQuery("\"NOT\" (a"))
        assertEquals("\"body*\" \"x*\"", buildFtsQuery("body:x*"))
        assertEquals("\"a*\" \"NEAR*\" \"3*\" \"b*\"", buildFtsQuery("a NEAR/3 b"))
    }

    @Test
    fun `non latin scripts and diacritics are kept whole`() {
        assertEquals("\"хорошо*\" \"día*\"", buildFtsQuery("хорошо día"))
        assertEquals("\"café*\"", buildFtsQuery("café"))
    }

    @Test
    fun `term count is capped`() {
        val raw = (1..MAX_QUERY_TERMS + 5).joinToString(" ") { "w$it" }
        assertEquals(MAX_QUERY_TERMS, buildFtsQuery(raw).split(' ').size)
    }
}
