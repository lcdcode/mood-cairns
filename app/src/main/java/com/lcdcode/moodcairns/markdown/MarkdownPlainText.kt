package com.lcdcode.moodcairns.markdown

import com.lcdcode.moodcairns.ui.common.takeWholeChars

private const val ELLIPSIS = "…"
private const val TABLE_CELL_SEPARATOR = " · "

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
 * Markdown reduced to one line of plain text for previews: headings, list and checkbox
 * markers, quotes, emphasis, link targets, table pipes and divider rows, and code fences are
 * removed, and lines are joined. Line-based and regex-only, so it never parses the document.
 * Characters it does not treat as markup (such as search-hit markers) pass through.
 */
fun markdownPlainText(markdown: String): String =
    markdown.lineSequence()
        .filterNot { line ->
            CODE_FENCE.containsMatchIn(line) || THEMATIC_BREAK.matches(line) ||
                TABLE_DIVIDER.matches(line)
        }
        .map(::plainLine)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .let { WHITESPACE.replace(it, " ").trim() }

/** [markdownPlainText] cut at [maxChars] (never inside an emoji), with an ellipsis if cut. */
fun markdownPreview(markdown: String, maxChars: Int): String {
    val plain = markdownPlainText(markdown)
    if (plain.length <= maxChars) return plain
    return plain.takeWholeChars(maxChars).trimEnd() + ELLIPSIS
}

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
