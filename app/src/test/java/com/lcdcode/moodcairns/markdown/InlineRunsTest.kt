package com.lcdcode.moodcairns.markdown

import com.lcdcode.moodcairns.markdown.MdInline.Bold
import com.lcdcode.moodcairns.markdown.MdInline.Code
import com.lcdcode.moodcairns.markdown.MdInline.HardBreak
import com.lcdcode.moodcairns.markdown.MdInline.Italic
import com.lcdcode.moodcairns.markdown.MdInline.Link
import com.lcdcode.moodcairns.markdown.MdInline.SoftBreak
import com.lcdcode.moodcairns.markdown.MdInline.Text
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineRunsTest {

    @Test
    fun empty_input_gives_no_runs() {
        assertEquals(emptyList<StyledRun>(), flattenInlines(emptyList()))
    }

    @Test
    fun plain_text_is_one_unstyled_run() {
        assertEquals(listOf(StyledRun("hello")), flattenInlines(listOf(Text("hello"))))
    }

    @Test
    fun nesting_accumulates_styles() {
        assertEquals(
            listOf(
                StyledRun("a"),
                StyledRun("b", bold = true),
                StyledRun("c", bold = true, italic = true),
            ),
            flattenInlines(listOf(Text("a"), Bold(listOf(Text("b"), Italic(listOf(Text("c"))))))),
        )
    }

    @Test
    fun neighbours_with_the_same_style_merge() {
        assertEquals(
            listOf(StyledRun("ab", bold = true)),
            flattenInlines(listOf(Bold(listOf(Text("a"))), Bold(listOf(Text("b"))))),
        )
    }

    @Test
    fun strikethrough_combines_with_other_styles() {
        assertEquals(
            listOf(StyledRun("x", italic = true, strikethrough = true)),
            flattenInlines(listOf(MdInline.Strikethrough(listOf(Italic(listOf(Text("x"))))))),
        )
    }

    @Test
    fun code_inside_bold_keeps_both() {
        assertEquals(
            listOf(StyledRun("x", bold = true, code = true)),
            flattenInlines(listOf(Bold(listOf(Code("x"))))),
        )
    }

    @Test
    fun link_children_carry_the_destination() {
        assertEquals(
            listOf(
                StyledRun("see ", linkDestination = "u"),
                StyledRun("this", bold = true, linkDestination = "u"),
            ),
            flattenInlines(listOf(Link("u", listOf(Text("see "), Bold(listOf(Text("this"))))))),
        )
    }

    @Test
    fun soft_and_hard_breaks_become_newlines_in_the_surrounding_run() {
        assertEquals(
            listOf(StyledRun("a\nb\nc")),
            flattenInlines(listOf(Text("a"), SoftBreak, Text("b"), HardBreak, Text("c"))),
        )
    }
}
