package com.lcdcode.moodcairns.ui.journal

import com.lcdcode.moodcairns.data.dao.JournalSql.MATCH_END
import com.lcdcode.moodcairns.data.dao.JournalSql.MATCH_START
import org.junit.Assert.assertEquals
import org.junit.Test

class JournalPreviewTest {

    // Plain previews

    @Test
    fun `headings, quotes and list markers are removed`() {
        assertEquals(
            "Monday Felt good walked home 1st stop",
            journalPreview("# Monday\n> Felt good\n- walked home\n1. 1st stop"),
        )
    }

    @Test
    fun `checklist boxes are removed`() {
        assertEquals("milk eggs", journalPreview("- [ ] milk\n- [x] eggs"))
    }

    @Test
    fun `inline markup is removed`() {
        assertEquals(
            "bold italic both gone code",
            journalPreview("**bold** *italic* ***both*** ~~gone~~ `code`"),
        )
    }

    @Test
    fun `links and images keep only their text`() {
        assertEquals(
            "see site and a cat",
            journalPreview("see [site](https://x.y) and ![a cat](c.png)"),
        )
    }

    @Test
    fun `tables keep their cells and drop the divider row`() {
        assertEquals(
            "Mood · Sleep good · 8h",
            journalPreview("| Mood | Sleep |\n| :-- | --: |\n| good | 8h |\n|  |  |"),
        )
    }

    @Test
    fun `single dash dividers are dropped too`() {
        assertEquals("a · b 1 · 2", journalPreview("|a|b|\n|-|:-:|\n|1|2|"))
    }

    @Test
    fun `code fences and thematic breaks are dropped, code kept`() {
        assertEquals("before x = 1 after", journalPreview("before\n```\nx = 1\n```\n---\nafter"))
    }

    @Test
    fun `whitespace is collapsed`() {
        assertEquals("a b c", journalPreview("  a\n\n\n   b\tc  "))
    }

    @Test
    fun `long text is cut with an ellipsis`() {
        assertEquals("abcde…", journalPreview("abcdefghij", maxChars = 5))
    }

    @Test
    fun `cutting never splits an emoji`() {
        assertEquals("abcd…", journalPreview("abcd😀efgh", maxChars = 5))
    }

    @Test
    fun `cyrillic passes through`() {
        assertEquals("Сегодня хорошо", journalPreview("## Сегодня **хорошо**"))
    }

    @Test
    fun `empty and syntax-only pages give empty previews`() {
        assertEquals("", journalPreview(""))
        assertEquals("", journalPreview("---\n```\n```"))
    }

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
