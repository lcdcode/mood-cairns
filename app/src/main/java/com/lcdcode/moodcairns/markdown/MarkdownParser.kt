package com.lcdcode.moodcairns.markdown

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.LinkReferenceDefinition
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

/**
 * Parses CommonMark into [MdBlock]s. HTML is never interpreted (it is shown literally) and
 * images are never loaded (only their alt text is kept), so journal content cannot reach
 * outside the app. Nesting is capped so hostile or accidental input such as thousands of
 * `>` characters cannot overflow the stack in the converter or the renderer.
 */
object MarkdownParser {

    internal const val MAX_NESTING_DEPTH = 16

    // Parser instances are immutable and safe to share across threads.
    private val parser: Parser = Parser.builder()
        .maxOpenBlockParsers(MAX_NESTING_DEPTH)
        .maxInlineNesting(MAX_NESTING_DEPTH)
        .build()

    fun parse(text: String): List<MdBlock> = blocksOf(parser.parse(text))

    private fun blocksOf(parent: Node): List<MdBlock> =
        childrenOf(parent).mapNotNull(::toBlock).toList()

    private fun toBlock(node: Node): MdBlock? = when (node) {
        is Heading -> MdBlock.Heading(node.level, inlinesOf(node))
        is Paragraph -> MdBlock.Paragraph(inlinesOf(node))
        is BlockQuote -> MdBlock.Quote(blocksOf(node))
        is BulletList -> MdBlock.ListBlock(ordered = false, startNumber = 1, listItems(node))
        is OrderedList -> MdBlock.ListBlock(
            ordered = true,
            startNumber = node.markerStartNumber ?: 1,
            items = listItems(node),
        )
        is FencedCodeBlock -> MdBlock.CodeBlock(
            text = node.literal.orEmpty().removeSuffix("\n"),
            language = node.info?.takeIf { it.isNotBlank() },
        )
        is IndentedCodeBlock -> MdBlock.CodeBlock(node.literal.orEmpty().removeSuffix("\n"), null)
        is HtmlBlock -> MdBlock.Paragraph(listOf(MdInline.Text(node.literal.orEmpty().trimEnd())))
        is ThematicBreak -> MdBlock.ThematicBreak
        is LinkReferenceDefinition -> null
        else -> plainTextOf(node).takeIf { it.isNotEmpty() }
            ?.let { MdBlock.Paragraph(listOf(MdInline.Text(it))) }
    }

    private fun listItems(list: Node): List<List<MdBlock>> =
        childrenOf(list).filterIsInstance<ListItem>().map(::blocksOf).toList()

    private fun inlinesOf(parent: Node): List<MdInline> =
        mergeAdjacentText(childrenOf(parent).mapNotNull(::toInline).toList())

    private fun toInline(node: Node): MdInline? = when (node) {
        is Text -> MdInline.Text(node.literal.orEmpty())
        is StrongEmphasis -> MdInline.Bold(inlinesOf(node))
        is Emphasis -> MdInline.Italic(inlinesOf(node))
        is Code -> MdInline.Code(node.literal.orEmpty())
        is Link -> MdInline.Link(node.destination.orEmpty(), inlinesOf(node))
        is Image -> MdInline.Text(plainTextOf(node))
        is HardLineBreak -> MdInline.HardBreak
        is SoftLineBreak -> MdInline.SoftBreak
        is HtmlInline -> MdInline.Text(node.literal.orEmpty())
        else -> MdInline.Text(plainTextOf(node))
    }

    private fun mergeAdjacentText(inlines: List<MdInline>): List<MdInline> {
        val merged = ArrayList<MdInline>(inlines.size)
        for (inline in inlines) {
            val previous = merged.lastOrNull()
            when {
                inline is MdInline.Text && inline.value.isEmpty() -> Unit
                inline is MdInline.Text && previous is MdInline.Text ->
                    merged[merged.lastIndex] = MdInline.Text(previous.value + inline.value)
                else -> merged += inline
            }
        }
        return merged
    }

    private fun childrenOf(parent: Node): Sequence<Node> =
        generateSequence(parent.firstChild) { it.next }

    /** Literal text of a subtree, iterative so arbitrarily deep trees cannot overflow. */
    private fun plainTextOf(root: Node): String {
        val sb = StringBuilder()
        val pending = ArrayDeque<Node>()
        childrenOf(root).toList().asReversed().forEach(pending::addLast)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            when (node) {
                is Text -> sb.append(node.literal)
                is Code -> sb.append(node.literal)
                is HtmlInline -> sb.append(node.literal)
                is SoftLineBreak, is HardLineBreak -> sb.append('\n')
            }
            childrenOf(node).toList().asReversed().forEach(pending::addLast)
        }
        return sb.toString()
    }
}
