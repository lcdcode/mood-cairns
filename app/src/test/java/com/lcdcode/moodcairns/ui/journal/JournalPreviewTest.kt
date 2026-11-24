package com.lcdcode.moodcairns.ui.journal

import com.lcdcode.moodcairns.data.dao.JournalSql.MATCH_END
import com.lcdcode.moodcairns.data.dao.JournalSql.MATCH_START
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalPreviewTest {

    // Search snippets

    @Test
    fun snippet_markers_become_highlight_ranges() {
        val preview = snippetPreview("went to the ${MATCH_START}park${MATCH_END} today")
        assertEquals("went to the park today", preview.text)
        assertEquals(listOf(12..15), preview.highlights)
        assertEquals("park", preview.text.substring(12, 16))
    }

    @Test
    fun several_hits_are_all_highlighted() {
        val preview =
            snippetPreview("${MATCH_START}rain${MATCH_END} then ${MATCH_START}sun${MATCH_END}")
        assertEquals("rain then sun", preview.text)
        assertEquals(listOf(0..3, 10..12), preview.highlights)
    }

    @Test
    fun markdown_around_a_hit_is_removed_and_the_range_follows_the_text() {
        val preview = snippetPreview("- [ ] buy **${MATCH_START}milk${MATCH_END}**")
        assertEquals("buy milk", preview.text)
        assertEquals(listOf(4..7), preview.highlights)
    }

    @Test
    fun an_unclosed_marker_highlights_to_the_end() {
        val preview = snippetPreview("…a ${MATCH_START}walk")
        assertEquals("…a walk", preview.text)
        assertEquals(listOf(3..6), preview.highlights)
    }

    @Test
    fun empty_markers_add_no_range() {
        assertEquals(emptyList<IntRange>(), snippetPreview("a $MATCH_START$MATCH_END b").highlights)
    }
}
