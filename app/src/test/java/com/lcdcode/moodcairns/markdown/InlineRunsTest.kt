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
    fun `empty input gives no runs`() {
        assertEquals(emptyList<StyledRun>(), flattenInlines(emptyList()))
    }

    @Test
    fun `plain text is one unstyled run`() {
        assertEquals(listOf(StyledRun("hello")), flattenInlines(listOf(Text("hello"))))
    }

    @Test
    fun `nesting accumulates styles`() {
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
    fun `neighbours with the same style merge`() {
        assertEquals(
            listOf(StyledRun("ab", bold = true)),
            flattenInlines(listOf(Bold(listOf(Text("a"))), Bold(listOf(Text("b"))))),
        )
    }

    @Test
    fun `strikethrough combines with other styles`() {
        assertEquals(
            listOf(StyledRun("x", italic = true, strikethrough = true)),
            flattenInlines(listOf(MdInline.Strikethrough(listOf(Italic(listOf(Text("x"))))))),
        )
    }

    @Test
    fun `code inside bold keeps both`() {
        assertEquals(
            listOf(StyledRun("x", bold = true, code = true)),
            flattenInlines(listOf(Bold(listOf(Code("x"))))),
        )
    }

    @Test
    fun `link children carry the destination`() {
        assertEquals(
            listOf(
                StyledRun("see ", linkDestination = "u"),
                StyledRun("this", bold = true, linkDestination = "u"),
            ),
            flattenInlines(listOf(Link("u", listOf(Text("see "), Bold(listOf(Text("this"))))))),
        )
    }

    @Test
    fun `soft and hard breaks become newlines in the surrounding run`() {
        assertEquals(
            listOf(StyledRun("a\nb\nc")),
            flattenInlines(listOf(Text("a"), SoftBreak, Text("b"), HardBreak, Text("c"))),
        )
    }
}
