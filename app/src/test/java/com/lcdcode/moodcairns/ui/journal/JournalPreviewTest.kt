package com.lcdcode.moodcairns.ui.journal

import com.lcdcode.moodcairns.data.dao.JournalSql.MATCH_END
import com.lcdcode.moodcairns.data.dao.JournalSql.MATCH_START
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalPreviewTest {

    // Search snippets

    @Test
    fun `snippet markers become highlight ranges`() {
        val preview = snippetPreview("went to the ${MATCH_START}park${MATCH_END} today")
        assertEquals("went to the park today", preview.text)
        assertEquals(listOf(12..15), preview.highlights)
        assertEquals("park", preview.text.substring(12, 16))
    }

    @Test
    fun `several hits are all highlighted`() {
        val preview =
            snippetPreview("${MATCH_START}rain${MATCH_END} then ${MATCH_START}sun${MATCH_END}")
        assertEquals("rain then sun", preview.text)
        assertEquals(listOf(0..3, 10..12), preview.highlights)
    }

    @Test
    fun `markdown around a hit is removed and the range follows the text`() {
        val preview = snippetPreview("- [ ] buy **${MATCH_START}milk${MATCH_END}**")
        assertEquals("buy milk", preview.text)
        assertEquals(listOf(4..7), preview.highlights)
    }

    @Test
    fun `an unclosed marker highlights to the end`() {
        val preview = snippetPreview("…a ${MATCH_START}walk")
        assertEquals("…a walk", preview.text)
        assertEquals(listOf(3..6), preview.highlights)
    }

    @Test
    fun `empty markers add no range`() {
        assertEquals(emptyList<IntRange>(), snippetPreview("a $MATCH_START$MATCH_END b").highlights)
    }
}
