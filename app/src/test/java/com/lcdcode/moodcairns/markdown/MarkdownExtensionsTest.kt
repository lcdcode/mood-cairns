package com.lcdcode.moodcairns.markdown

import com.lcdcode.moodcairns.markdown.MdBlock.ListBlock
import com.lcdcode.moodcairns.markdown.MdBlock.Paragraph
import com.lcdcode.moodcairns.markdown.MdBlock.Quote
import com.lcdcode.moodcairns.markdown.MdBlock.Table
import com.lcdcode.moodcairns.markdown.MdInline.Bold
import com.lcdcode.moodcairns.markdown.MdInline.Strikethrough
import com.lcdcode.moodcairns.markdown.MdInline.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Tables, checklists and strikethrough: the GitHub-flavoured additions to CommonMark. */
class MarkdownExtensionsTest {

    private fun cell(text: String) = listOf(Text(text))

    // Checklists

    @Test
    fun checklist_items_carry_their_state_and_marker_position() {
        val source = "- [ ] buy milk\n- [x] call mom"
        val list = MarkdownParser.parse(source).single() as ListBlock
        assertEquals(MdTask(checked = false, markerOffset = 3), list.items[0].task)
        assertEquals(MdTask(checked = true, markerOffset = 18), list.items[1].task)
        assertEquals('x', source[18])
    }

    @Test
    fun checkbox_text_is_removed_from_the_item_content() {
        val list = MarkdownParser.parse("- [ ] buy milk").single() as ListBlock
        assertEquals(listOf(Paragraph(listOf(Text("buy milk")))), list.items.single().blocks)
    }

    @Test
    fun uppercase_x_counts_as_checked() {
        val list = MarkdownParser.parse("- [X] done").single() as ListBlock
        assertEquals(true, list.items.single().task?.checked)
    }

    @Test
    fun numbered_checklist_items_are_found_too() {
        val list = MarkdownParser.parse("1. [ ] first").single() as ListBlock
        assertEquals(MdTask(checked = false, markerOffset = 4), list.items.single().task)
    }

    @Test
    fun checklist_inside_a_quote_points_at_the_right_character() {
        val source = "> - [ ] quoted"
        val quote = MarkdownParser.parse(source).single() as Quote
        val task = (quote.blocks.single() as ListBlock).items.single().task!!
        assertEquals(' ', source[task.markerOffset])
        assertEquals('[', source[task.markerOffset - 1])
    }

    @Test
    fun ordinary_list_items_have_no_task() {
        val list = MarkdownParser.parse("- [link](u)\n- plain").single() as ListBlock
        assertNull(list.items[0].task)
        assertNull(list.items[1].task)
    }

    @Test
    fun marker_offset_is_unknown_when_no_checkbox_follows_the_item_start() {
        assertEquals(MarkdownParser.UNKNOWN_OFFSET, taskMarkerOffset("- plain", 0))
        assertEquals(MarkdownParser.UNKNOWN_OFFSET, taskMarkerOffset("- [?] odd", 0))
        assertEquals(MarkdownParser.UNKNOWN_OFFSET, taskMarkerOffset("abc", 99))
    }

    // Tables

    @Test
    fun table_keeps_header_alignment_and_rows() {
        val table = MarkdownParser.parse("| a | b | c |\n|:--|:-:|--:|\n| 1 | **2** | 3 |")
            .single() as Table
        assertEquals(
            listOf(MdAlignment.LEFT, MdAlignment.CENTER, MdAlignment.RIGHT),
            table.alignments,
        )
        assertEquals(listOf(cell("a"), cell("b"), cell("c")), table.header)
        assertEquals(
            listOf(listOf(cell("1"), listOf(Bold(listOf(Text("2")))), cell("3"))),
            table.rows,
        )
    }

    @Test
    fun columns_without_alignment_are_none() {
        val table = MarkdownParser.parse("| a |\n|---|").single() as Table
        assertEquals(listOf(MdAlignment.NONE), table.alignments)
        assertEquals(emptyList<List<List<MdInline>>>(), table.rows)
    }

    @Test
    fun ragged_rows_are_padded_or_trimmed_to_the_header_width() {
        val table = MarkdownParser.parse("| a | b |\n|---|---|\n| 1 |\n| 1 | 2 | 3 |")
            .single() as Table
        assertEquals(listOf(cell("1"), emptyList()), table.rows[0])
        assertEquals(listOf(cell("1"), cell("2")), table.rows[1])
    }

    @Test
    fun fitrow_pads_and_trims() {
        assertEquals(listOf(listOf(1), emptyList()), fitRow(listOf(listOf(1)), 2))
        assertEquals(listOf(listOf(1)), fitRow(listOf(listOf(1), listOf(2)), 1))
    }

    // Strikethrough

    @Test
    fun double_tilde_strikes_through() {
        assertEquals(
            listOf(Paragraph(listOf(Text("not "), Strikethrough(listOf(Text("this")))))),
            MarkdownParser.parse("not ~~this~~"),
        )
    }

    @Test
    fun single_tilde_stays_literal_text() {
        assertEquals(
            listOf(Paragraph(listOf(Text("about ~5 minutes~ ago")))),
            MarkdownParser.parse("about ~5 minutes~ ago"),
        )
    }
}
