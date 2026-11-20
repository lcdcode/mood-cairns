package com.lcdcode.moodcairns.markdown

import com.lcdcode.moodcairns.markdown.MdBlock.CodeBlock
import com.lcdcode.moodcairns.markdown.MdBlock.Heading
import com.lcdcode.moodcairns.markdown.MdBlock.ListBlock
import com.lcdcode.moodcairns.markdown.MdBlock.Paragraph
import com.lcdcode.moodcairns.markdown.MdBlock.Quote
import com.lcdcode.moodcairns.markdown.MdInline.Bold
import com.lcdcode.moodcairns.markdown.MdInline.Code
import com.lcdcode.moodcairns.markdown.MdInline.HardBreak
import com.lcdcode.moodcairns.markdown.MdInline.Italic
import com.lcdcode.moodcairns.markdown.MdInline.Link
import com.lcdcode.moodcairns.markdown.MdInline.SoftBreak
import com.lcdcode.moodcairns.markdown.MdInline.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    private fun paragraph(vararg inlines: MdInline) = Paragraph(inlines.toList())

    @Test
    fun `empty and blank input produce no blocks`() {
        assertEquals(emptyList<MdBlock>(), MarkdownParser.parse(""))
        assertEquals(emptyList<MdBlock>(), MarkdownParser.parse("  \n\n "))
    }

    @Test
    fun `headings keep their level`() {
        assertEquals(
            listOf(Heading(1, listOf(Text("One"))), Heading(3, listOf(Text("Three")))),
            MarkdownParser.parse("# One\n### Three"),
        )
    }

    @Test
    fun `nested emphasis maps to bold and italic`() {
        assertEquals(
            listOf(
                paragraph(
                    Text("a "),
                    Bold(listOf(Text("b "), Italic(listOf(Text("c"))))),
                    Text(" d"),
                ),
            ),
            MarkdownParser.parse("a **b *c*** d"),
        )
    }

    @Test
    fun `inline code keeps its literal`() {
        assertEquals(
            listOf(paragraph(Text("run "), Code("ls -la"))),
            MarkdownParser.parse("run `ls -la`"),
        )
    }

    @Test
    fun `bullet list items hold their blocks`() {
        assertEquals(
            listOf(
                ListBlock(
                    false,
                    1,
                    listOf(
                        MdListItem(listOf(paragraph(Text("one")))),
                        MdListItem(listOf(paragraph(Text("two")))),
                    ),
                ),
            ),
            MarkdownParser.parse("- one\n- two"),
        )
    }

    @Test
    fun `ordered list keeps its start number`() {
        val list = MarkdownParser.parse("3. a\n4. b").single() as ListBlock
        assertTrue(list.ordered)
        assertEquals(3, list.startNumber)
        assertEquals(2, list.items.size)
    }

    @Test
    fun `nested list sits inside its parent item`() {
        val outer = MarkdownParser.parse("- a\n  - b").single() as ListBlock
        assertEquals(
            listOf(
                paragraph(Text("a")),
                ListBlock(false, 1, listOf(MdListItem(listOf(paragraph(Text("b")))))),
            ),
            outer.items.single().blocks,
        )
    }

    @Test
    fun `block quote wraps its content`() {
        assertEquals(listOf(Quote(listOf(paragraph(Text("hi"))))), MarkdownParser.parse("> hi"))
    }

    @Test
    fun `fenced code block keeps language and drops trailing newline`() {
        assertEquals(
            listOf(CodeBlock("val x = 1\nval y = 2", "kotlin")),
            MarkdownParser.parse("```kotlin\nval x = 1\nval y = 2\n```"),
        )
    }

    @Test
    fun `indented code block has no language`() {
        assertEquals(listOf(CodeBlock("code", null)), MarkdownParser.parse("    code"))
    }

    @Test
    fun `link keeps destination and label`() {
        assertEquals(
            listOf(paragraph(Link("https://example.org", listOf(Text("site"))))),
            MarkdownParser.parse("[site](https://example.org)"),
        )
    }

    @Test
    fun `reference links resolve and their definitions are dropped`() {
        assertEquals(
            listOf(paragraph(Link("https://example.org", listOf(Text("x"))))),
            MarkdownParser.parse("[x]: https://example.org\n\n[x]"),
        )
    }

    @Test
    fun `images degrade to their alt text`() {
        assertEquals(listOf(paragraph(Text("a cat"))), MarkdownParser.parse("![a cat](cat.png)"))
    }

    @Test
    fun `html is shown literally, never interpreted`() {
        assertEquals(listOf(paragraph(Text("<div>x</div>"))), MarkdownParser.parse("<div>x</div>"))
        assertEquals(listOf(paragraph(Text("a <b>c</b>"))), MarkdownParser.parse("a <b>c</b>"))
    }

    @Test
    fun `line breaks are distinguished`() {
        assertEquals(
            listOf(paragraph(Text("a"), SoftBreak, Text("b"))),
            MarkdownParser.parse("a\nb"),
        )
        assertEquals(
            listOf(paragraph(Text("a"), HardBreak, Text("b"))),
            MarkdownParser.parse("a  \nb"),
        )
    }

    @Test
    fun `thematic break between paragraphs`() {
        assertEquals(
            listOf(paragraph(Text("a")), MdBlock.ThematicBreak, paragraph(Text("b"))),
            MarkdownParser.parse("a\n\n---\n\nb"),
        )
    }

    @Test
    fun `cyrillic text passes through`() {
        assertEquals(
            listOf(paragraph(Text("Сегодня "), Bold(listOf(Text("хорошо"))))),
            MarkdownParser.parse("Сегодня **хорошо**"),
        )
    }

    @Test(timeout = PATHOLOGICAL_TIMEOUT_MS)
    fun `deeply nested quotes are capped`() {
        val blocks = MarkdownParser.parse(">".repeat(PATHOLOGICAL_SIZE) + " x")
        assertTrue(blockDepth(blocks) <= MarkdownParser.MAX_NESTING_DEPTH)
    }

    @Test(timeout = PATHOLOGICAL_TIMEOUT_MS)
    fun `deeply nested lists are capped`() {
        val blocks = MarkdownParser.parse("- ".repeat(PATHOLOGICAL_SIZE) + "x")
        assertTrue(blockDepth(blocks) <= MarkdownParser.MAX_NESTING_DEPTH)
    }

    @Test(timeout = PATHOLOGICAL_TIMEOUT_MS)
    fun `deeply nested emphasis is capped`() {
        val n = PATHOLOGICAL_SIZE / 2
        val blocks = MarkdownParser.parse("*_".repeat(n) + "x" + "_*".repeat(n))
        val inlines = (blocks.single() as Paragraph).inlines
        assertTrue(inlineDepth(inlines) <= MarkdownParser.MAX_NESTING_DEPTH + 1)
    }

    @Test(timeout = PATHOLOGICAL_TIMEOUT_MS)
    fun `runs of markers and brackets terminate`() {
        MarkdownParser.parse("*".repeat(PATHOLOGICAL_SIZE))
        MarkdownParser.parse("[".repeat(PATHOLOGICAL_SIZE) + "x" + "](u)".repeat(PATHOLOGICAL_SIZE))
        MarkdownParser.parse("`".repeat(PATHOLOGICAL_SIZE))
    }

    private fun blockDepth(blocks: List<MdBlock>): Int = blocks.maxOfOrNull { block ->
        when (block) {
            is Quote -> 1 + blockDepth(block.blocks)
            is ListBlock -> 1 + (block.items.maxOfOrNull { blockDepth(it.blocks) } ?: 0)
            else -> 0
        }
    } ?: 0

    private fun inlineDepth(inlines: List<MdInline>): Int = inlines.maxOfOrNull { inline ->
        when (inline) {
            is Bold -> 1 + inlineDepth(inline.children)
            is Italic -> 1 + inlineDepth(inline.children)
            is Link -> 1 + inlineDepth(inline.children)
            else -> 0
        }
    } ?: 0

    private companion object {
        const val PATHOLOGICAL_SIZE = 10_000
        const val PATHOLOGICAL_TIMEOUT_MS = 10_000L
    }
}
