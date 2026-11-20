package com.lcdcode.moodcairns.markdown

/** A stretch of inline text with one uniform style, ready to become an AnnotatedString span. */
data class StyledRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val code: Boolean = false,
    val linkDestination: String? = null,
)

private data class RunStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikethrough: Boolean = false,
    val linkDestination: String? = null,
)

/**
 * Flattens nested inlines into styled runs, merging neighbours that share a style.
 * Soft breaks become newlines rather than spaces: on a phone, pressing Enter once and seeing
 * the lines joined in the read view would look like lost formatting. Obsidian's default
 * behaves the same way.
 */
fun flattenInlines(inlines: List<MdInline>): List<StyledRun> {
    val runs = ArrayList<StyledRun>()
    appendRuns(inlines, RunStyle(), runs)
    return runs
}

private fun appendRuns(inlines: List<MdInline>, style: RunStyle, out: MutableList<StyledRun>) {
    for (inline in inlines) {
        when (inline) {
            is MdInline.Text -> out.appendText(inline.value, style, code = false)
            is MdInline.Code -> out.appendText(inline.value, style, code = true)
            is MdInline.Bold -> appendRuns(inline.children, style.copy(bold = true), out)
            is MdInline.Italic -> appendRuns(inline.children, style.copy(italic = true), out)
            is MdInline.Strikethrough ->
                appendRuns(inline.children, style.copy(strikethrough = true), out)
            is MdInline.Link ->
                appendRuns(inline.children, style.copy(linkDestination = inline.destination), out)
            MdInline.HardBreak, MdInline.SoftBreak -> out.appendText("\n", style, code = false)
        }
    }
}

private fun MutableList<StyledRun>.appendText(text: String, style: RunStyle, code: Boolean) {
    if (text.isEmpty()) return
    val run = StyledRun(
        text = text,
        bold = style.bold,
        italic = style.italic,
        strikethrough = style.strikethrough,
        code = code,
        linkDestination = style.linkDestination,
    )
    val previous = lastOrNull()
    if (previous != null && previous.copy(text = run.text) == run) {
        this[lastIndex] = previous.copy(text = previous.text + text)
    } else {
        add(run)
    }
}
