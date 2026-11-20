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
    fun `bold wraps the selection`() {
        assertEquals(
            EditResult("hello **world**", 8, 13),
            inline("hello world", 6, 11, InlineMarker.BOLD),
        )
    }

    @Test
    fun `reversed selection behaves the same`() {
        assertEquals(
            EditResult("hello **world**", 8, 13),
            inline("hello world", 11, 6, InlineMarker.BOLD),
        )
    }

    @Test
    fun `bold unwraps when markers sit just outside the selection`() {
        assertEquals(
            EditResult("hello world", 6, 11),
            inline("hello **world**", 8, 13, InlineMarker.BOLD),
        )
    }

    @Test
    fun `bold unwraps when the selection includes the markers`() {
        assertEquals(
            EditResult("hello world", 6, 11),
            inline("hello **world**", 6, 15, InlineMarker.BOLD),
        )
    }

    @Test
    fun `surrounding whitespace stays outside the markers`() {
        assertEquals(
            EditResult("hello **world** ", 8, 13),
            inline("hello world ", 6, 12, InlineMarker.BOLD),
        )
    }

    @Test
    fun `caret inside a word wraps the word`() {
        assertEquals(
            EditResult("hello **world**", 8, 13),
            inline("hello world", 8, 8, InlineMarker.BOLD),
        )
    }

    @Test
    fun `caret inside a bold word unwraps it`() {
        assertEquals(
            EditResult("hello world", 6, 11),
            inline("hello **world**", 10, 10, InlineMarker.BOLD),
        )
    }

    @Test
    fun `caret outside a word inserts an empty pair`() {
        assertEquals(EditResult("hello ****", 8, 8), inline("hello ", 6, 6, InlineMarker.BOLD))
    }

    @Test
    fun `tapping again on an empty pair removes it`() {
        assertEquals(EditResult("hello ", 6, 6), inline("hello ****", 8, 8, InlineMarker.BOLD))
    }

    @Test
    fun `whitespace-only selection inserts a pair at its start`() {
        assertEquals(EditResult("a**   b", 2, 2), inline("a   b", 1, 4, InlineMarker.ITALIC))
    }

    @Test
    fun `italic on bold text adds italic instead of stripping bold`() {
        assertEquals(EditResult("***word***", 3, 7), inline("**word**", 2, 6, InlineMarker.ITALIC))
    }

    @Test
    fun `italic removal from bold italic keeps bold`() {
        assertEquals(EditResult("**word**", 2, 6), inline("***word***", 3, 7, InlineMarker.ITALIC))
    }

    @Test
    fun `bold removal from bold italic keeps italic`() {
        assertEquals(EditResult("*word*", 1, 5), inline("***word***", 3, 7, InlineMarker.BOLD))
    }

    @Test
    fun `code wraps in backticks`() {
        assertEquals(
            EditResult("run `ls` now", 5, 7),
            inline("run ls now", 4, 6, InlineMarker.CODE),
        )
    }

    @Test
    fun `out of range selection is clamped`() {
        assertEquals(EditResult("**ab**", 2, 4), inline("ab", -5, 99, InlineMarker.BOLD))
    }

    // Line prefixes

    @Test
    fun `bullet on the caret line moves the caret past the marker`() {
        assertEquals(EditResult("- milk", 4, 4), line("milk", 2, 2, LinePrefix.BULLET))
    }

    @Test
    fun `bullet on an empty document`() {
        assertEquals(EditResult("- ", 2, 2), line("", 0, 0, LinePrefix.BULLET))
    }

    @Test
    fun `bullet applies to every selected line`() {
        assertEquals(
            EditResult("- milk\n- eggs", 2, 13),
            line("milk\neggs", 0, 9, LinePrefix.BULLET),
        )
    }

    @Test
    fun `bullet is removed when every selected line has one`() {
        assertEquals(
            EditResult("milk\neggs", 0, 9),
            line("- milk\n- eggs", 0, 13, LinePrefix.BULLET),
        )
    }

    @Test
    fun `numbered converts bullets and renumbers from one`() {
        assertEquals("1. a\n2. b\n3. c", line("- a\n- b\nc", 0, 9, LinePrefix.NUMBERED).text)
    }

    @Test
    fun `numbered is removed when every selected line is numbered`() {
        assertEquals("a\nb", line("1. a\n7) b", 0, 9, LinePrefix.NUMBERED).text)
    }

    @Test
    fun `blank lines in a selection are skipped`() {
        assertEquals("- a\n\n- b", line("a\n\nb", 0, 4, LinePrefix.BULLET).text)
    }

    @Test
    fun `selection ending right after a newline excludes the next line`() {
        assertEquals("- a\nb", line("a\nb", 0, 2, LinePrefix.BULLET).text)
    }

    @Test
    fun `lines outside the selection are untouched`() {
        assertEquals(EditResult("x\n- y\nz", 4, 4), line("x\ny\nz", 2, 2, LinePrefix.BULLET))
    }

    @Test
    fun `indentation is preserved`() {
        assertEquals("  - a", line("  a", 3, 3, LinePrefix.BULLET).text)
        assertEquals("  a", line("  - a", 5, 5, LinePrefix.BULLET).text)
    }

    @Test
    fun `quote is layered over a list and removed again`() {
        val quoted = line("- a", 3, 3, LinePrefix.QUOTE)
        assertEquals(EditResult("> - a", 5, 5), quoted)
        assertEquals(
            EditResult("- a", 3, 3),
            line(quoted.text, quoted.selStart, quoted.selEnd, LinePrefix.QUOTE),
        )
    }

    @Test
    fun `heading cycles through three levels then off`() {
        var result = EditResult("Title", 0, 0)
        val seen = (1..4).map {
            result = line(result.text, result.selStart, result.selEnd, LinePrefix.HEADING)
            result.text
        }
        assertEquals(listOf("# Title", "## Title", "### Title", "Title"), seen)
    }

    @Test
    fun `heading replaces a list marker`() {
        assertEquals("# a", line("- a", 3, 3, LinePrefix.HEADING).text)
    }

    @Test
    fun `caret inside an old marker lands after the new one`() {
        assertEquals(EditResult("1. a", 3, 3), line("- a", 1, 1, LinePrefix.NUMBERED))
    }

    // Checklists

    @Test
    fun `checklist adds an unchecked box`() {
        assertEquals(EditResult("- [ ] milk", 6, 6), line("milk", 0, 0, LinePrefix.TASK))
    }

    @Test
    fun `checklist is removed when every line is a checklist item`() {
        assertEquals("a\nb", line("- [ ] a\n- [x] b", 0, 15, LinePrefix.TASK).text)
    }

    @Test
    fun `checklist keeps existing ticks when converting mixed lines`() {
        assertEquals("- [x] a\n- [ ] b", line("- [x] a\nb", 0, 9, LinePrefix.TASK).text)
    }

    @Test
    fun `bullet turns a checklist item into a plain bullet`() {
        assertEquals("- a", line("- [ ] a", 7, 7, LinePrefix.BULLET).text)
    }

    @Test
    fun `toggling a box flips between empty and x`() {
        assertEquals("- [x] a", toggleTaskAt("- [ ] a", 3))
        assertEquals("- [ ] a", toggleTaskAt("- [x] a", 3))
        assertEquals("- [ ] a", toggleTaskAt("- [X] a", 3))
    }

    @Test
    fun `toggling a stale offset does nothing`() {
        assertEquals(null, toggleTaskAt("- [ ] a", 0))
        assertEquals(null, toggleTaskAt("- a", 2))
        assertEquals(null, toggleTaskAt("- [ ] a", 99))
        assertEquals(null, toggleTaskAt("[?]", 1))
    }

    @Test
    fun `parser offsets toggle exactly the tapped item`() {
        val source = "- [ ] a\n- [ ] b"
        val list = MarkdownParser.parse(source).single() as MdBlock.ListBlock
        val toggled = toggleTaskAt(source, list.items[1].task!!.markerOffset)
        assertEquals("- [ ] a\n- [x] b", toggled)
    }

    // Tables

    private val headers = listOf("Column 1", "Column 2")
    private val template = "| Column 1 | Column 2 |\n| --- | --- |\n|  |  |"

    @Test
    fun `table in an empty page selects the first header`() {
        assertEquals(EditResult(template, 2, 10), insertTableTemplate("", 0, 0, headers))
    }

    @Test
    fun `table after text on the same line starts after a blank line`() {
        val result = insertTableTemplate("intro", 5, 5, headers)
        assertEquals("intro\n\n$template", result.text)
        assertEquals("Column 1", result.text.substring(result.selStart, result.selEnd))
    }

    @Test
    fun `table between lines is set off by blank lines and parses as a table`() {
        val result = insertTableTemplate("a\nb", 1, 1, headers)
        assertEquals("a\n\n$template\n\nb", result.text)
        val blocks = MarkdownParser.parse(result.text)
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is MdBlock.Table)
    }

    @Test
    fun `table is inserted after a selection, not over it`() {
        val result = insertTableTemplate("keep me", 0, 7, headers)
        assertTrue(result.text.startsWith("keep me\n\n|"))
    }
}
