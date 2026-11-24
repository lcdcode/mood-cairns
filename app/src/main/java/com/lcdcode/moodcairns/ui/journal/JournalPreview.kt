package com.lcdcode.moodcairns.ui.journal

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.markdown.markdownPlainText

/** Plain text for a list row, with [highlights] marking search hits. */
data class HighlightedText(val text: String, val highlights: List<IntRange> = emptyList())

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
    for (ch in markdownPlainText(snippet)) {
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
