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
    fun empty_and_blank_input_produce_no_blocks() {
        assertEquals(emptyList<MdBlock>(), MarkdownParser.parse(""))
        assertEquals(emptyList<MdBlock>(), MarkdownParser.parse("  \n\n "))
    }

    @Test
    fun headings_keep_their_level() {
        assertEquals(
            listOf(Heading(1, listOf(Text("One"))), Heading(3, listOf(Text("Three")))),
            MarkdownParser.parse("# One\n### Three"),
        )
    }

    @Test
    fun nested_emphasis_maps_to_bold_and_italic() {
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
    fun inline_code_keeps_its_literal() {
        assertEquals(
            listOf(paragraph(Text("run "), Code("ls -la"))),
            MarkdownParser.parse("run `ls -la`"),
        )
    }

    @Test
    fun bullet_list_items_hold_their_blocks() {
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
    fun ordered_list_keeps_its_start_number() {
        val list = MarkdownParser.parse("3. a\n4. b").single() as ListBlock
        assertTrue(list.ordered)
        assertEquals(3, list.startNumber)
        assertEquals(2, list.items.size)
    }

    @Test
    fun nested_list_sits_inside_its_parent_item() {
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
    fun block_quote_wraps_its_content() {
        assertEquals(listOf(Quote(listOf(paragraph(Text("hi"))))), MarkdownParser.parse("> hi"))
    }

    @Test
    fun fenced_code_block_keeps_language_and_drops_trailing_newline() {
        assertEquals(
            listOf(CodeBlock("val x = 1\nval y = 2", "kotlin")),
            MarkdownParser.parse("```kotlin\nval x = 1\nval y = 2\n```"),
        )
    }

    @Test
    fun indented_code_block_has_no_language() {
        assertEquals(listOf(CodeBlock("code", null)), MarkdownParser.parse("    code"))
    }

    @Test
    fun link_keeps_destination_and_label() {
        assertEquals(
            listOf(paragraph(Link("https://example.org", listOf(Text("site"))))),
            MarkdownParser.parse("[site](https://example.org)"),
        )
    }

    @Test
    fun reference_links_resolve_and_their_definitions_are_dropped() {
        assertEquals(
            listOf(paragraph(Link("https://example.org", listOf(Text("x"))))),
            MarkdownParser.parse("[x]: https://example.org\n\n[x]"),
        )
    }

    @Test
    fun images_degrade_to_their_alt_text() {
        assertEquals(listOf(paragraph(Text("a cat"))), MarkdownParser.parse("![a cat](cat.png)"))
    }

    @Test
    fun html_is_shown_literally_never_interpreted() {
        assertEquals(listOf(paragraph(Text("<div>x</div>"))), MarkdownParser.parse("<div>x</div>"))
        assertEquals(listOf(paragraph(Text("a <b>c</b>"))), MarkdownParser.parse("a <b>c</b>"))
    }

    @Test
    fun line_breaks_are_distinguished() {
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
    fun thematic_break_between_paragraphs() {
        assertEquals(
            listOf(paragraph(Text("a")), MdBlock.ThematicBreak, paragraph(Text("b"))),
            MarkdownParser.parse("a\n\n---\n\nb"),
        )
    }

    @Test
    fun cyrillic_text_passes_through() {
        assertEquals(
            listOf(paragraph(Text("Сегодня "), Bold(listOf(Text("хорошо"))))),
            MarkdownParser.parse("Сегодня **хорошо**"),
        )
    }

    @Test(timeout = PATHOLOGICAL_TIMEOUT_MS)
    fun deeply_nested_quotes_are_capped() {
        val blocks = MarkdownParser.parse(">".repeat(PATHOLOGICAL_SIZE) + " x")
        assertTrue(blockDepth(blocks) <= MarkdownParser.MAX_NESTING_DEPTH)
    }

    @Test(timeout = PATHOLOGICAL_TIMEOUT_MS)
    fun deeply_nested_lists_are_capped() {
        val blocks = MarkdownParser.parse("- ".repeat(PATHOLOGICAL_SIZE) + "x")
        assertTrue(blockDepth(blocks) <= MarkdownParser.MAX_NESTING_DEPTH)
    }

    @Test(timeout = PATHOLOGICAL_TIMEOUT_MS)
    fun deeply_nested_emphasis_is_capped() {
        val n = PATHOLOGICAL_SIZE / 2
        val blocks = MarkdownParser.parse("*_".repeat(n) + "x" + "_*".repeat(n))
        val inlines = (blocks.single() as Paragraph).inlines
        assertTrue(inlineDepth(inlines) <= MarkdownParser.MAX_NESTING_DEPTH + 1)
    }

    @Test(timeout = PATHOLOGICAL_TIMEOUT_MS)
    fun runs_of_markers_and_brackets_terminate() {
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
