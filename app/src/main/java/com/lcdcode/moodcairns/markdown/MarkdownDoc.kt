package com.lcdcode.moodcairns.markdown

/**
 * The app's own Markdown document model. [MarkdownParser] maps the commonmark AST onto it,
 * so the Compose renderer and the tests never depend on commonmark types directly.
 * It covers only what the journal renders; anything else is degraded to plain text.
 */
sealed interface MdBlock {
    data class Heading(val level: Int, val inlines: List<MdInline>) : MdBlock
    data class Paragraph(val inlines: List<MdInline>) : MdBlock
    data class ListBlock(
        val ordered: Boolean,
        val startNumber: Int,
        val items: List<MdListItem>,
    ) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class CodeBlock(val text: String, val language: String?) : MdBlock
    data object ThematicBreak : MdBlock

    /** Every row, header included, has exactly one cell per entry in [alignments]. */
    data class Table(
        val alignments: List<MdAlignment>,
        val header: List<List<MdInline>>,
        val rows: List<List<List<MdInline>>>,
    ) : MdBlock
}

data class MdListItem(val blocks: List<MdBlock>, val task: MdTask? = null)

/**
 * A checklist item (`- [ ]` or `- [x]`). [markerOffset] is the index in the source text of
 * the character between the brackets, so the read view can flip it in place.
 */
data class MdTask(val checked: Boolean, val markerOffset: Int)

enum class MdAlignment { NONE, LEFT, CENTER, RIGHT }

sealed interface MdInline {
    data class Text(val value: String) : MdInline
    data class Bold(val children: List<MdInline>) : MdInline
    data class Italic(val children: List<MdInline>) : MdInline
    data class Strikethrough(val children: List<MdInline>) : MdInline
    data class Code(val value: String) : MdInline
    data class Link(val destination: String, val children: List<MdInline>) : MdInline
    data object HardBreak : MdInline
    data object SoftBreak : MdInline
}
