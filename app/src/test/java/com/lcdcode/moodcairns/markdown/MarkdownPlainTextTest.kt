package com.lcdcode.moodcairns.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPlainTextTest {

    private fun preview(markdown: String, maxChars: Int = LIST_PREVIEW_CHARS) =
        markdownPreview(markdown, maxChars)

    // Plain previews

    @Test
    fun `headings, quotes and list markers are removed`() {
        assertEquals(
            "Monday Felt good walked home 1st stop",
            preview("# Monday\n> Felt good\n- walked home\n1. 1st stop"),
        )
    }

    @Test
    fun `checklist boxes are removed`() {
        assertEquals("milk eggs", preview("- [ ] milk\n- [x] eggs"))
    }

    @Test
    fun `inline markup is removed`() {
        assertEquals(
            "bold italic both gone code",
            preview("**bold** *italic* ***both*** ~~gone~~ `code`"),
        )
    }

    @Test
    fun `links and images keep only their text`() {
        assertEquals(
            "see site and a cat",
            preview("see [site](https://x.y) and ![a cat](c.png)"),
        )
    }

    @Test
    fun `tables keep their cells and drop the divider row`() {
        assertEquals(
            "Mood · Sleep good · 8h",
            preview("| Mood | Sleep |\n| :-- | --: |\n| good | 8h |\n|  |  |"),
        )
    }

    @Test
    fun `single dash dividers are dropped too`() {
        assertEquals("a · b 1 · 2", preview("|a|b|\n|-|:-:|\n|1|2|"))
    }

    @Test
    fun `code fences and thematic breaks are dropped, code kept`() {
        assertEquals("before x = 1 after", preview("before\n```\nx = 1\n```\n---\nafter"))
    }

    @Test
    fun `whitespace is collapsed`() {
        assertEquals("a b c", preview("  a\n\n\n   b\tc  "))
    }

    @Test
    fun `long text is cut with an ellipsis`() {
        assertEquals("abcde…", preview("abcdefghij", 5))
    }

    @Test
    fun `cutting never splits an emoji`() {
        assertEquals("abcd…", preview("abcd😀efgh", 5))
    }

    @Test
    fun `cyrillic passes through`() {
        assertEquals("Сегодня хорошо", preview("## Сегодня **хорошо**"))
    }

    @Test
    fun `empty and syntax-only pages give empty previews`() {
        assertEquals("", preview(""))
        assertEquals("", preview("---\n```\n```"))
    }

    @Test
    fun `characters that are not markup pass through untouched`() {
        assertEquals("a \u0002hit\u0003 b", markdownPlainText("a **\u0002hit\u0003** b"))
    }

    private companion object {
        const val LIST_PREVIEW_CHARS = 160
    }
}
