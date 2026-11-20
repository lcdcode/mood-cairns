package com.lcdcode.moodcairns.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.markdown.MarkdownParser
import com.lcdcode.moodcairns.markdown.MdAlignment
import com.lcdcode.moodcairns.markdown.MdBlock
import com.lcdcode.moodcairns.markdown.MdInline
import com.lcdcode.moodcairns.markdown.MdListItem
import com.lcdcode.moodcairns.markdown.MdTask
import com.lcdcode.moodcairns.markdown.StyledRun
import com.lcdcode.moodcairns.markdown.flattenInlines

private val BlockSpacing = 12.dp
private val ItemSpacing = 4.dp
private val ListMarkerWidth = 24.dp
private val QuoteBarWidth = 3.dp
private val QuoteContentInset = 12.dp
private val CodePadding = 12.dp
private val TableCellPadding = 8.dp
private val TableBorderWidth = 1.dp
private val TableMinColumnWidth = 48.dp
private val TableMaxColumnWidth = 240.dp
private const val QUOTE_BAR_ALPHA = 0.6f
private const val BULLET = "•"

/**
 * Read-only rendering of a parsed document. Links are styled but deliberately inert: this
 * app never opens anything outside itself from journal content. Checkboxes are interactive
 * only when [onToggleTask] is given.
 */
@Composable
fun MarkdownView(
    blocks: List<MdBlock>,
    modifier: Modifier = Modifier,
    onToggleTask: ((MdTask) -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BlockSpacing)) {
        blocks.forEach { BlockView(it, onToggleTask) }
    }
}

@Composable
private fun BlockView(block: MdBlock, onToggleTask: ((MdTask) -> Unit)?) {
    when (block) {
        is MdBlock.Heading -> InlineText(block.inlines, headingStyle(block.level))
        is MdBlock.Paragraph -> InlineText(block.inlines, MaterialTheme.typography.bodyLarge)
        is MdBlock.ListBlock -> ListView(block, onToggleTask)
        is MdBlock.Quote -> QuoteView(block, onToggleTask)
        is MdBlock.CodeBlock -> CodeBlockView(block)
        is MdBlock.Table -> TableView(block)
        MdBlock.ThematicBreak -> HorizontalDivider()
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle = with(MaterialTheme.typography) {
    when (level) {
        1 -> headlineSmall
        2 -> titleLarge
        3 -> titleMedium
        else -> titleSmall
    }
}

@Composable
private fun InlineText(
    inlines: List<MdInline>,
    style: TextStyle,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(inlines, codeBackground, linkColor) {
        flattenInlines(inlines).toAnnotatedString(codeBackground, linkColor)
    }
    Text(annotated, style = style, textAlign = textAlign)
}

@Composable
private fun ListView(list: MdBlock.ListBlock, onToggleTask: ((MdTask) -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        list.items.forEachIndexed { index, item ->
            val task = item.task
            // A checkbox is taller than one line of text; centre it on single-block items.
            val alignment = if (task != null && item.blocks.size == 1) {
                Alignment.CenterVertically
            } else {
                Alignment.Top
            }
            Row(verticalAlignment = alignment) {
                if (task != null) {
                    TaskCheckbox(task, onToggleTask)
                } else {
                    Text(
                        text = if (list.ordered) "${list.startNumber + index}." else BULLET,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.widthIn(min = ListMarkerWidth),
                    )
                }
                ListItemBody(item, onToggleTask, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TaskCheckbox(task: MdTask, onToggleTask: ((MdTask) -> Unit)?) {
    val toggleable = onToggleTask != null && task.markerOffset != MarkdownParser.UNKNOWN_OFFSET
    Checkbox(
        checked = task.checked,
        onCheckedChange = if (toggleable) { _ -> onToggleTask?.invoke(task) } else null,
    )
}

@Composable
private fun ListItemBody(
    item: MdListItem,
    onToggleTask: ((MdTask) -> Unit)?,
    modifier: Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        item.blocks.forEach { BlockView(it, onToggleTask) }
    }
}

@Composable
private fun QuoteView(quote: MdBlock.Quote, onToggleTask: ((MdTask) -> Unit)?) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = QUOTE_BAR_ALPHA)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(QuoteBarWidth).fillMaxHeight().background(barColor))
        Column(
            modifier = Modifier.weight(1f).padding(start = QuoteContentInset),
            verticalArrangement = Arrangement.spacedBy(BlockSpacing),
        ) {
            quote.blocks.forEach { BlockView(it, onToggleTask) }
        }
    }
}

@Composable
private fun CodeBlockView(code: MdBlock.CodeBlock) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = code.text,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(CodePadding),
        )
    }
}

/**
 * A grid sized like a spreadsheet: each column is as wide as its widest cell (clamped, so
 * long text wraps) and each row as tall as its tallest cell, so the borders line up. Wide
 * tables scroll sideways instead of squashing.
 */
@Composable
private fun TableView(table: MdBlock.Table) {
    val columns = table.alignments.size
    if (columns == 0) return
    val rows = listOf(table.header) + table.rows
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant
    val bodyStyle = MaterialTheme.typography.bodyMedium
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.Bold)

    Box(Modifier.horizontalScroll(rememberScrollState())) {
        Layout(
            content = {
                rows.forEachIndexed { rowIndex, row ->
                    val isHeader = rowIndex == 0
                    row.forEachIndexed { column, cell ->
                        Box(
                            Modifier
                                .background(if (isHeader) headerBackground else Color.Transparent)
                                .border(TableBorderWidth, borderColor)
                                .padding(TableCellPadding),
                        ) {
                            InlineText(
                                inlines = cell,
                                style = if (isHeader) headerStyle else bodyStyle,
                                textAlign = table.alignments[column].toTextAlign(),
                            )
                        }
                    }
                }
            },
        ) { measurables, _ ->
            val minWidth = TableMinColumnWidth.roundToPx()
            val maxWidth = TableMaxColumnWidth.roundToPx()
            val widths = IntArray(columns) { column ->
                rows.indices.maxOf { row ->
                    measurables[row * columns + column].maxIntrinsicWidth(Constraints.Infinity)
                }.coerceIn(minWidth, maxWidth)
            }
            val heights = IntArray(rows.size) { row ->
                (0 until columns).maxOf { column ->
                    measurables[row * columns + column].maxIntrinsicHeight(widths[column])
                }
            }
            val placeables = measurables.mapIndexed { index, measurable ->
                measurable.measure(
                    Constraints.fixed(widths[index % columns], heights[index / columns]),
                )
            }
            layout(widths.sum(), heights.sum()) {
                var y = 0
                for (row in rows.indices) {
                    var x = 0
                    for (column in 0 until columns) {
                        placeables[row * columns + column].place(x, y)
                        x += widths[column]
                    }
                    y += heights[row]
                }
            }
        }
    }
}

private fun MdAlignment.toTextAlign(): TextAlign = when (this) {
    MdAlignment.NONE, MdAlignment.LEFT -> TextAlign.Start
    MdAlignment.CENTER -> TextAlign.Center
    MdAlignment.RIGHT -> TextAlign.End
}

internal fun List<StyledRun>.toAnnotatedString(
    codeBackground: Color,
    linkColor: Color,
): AnnotatedString = buildAnnotatedString {
    for (run in this@toAnnotatedString) {
        withStyle(run.spanStyle(codeBackground, linkColor)) { append(run.text) }
    }
}

private fun StyledRun.spanStyle(codeBackground: Color, linkColor: Color): SpanStyle {
    val isLink = linkDestination != null
    val decorations = listOfNotNull(
        TextDecoration.Underline.takeIf { isLink },
        TextDecoration.LineThrough.takeIf { strikethrough },
    )
    return SpanStyle(
        color = if (isLink) linkColor else Color.Unspecified,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontFamily = if (code) FontFamily.Monospace else null,
        background = if (code) codeBackground else Color.Unspecified,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
    )
}
