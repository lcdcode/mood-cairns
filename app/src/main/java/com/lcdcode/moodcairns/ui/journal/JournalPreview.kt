package com.lcdcode.moodcairns.ui.journal

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.ui.common.takeWholeChars

internal const val PREVIEW_MAX_CHARS = 160
private const val ELLIPSIS = "…"
private const val TABLE_CELL_SEPARATOR = " · "

/** Plain text for a list row, with [highlights] marking search hits. */
data class HighlightedText(val text: String, val highlights: List<IntRange> = emptyList())

private val CODE_FENCE = Regex("""^\s*(```|~~~)""")
private val THEMATIC_BREAK = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
private val TABLE_DIVIDER = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")
private val BLOCK_PREFIX = Regex(
    """^\s*(>\s*)*(#{1,6}\s+|[-*+]\s+(\[[ xX]]\s+)?|\d{1,9}[.)]\s+(\[[ xX]]\s+)?)?""",
)
private val IMAGE_OR_LINK = Regex("""!?\[([^\]]*)]\([^)]*\)""")
private val INLINE_MARKUP = Regex("""\*{1,3}|~~|`+""")
private val WHITESPACE = Regex("""\s+""")

/**
 * A short plain-text summary of a page for the history list: Markdown syntax is removed
 * (headings, list and checkbox markers, quotes, emphasis, link targets, table pipes and
 * divider rows, code fences), lines are joined, and the result is cut at [maxChars].
 */
internal fun journalPreview(markdown: String, maxChars: Int = PREVIEW_MAX_CHARS): String {
    val plain = plainTextOf(markdown)
    if (plain.length <= maxChars) return plain
    return plain.takeWholeChars(maxChars).trimEnd() + ELLIPSIS
}

/**
 * Turns an FTS snippet (raw Markdown wrapped around [JournalSql.MATCH_START] /
 * [JournalSql.MATCH_END] markers) into plain text plus the ranges of the matched words.
 */
internal fun snippetPreview(snippet: String): HighlightedText {
    val matchStart = JournalSql.MATCH_START.single()
    val matchEnd = JournalSql.MATCH_END.single()
    val text = StringBuilder()
    val highlights = mutableListOf<IntRange>()
    var openedAt = -1
    for (ch in plainTextOf(snippet)) {
        when (ch) {
            matchStart -> openedAt = text.length
            matchEnd -> {
                if (openedAt in 0 until text.length) highlights += openedAt until text.length
                openedAt = -1
            }
            else -> text.append(ch)
        }
    }
    if (openedAt in 0 until text.length) highlights += openedAt until text.length
    return HighlightedText(text.toString(), highlights)
}

private fun plainTextOf(markdown: String): String =
    markdown.lineSequence()
        .filterNot { line ->
            CODE_FENCE.containsMatchIn(line) || THEMATIC_BREAK.matches(line) ||
                TABLE_DIVIDER.matches(line)
        }
        .map(::plainLine)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .let { WHITESPACE.replace(it, " ").trim() }

private fun plainLine(line: String): String {
    val content = BLOCK_PREFIX.replaceFirst(line, "")
    val cells = if (content.trimStart().startsWith("|")) tableCells(content) else content
    return INLINE_MARKUP.replace(IMAGE_OR_LINK.replace(cells, "$1"), "")
}

private fun tableCells(row: String): String =
    row.trim().removePrefix("|").removeSuffix("|")
        .split('|')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(TABLE_CELL_SEPARATOR)
