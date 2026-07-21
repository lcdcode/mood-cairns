package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale

/**
 * Builds a plaintext, RFC 4180 CSV view of the full dataset. Pure and free of
 * Android dependencies so it can be unit-tested on the JVM (unlike the encrypted
 * [BackupSerializer], which pulls in android.util.Base64).
 *
 * Layout is "wide": one row per entry, one column per non-archived scale, so the
 * result drops straight into a spreadsheet. Archived scales are still emitted if
 * an entry carries a value for one, so no recorded data is silently lost.
 */
object MoodCsv {

    private val FIXED_HEADERS = listOf("recordedAt", "slot", "window", "note", "tags")

    fun build(
        scales: List<Scale>,
        windows: List<PromptWindow>,
        entries: List<EntryWithValues>,
    ): String {
        val scaleColumns = orderedScaleColumns(scales, entries)
        val windowLabels = windows.associate { it.id to it.label }

        val sb = StringBuilder()
        appendRow(sb, FIXED_HEADERS + scaleColumns.map { it.name })

        for (e in entries.sortedByDescending { it.entry.recordedAt }) {
            val valueByScale = e.values.associate { it.scaleId to it.value }
            val row = ArrayList<String>(FIXED_HEADERS.size + scaleColumns.size)
            row += e.entry.recordedAt.toString()
            row += e.entry.slot.name
            row += e.entry.promptWindowId?.let { windowLabels[it] ?: it.toString() } ?: ""
            row += e.entry.note ?: ""
            row += e.tags.joinToString("; ") { it.name }
            for (scale in scaleColumns) {
                row += valueByScale[scale.id]?.let(::formatValue) ?: ""
            }
            appendRow(sb, row)
        }
        return sb.toString()
    }

    /**
     * Scales to render as columns, in a stable order: declared scales first
     * (their own sortOrder, then name), followed by any scale referenced by an
     * entry but no longer present in [scales] - a deleted-but-recorded scale
     * still gets a synthetic column so its values survive the export.
     */
    private fun orderedScaleColumns(
        scales: List<Scale>,
        entries: List<EntryWithValues>,
    ): List<Scale> {
        val declared = scales.sortedWith(compareBy({ it.sortOrder }, { it.name }))
        val known = declared.map { it.id }.toSet()
        val orphanIds = entries
            .flatMap { it.values }
            .map { it.scaleId }
            .filter { it !in known }
            .distinct()
            .sorted()
        val orphans = orphanIds.map { syntheticScale(it) }
        return declared + orphans
    }

    private fun syntheticScale(id: Long) = Scale(
        id = id,
        name = "scale_$id",
        minValue = 0,
        maxValue = 0,
        colorArgb = 0,
    )

    /** Trim a trailing ".0" so whole numbers read cleanly, keep real decimals. */
    private fun formatValue(v: Float): String =
        if (v == v.toLong().toFloat()) v.toLong().toString() else v.toString()

    private fun appendRow(sb: StringBuilder, fields: List<String>) {
        fields.joinTo(sb, separator = ",", transform = ::escape)
        sb.append("\r\n")
    }

    /** RFC 4180: quote when a field holds a comma, quote, CR or LF; double quotes. */
    private fun escape(field: String): String {
        val needsQuoting = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuoting) return field
        return "\"" + field.replace("\"", "\"\"") + "\""
    }
}
