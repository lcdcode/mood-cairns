package com.lcdcode.moodcairns.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownPlainTextTest {

    private fun preview(markdown: String, maxChars: Int = LIST_PREVIEW_CHARS) =
        markdownPreview(markdown, maxChars)

    // Plain previews

    @Test
    fun headings_quotes_and_list_markers_are_removed() {
        assertEquals(
            "Monday Felt good walked home 1st stop",
            preview("# Monday\n> Felt good\n- walked home\n1. 1st stop"),
        )
    }

    @Test
    fun checklist_boxes_are_removed() {
        assertEquals("milk eggs", preview("- [ ] milk\n- [x] eggs"))
    }

    @Test
    fun inline_markup_is_removed() {
        assertEquals(
            "bold italic both gone code",
            preview("**bold** *italic* ***both*** ~~gone~~ `code`"),
        )
    }

    @Test
    fun links_and_images_keep_only_their_text() {
        assertEquals(
            "see site and a cat",
            preview("see [site](https://x.y) and ![a cat](c.png)"),
        )
    }

    @Test
    fun tables_keep_their_cells_and_drop_the_divider_row() {
        assertEquals(
            "Mood · Sleep good · 8h",
            preview("| Mood | Sleep |\n| :-- | --: |\n| good | 8h |\n|  |  |"),
        )
    }

    @Test
    fun single_dash_dividers_are_dropped_too() {
        assertEquals("a · b 1 · 2", preview("|a|b|\n|-|:-:|\n|1|2|"))
    }

    @Test
    fun code_fences_and_thematic_breaks_are_dropped_code_kept() {
        assertEquals("before x = 1 after", preview("before\n```\nx = 1\n```\n---\nafter"))
    }

    @Test
    fun whitespace_is_collapsed() {
        assertEquals("a b c", preview("  a\n\n\n   b\tc  "))
    }

    @Test
    fun long_text_is_cut_with_an_ellipsis() {
        assertEquals("abcde…", preview("abcdefghij", 5))
    }

    @Test
    fun cutting_never_splits_an_emoji() {
        assertEquals("abcd…", preview("abcd😀efgh", 5))
    }

    @Test
    fun cyrillic_passes_through() {
        assertEquals("Сегодня хорошо", preview("## Сегодня **хорошо**"))
    }

    @Test
    fun empty_and_syntax_only_pages_give_empty_previews() {
        assertEquals("", preview(""))
        assertEquals("", preview("---\n```\n```"))
    }

    @Test
    fun characters_that_are_not_markup_pass_through_untouched() {
        assertEquals("a \u0002hit\u0003 b", markdownPlainText("a **\u0002hit\u0003** b"))
    }

    private companion object {
        const val LIST_PREVIEW_CHARS = 160
    }
}
