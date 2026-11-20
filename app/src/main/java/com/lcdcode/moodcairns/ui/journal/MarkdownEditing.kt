package com.lcdcode.moodcairns.ui.journal

/**
 * Pure formatting-toolbar logic. Works on plain text plus selection offsets (not Compose
 * types) so it can be unit-tested on the JVM; the screen maps to and from TextFieldValue.
 */
internal data class EditResult(val text: String, val selStart: Int, val selEnd: Int)

internal enum class InlineMarker(val char: Char, val width: Int) {
    BOLD('*', 2),
    ITALIC('*', 1),
    CODE('`', 1);

    val token: String get() = char.toString().repeat(width)

    /** Whether a run of [count] marker characters on both sides already applies this style. */
    fun isAppliedBy(count: Int): Boolean = when (this) {
        BOLD -> count >= 2
        ITALIC -> count % 2 == 1 // `*x*` or `***x***`
        CODE -> count >= 1
    }
}

internal enum class LinePrefix { BULLET, NUMBERED, QUOTE, HEADING }

private const val BULLET_MARKER = "- "
private const val QUOTE_MARKER = "> "
private const val MAX_CYCLED_HEADING_LEVEL = 3

/**
 * Wraps the selection in [marker], or unwraps it when the style is already applied (markers
 * just outside or just inside the selection). Surrounding whitespace is left outside the
 * markers because CommonMark ignores `**word **`. A caret inside a word acts on that word;
 * a caret elsewhere inserts an empty marker pair with the caret between.
 */
internal fun toggleInlineMarker(
    text: String,
    selStart: Int,
    selEnd: Int,
    marker: InlineMarker,
): EditResult {
    val (start, end) = trimmedSelection(text, selStart, selEnd)
    val width = marker.width

    val outside = minOf(runBefore(text, start, marker.char), runAfter(text, end, marker.char))
    if (marker.isAppliedBy(outside)) {
        val unwrapped = text.removeRange(end, end + width).removeRange(start - width, start)
        return EditResult(unwrapped, start - width, end - width)
    }

    val inside = runInside(text, start, end, marker.char)
    if (inside > 0 && marker.isAppliedBy(inside)) {
        val innerStart = start + inside - width
        val innerEnd = end - inside
        val unwrapped = text.removeRange(innerEnd, innerEnd + width)
            .removeRange(innerStart, innerStart + width)
        return EditResult(unwrapped, start, end - 2 * width)
    }

    val wrapped = buildString(text.length + 2 * width) {
        append(text, 0, start)
        append(marker.token)
        append(text, start, end)
        append(marker.token)
        append(text, end, text.length)
    }
    return EditResult(wrapped, start + width, end + width)
}

/**
 * Applies or removes a block prefix on every line touched by the selection. Blank lines in a
 * multi-line selection are skipped. Bullet, numbered and heading markers replace each other;
 * a quote marker is an outer layer kept separately, so quoting a list keeps the list.
 * Numbered lines are renumbered from 1. Heading cycles `#`, `##`, `###`, then off.
 */
internal fun toggleLinePrefix(
    text: String,
    selStart: Int,
    selEnd: Int,
    prefix: LinePrefix,
): EditResult {
    val start = minOf(selStart, selEnd).coerceIn(0, text.length)
    val end = maxOf(selStart, selEnd).coerceIn(0, text.length)
    val blockStart = text.lastIndexOf('\n', start - 1) + 1
    val lastTouched = if (end > start && text[end - 1] == '\n') end - 1 else end
    val blockEnd = text.indexOf('\n', lastTouched).let { if (it < 0) text.length else it }

    val lines = text.substring(blockStart, blockEnd).split('\n').map(::parseLine)
    val targets = lines.indices.filter { lines[it].isNotBlank }.ifEmpty { lines.indices.toList() }
    val rewritten = rewriteLines(lines, targets.toSet(), prefix)

    val newBlock = rewritten.joinToString("\n") { it.render() }
    val newText = text.substring(0, blockStart) + newBlock + text.substring(blockEnd)
    val mapper = OffsetMapper(blockStart, blockEnd, lines, rewritten)
    return EditResult(newText, mapper.map(start), mapper.map(end))
}

private fun rewriteLines(
    lines: List<ParsedLine>,
    targets: Set<Int>,
    prefix: LinePrefix,
): List<ParsedLine> {
    val targetLines = targets.sorted().map { lines[it] }
    return when (prefix) {
        LinePrefix.QUOTE -> {
            val quote = if (targetLines.all { it.quote.isNotEmpty() }) "" else QUOTE_MARKER
            lines.mapIndexed { i, line -> if (i in targets) line.copy(quote = quote) else line }
        }
        LinePrefix.HEADING -> {
            val next = when (val level = targetLines.first().headingLevel) {
                in 0 until MAX_CYCLED_HEADING_LEVEL -> level + 1
                else -> 0
            }
            val inner = if (next == 0) "" else "#".repeat(next) + " "
            lines.mapIndexed { i, line -> if (i in targets) line.copy(inner = inner) else line }
        }
        LinePrefix.BULLET, LinePrefix.NUMBERED -> {
            val removing = targetLines.all { it.innerKind == prefix }
            var number = 0
            lines.mapIndexed { i, line ->
                when {
                    i !in targets -> line
                    removing -> line.copy(inner = "")
                    prefix == LinePrefix.BULLET -> line.copy(inner = BULLET_MARKER)
                    else -> line.copy(inner = "${++number}. ")
                }
            }
        }
    }
}

private val LINE_STRUCTURE = Regex("""^([ \t]*)(> ?)?(#{1,6} |[-*+] |\d{1,9}[.)] )?(.*)$""")

private data class ParsedLine(
    val indent: String,
    val quote: String,
    val inner: String,
    val rest: String,
) {
    val prefixLength: Int get() = indent.length + quote.length + inner.length
    val isNotBlank: Boolean get() = render().isNotBlank()

    val innerKind: LinePrefix? get() = when {
        inner.isEmpty() -> null
        inner.startsWith("#") -> LinePrefix.HEADING
        inner[0].isDigit() -> LinePrefix.NUMBERED
        else -> LinePrefix.BULLET
    }

    val headingLevel: Int get() = if (innerKind == LinePrefix.HEADING) inner.trimEnd().length else 0

    fun render(): String = indent + quote + inner + rest
}

private fun parseLine(line: String): ParsedLine {
    val groups = LINE_STRUCTURE.matchEntire(line)?.groupValues
        ?: return ParsedLine("", "", "", line)
    return ParsedLine(groups[1], groups[2], groups[3], groups[4])
}

/** Maps a selection offset in the old text to the matching offset in the rewritten text. */
private class OffsetMapper(
    private val blockStart: Int,
    private val blockEnd: Int,
    private val oldLines: List<ParsedLine>,
    private val newLines: List<ParsedLine>,
) {
    private val totalDelta =
        newLines.sumOf { it.render().length } - oldLines.sumOf { it.render().length }

    fun map(offset: Int): Int {
        if (offset < blockStart) return offset
        if (offset > blockEnd) return offset + totalDelta
        var oldLineStart = blockStart
        var newLineStart = blockStart
        for (i in oldLines.indices) {
            val old = oldLines[i]
            val new = newLines[i]
            val oldLineEnd = oldLineStart + old.render().length
            if (offset <= oldLineEnd) {
                val inLine = offset - oldLineStart
                val mapped = when {
                    inLine < old.indent.length -> inLine
                    inLine >= old.prefixLength -> inLine - old.prefixLength + new.prefixLength
                    else -> new.prefixLength
                }
                return newLineStart + mapped
            }
            oldLineStart = oldLineEnd + 1
            newLineStart += new.render().length + 1
        }
        return offset + totalDelta
    }
}

private fun trimmedSelection(text: String, selStart: Int, selEnd: Int): Pair<Int, Int> {
    var start = minOf(selStart, selEnd).coerceIn(0, text.length)
    var end = maxOf(selStart, selEnd).coerceIn(0, text.length)
    if (start == end) return wordAround(text, start)
    val original = start
    while (start < end && text[start].isWhitespace()) start++
    while (end > start && text[end - 1].isWhitespace()) end--
    return if (start == end) original to original else start to end
}

private fun wordAround(text: String, caret: Int): Pair<Int, Int> {
    val insideWord = caret in 1 until text.length &&
        text[caret - 1].isLetterOrDigit() && text[caret].isLetterOrDigit()
    if (!insideWord) return caret to caret
    var start = caret
    var end = caret
    while (start > 0 && text[start - 1].isLetterOrDigit()) start--
    while (end < text.length && text[end].isLetterOrDigit()) end++
    return start to end
}

private fun runBefore(text: String, index: Int, ch: Char): Int {
    var count = 0
    while (index - count - 1 >= 0 && text[index - count - 1] == ch) count++
    return count
}

private fun runAfter(text: String, index: Int, ch: Char): Int {
    var count = 0
    while (index + count < text.length && text[index + count] == ch) count++
    return count
}

/** Marker run length at both inner edges of [start, end), or 0 if the runs would overlap. */
private fun runInside(text: String, start: Int, end: Int, ch: Char): Int {
    var lead = 0
    while (start + lead < end && text[start + lead] == ch) lead++
    if (start + lead == end) return 0
    var trail = 0
    while (end - trail - 1 >= start + lead && text[end - trail - 1] == ch) trail++
    return minOf(lead, trail)
}
