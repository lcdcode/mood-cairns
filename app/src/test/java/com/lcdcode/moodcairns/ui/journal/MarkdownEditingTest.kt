package com.lcdcode.moodcairns.ui.journal

import com.lcdcode.moodcairns.markdown.MarkdownParser
import com.lcdcode.moodcairns.markdown.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditingTest {

    private fun inline(text: String, start: Int, end: Int, marker: InlineMarker) =
        toggleInlineMarker(text, start, end, marker)

    private fun line(text: String, start: Int, end: Int, prefix: LinePrefix) =
        toggleLinePrefix(text, start, end, prefix)

    // Inline markers

    @Test
    fun bold_wraps_the_selection() {
        assertEquals(
            EditResult("hello **world**", 8, 13),
            inline("hello world", 6, 11, InlineMarker.BOLD),
        )
    }

    @Test
    fun reversed_selection_behaves_the_same() {
        assertEquals(
            EditResult("hello **world**", 8, 13),
            inline("hello world", 11, 6, InlineMarker.BOLD),
        )
    }

    @Test
    fun bold_unwraps_when_markers_sit_just_outside_the_selection() {
        assertEquals(
            EditResult("hello world", 6, 11),
            inline("hello **world**", 8, 13, InlineMarker.BOLD),
        )
    }

    @Test
    fun bold_unwraps_when_the_selection_includes_the_markers() {
        assertEquals(
            EditResult("hello world", 6, 11),
            inline("hello **world**", 6, 15, InlineMarker.BOLD),
        )
    }

    @Test
    fun surrounding_whitespace_stays_outside_the_markers() {
        assertEquals(
            EditResult("hello **world** ", 8, 13),
            inline("hello world ", 6, 12, InlineMarker.BOLD),
        )
    }

    @Test
    fun caret_inside_a_word_wraps_the_word() {
        assertEquals(
            EditResult("hello **world**", 8, 13),
            inline("hello world", 8, 8, InlineMarker.BOLD),
        )
    }

    @Test
    fun caret_inside_a_bold_word_unwraps_it() {
        assertEquals(
            EditResult("hello world", 6, 11),
            inline("hello **world**", 10, 10, InlineMarker.BOLD),
        )
    }

    @Test
    fun caret_outside_a_word_inserts_an_empty_pair() {
        assertEquals(EditResult("hello ****", 8, 8), inline("hello ", 6, 6, InlineMarker.BOLD))
    }

    @Test
    fun tapping_again_on_an_empty_pair_removes_it() {
        assertEquals(EditResult("hello ", 6, 6), inline("hello ****", 8, 8, InlineMarker.BOLD))
    }

    @Test
    fun whitespace_only_selection_inserts_a_pair_at_its_start() {
        assertEquals(EditResult("a**   b", 2, 2), inline("a   b", 1, 4, InlineMarker.ITALIC))
    }

    @Test
    fun italic_on_bold_text_adds_italic_instead_of_stripping_bold() {
        assertEquals(EditResult("***word***", 3, 7), inline("**word**", 2, 6, InlineMarker.ITALIC))
    }

    @Test
    fun italic_removal_from_bold_italic_keeps_bold() {
        assertEquals(EditResult("**word**", 2, 6), inline("***word***", 3, 7, InlineMarker.ITALIC))
    }

    @Test
    fun bold_removal_from_bold_italic_keeps_italic() {
        assertEquals(EditResult("*word*", 1, 5), inline("***word***", 3, 7, InlineMarker.BOLD))
    }

    @Test
    fun code_wraps_in_backticks() {
        assertEquals(
            EditResult("run `ls` now", 5, 7),
            inline("run ls now", 4, 6, InlineMarker.CODE),
        )
    }

    @Test
    fun out_of_range_selection_is_clamped() {
        assertEquals(EditResult("**ab**", 2, 4), inline("ab", -5, 99, InlineMarker.BOLD))
    }

    // Line prefixes

    @Test
    fun bullet_on_the_caret_line_moves_the_caret_past_the_marker() {
        assertEquals(EditResult("- milk", 4, 4), line("milk", 2, 2, LinePrefix.BULLET))
    }

    @Test
    fun bullet_on_an_empty_document() {
        assertEquals(EditResult("- ", 2, 2), line("", 0, 0, LinePrefix.BULLET))
    }

    @Test
    fun bullet_applies_to_every_selected_line() {
        assertEquals(
            EditResult("- milk\n- eggs", 2, 13),
            line("milk\neggs", 0, 9, LinePrefix.BULLET),
        )
    }

    @Test
    fun bullet_is_removed_when_every_selected_line_has_one() {
        assertEquals(
            EditResult("milk\neggs", 0, 9),
            line("- milk\n- eggs", 0, 13, LinePrefix.BULLET),
        )
    }

    @Test
    fun numbered_converts_bullets_and_renumbers_from_one() {
        assertEquals("1. a\n2. b\n3. c", line("- a\n- b\nc", 0, 9, LinePrefix.NUMBERED).text)
    }

    @Test
    fun numbered_is_removed_when_every_selected_line_is_numbered() {
        assertEquals("a\nb", line("1. a\n7) b", 0, 9, LinePrefix.NUMBERED).text)
    }

    @Test
    fun blank_lines_in_a_selection_are_skipped() {
        assertEquals("- a\n\n- b", line("a\n\nb", 0, 4, LinePrefix.BULLET).text)
    }

    @Test
    fun selection_ending_right_after_a_newline_excludes_the_next_line() {
        assertEquals("- a\nb", line("a\nb", 0, 2, LinePrefix.BULLET).text)
    }

    @Test
    fun lines_outside_the_selection_are_untouched() {
        assertEquals(EditResult("x\n- y\nz", 4, 4), line("x\ny\nz", 2, 2, LinePrefix.BULLET))
    }

    @Test
    fun indentation_is_preserved() {
        assertEquals("  - a", line("  a", 3, 3, LinePrefix.BULLET).text)
        assertEquals("  a", line("  - a", 5, 5, LinePrefix.BULLET).text)
    }

    @Test
    fun quote_is_layered_over_a_list_and_removed_again() {
        val quoted = line("- a", 3, 3, LinePrefix.QUOTE)
        assertEquals(EditResult("> - a", 5, 5), quoted)
        assertEquals(
            EditResult("- a", 3, 3),
            line(quoted.text, quoted.selStart, quoted.selEnd, LinePrefix.QUOTE),
        )
    }

    @Test
    fun heading_cycles_through_three_levels_then_off() {
        var result = EditResult("Title", 0, 0)
        val seen = (1..4).map {
            result = line(result.text, result.selStart, result.selEnd, LinePrefix.HEADING)
            result.text
        }
        assertEquals(listOf("# Title", "## Title", "### Title", "Title"), seen)
    }

    @Test
    fun heading_replaces_a_list_marker() {
        assertEquals("# a", line("- a", 3, 3, LinePrefix.HEADING).text)
    }

    @Test
    fun caret_inside_an_old_marker_lands_after_the_new_one() {
        assertEquals(EditResult("1. a", 3, 3), line("- a", 1, 1, LinePrefix.NUMBERED))
    }

    // Checklists

    @Test
    fun checklist_adds_an_unchecked_box() {
        assertEquals(EditResult("- [ ] milk", 6, 6), line("milk", 0, 0, LinePrefix.TASK))
    }

    @Test
    fun checklist_is_removed_when_every_line_is_a_checklist_item() {
        assertEquals("a\nb", line("- [ ] a\n- [x] b", 0, 15, LinePrefix.TASK).text)
    }

    @Test
    fun checklist_keeps_existing_ticks_when_converting_mixed_lines() {
        assertEquals("- [x] a\n- [ ] b", line("- [x] a\nb", 0, 9, LinePrefix.TASK).text)
    }

    @Test
    fun bullet_turns_a_checklist_item_into_a_plain_bullet() {
        assertEquals("- a", line("- [ ] a", 7, 7, LinePrefix.BULLET).text)
    }

    @Test
    fun toggling_a_box_flips_between_empty_and_x() {
        assertEquals("- [x] a", toggleTaskAt("- [ ] a", 3))
        assertEquals("- [ ] a", toggleTaskAt("- [x] a", 3))
        assertEquals("- [ ] a", toggleTaskAt("- [X] a", 3))
    }

    @Test
    fun toggling_a_stale_offset_does_nothing() {
        assertEquals(null, toggleTaskAt("- [ ] a", 0))
        assertEquals(null, toggleTaskAt("- a", 2))
        assertEquals(null, toggleTaskAt("- [ ] a", 99))
        assertEquals(null, toggleTaskAt("[?]", 1))
    }

    @Test
    fun parser_offsets_toggle_exactly_the_tapped_item() {
        val source = "- [ ] a\n- [ ] b"
        val list = MarkdownParser.parse(source).single() as MdBlock.ListBlock
        val toggled = toggleTaskAt(source, list.items[1].task!!.markerOffset)
        assertEquals("- [ ] a\n- [x] b", toggled)
    }

    // Tables

    private val headers = listOf("Column 1", "Column 2")
    private val template = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |"

    @Test
    fun table_in_an_empty_page_selects_the_first_header() {
        assertEquals(EditResult(template, 2, 10), insertTableTemplate("", 0, 0, headers))
    }

    @Test
    fun table_after_text_on_the_same_line_starts_after_a_blank_line() {
        val result = insertTableTemplate("intro", 5, 5, headers)
        assertEquals("intro\n\n$template", result.text)
        assertEquals("Column 1", result.text.substring(result.selStart, result.selEnd))
    }

    @Test
    fun table_between_lines_is_set_off_by_blank_lines_and_parses_as_a_table() {
        val result = insertTableTemplate("a\nb", 1, 1, headers)
        assertEquals("a\n\n$template\n\nb", result.text)
        val blocks = MarkdownParser.parse(result.text)
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is MdBlock.Table)
    }

    @Test
    fun table_is_inserted_after_a_selection_not_over_it() {
        val result = insertTableTemplate("keep me", 0, 7, headers)
        assertTrue(result.text.startsWith("keep me\n\n|"))
    }
}
