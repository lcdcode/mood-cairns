package com.lcdcode.moodcairns.ui.journal

import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.markdown.MdBlock
import com.lcdcode.moodcairns.markdown.MdInline
import com.lcdcode.moodcairns.markdown.StyledRun
import com.lcdcode.moodcairns.markdown.flattenInlines

private val BlockSpacing = 12.dp
private val ItemSpacing = 4.dp
private val ListMarkerWidth = 24.dp
private val QuoteBarWidth = 3.dp
private val QuoteContentInset = 12.dp
private val CodePadding = 12.dp
private const val QUOTE_BAR_ALPHA = 0.6f
private const val BULLET = "•"

/**
 * Read-only rendering of a parsed journal document. Links are styled but deliberately inert:
 * this app never opens anything outside itself from journal content.
 */
@Composable
fun MarkdownView(blocks: List<MdBlock>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BlockSpacing)) {
        blocks.forEach { BlockView(it) }
    }
}

@Composable
private fun BlockView(block: MdBlock) {
    when (block) {
        is MdBlock.Heading -> InlineText(block.inlines, headingStyle(block.level))
        is MdBlock.Paragraph -> InlineText(block.inlines, MaterialTheme.typography.bodyLarge)
        is MdBlock.ListBlock -> ListView(block)
        is MdBlock.Quote -> QuoteView(block)
        is MdBlock.CodeBlock -> CodeBlockView(block)
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
private fun InlineText(inlines: List<MdInline>, style: TextStyle) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(inlines, codeBackground, linkColor) {
        flattenInlines(inlines).toAnnotatedString(codeBackground, linkColor)
    }
    Text(annotated, style = style)
}

@Composable
private fun ListView(list: MdBlock.ListBlock) {
    val style = MaterialTheme.typography.bodyLarge
    Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
        list.items.forEachIndexed { index, item ->
            Row {
                Text(
                    text = if (list.ordered) "${list.startNumber + index}." else BULLET,
                    style = style,
                    modifier = Modifier.widthIn(min = ListMarkerWidth),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ItemSpacing),
                ) {
                    item.forEach { BlockView(it) }
                }
            }
        }
    }
}

@Composable
private fun QuoteView(quote: MdBlock.Quote) {
    val barColor = MaterialTheme.colorScheme.primary.copy(alpha = QUOTE_BAR_ALPHA)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(QuoteBarWidth).fillMaxHeight().background(barColor))
        Column(
            modifier = Modifier.weight(1f).padding(start = QuoteContentInset),
            verticalArrangement = Arrangement.spacedBy(BlockSpacing),
        ) {
            quote.blocks.forEach { BlockView(it) }
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
    return SpanStyle(
        color = if (isLink) linkColor else Color.Unspecified,
        fontWeight = if (bold) FontWeight.Bold else null,
        fontStyle = if (italic) FontStyle.Italic else null,
        fontFamily = if (code) FontFamily.Monospace else null,
        background = if (code) codeBackground else Color.Unspecified,
        textDecoration = if (isLink) TextDecoration.Underline else null,
    )
}
