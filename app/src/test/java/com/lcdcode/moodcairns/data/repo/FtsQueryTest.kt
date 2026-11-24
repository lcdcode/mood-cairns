package com.lcdcode.moodcairns.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

class FtsQueryTest {

    @Test
    fun blank_input_gives_an_empty_query() {
        assertEquals("", buildFtsQuery(""))
        assertEquals("", buildFtsQuery("   \n\t"))
        assertEquals("", buildFtsQuery("*** \"\" ()"))
    }

    @Test
    fun each_term_becomes_a_quoted_prefix_query() {
        assertEquals("\"walk*\" \"park*\"", buildFtsQuery("walk park"))
    }

    @Test
    fun punctuation_separates_terms_like_the_tokenizer() {
        assertEquals("\"don*\" \"t*\" \"stop*\"", buildFtsQuery("don't, stop!"))
    }

    @Test
    fun operators_and_syntax_are_neutralized() {
        assertEquals("\"rain*\" \"OR*\" \"sun*\"", buildFtsQuery("rain OR sun"))
        assertEquals("\"NOT*\" \"a*\"", buildFtsQuery("\"NOT\" (a"))
        assertEquals("\"body*\" \"x*\"", buildFtsQuery("body:x*"))
        assertEquals("\"a*\" \"NEAR*\" \"3*\" \"b*\"", buildFtsQuery("a NEAR/3 b"))
    }

    @Test
    fun non_latin_scripts_and_diacritics_are_kept_whole() {
        assertEquals("\"хорошо*\" \"día*\"", buildFtsQuery("хорошо día"))
        assertEquals("\"café*\"", buildFtsQuery("café"))
    }

    @Test
    fun term_count_is_capped() {
        val raw = (1..MAX_QUERY_TERMS + 5).joinToString(" ") { "w$it" }
        assertEquals(MAX_QUERY_TERMS, buildFtsQuery(raw).split(' ').size)
    }
}
