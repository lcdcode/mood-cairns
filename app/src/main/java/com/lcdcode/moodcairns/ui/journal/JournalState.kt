package com.lcdcode.moodcairns.ui.journal

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.ui.common.UiText
import com.lcdcode.moodcairns.ui.common.takeWholeChars
import java.time.LocalDate
import java.time.format.DateTimeParseException

internal const val MAX_JOURNAL_BODY_CHARS = JournalSql.MAX_BODY_CHARS

internal const val AUTOSAVE_INTERVAL_MS = 30_000L

internal fun clampJournalBody(raw: String): String = raw.takeWholeChars(MAX_JOURNAL_BODY_CHARS)

/** The day to open: the navigation argument if it is a valid ISO date, otherwise today. */
internal fun resolveJournalDay(arg: String?, today: LocalDate): LocalDate {
    if (arg.isNullOrBlank()) return today
    return try {
        LocalDate.parse(arg)
    } catch (_: DateTimeParseException) {
        today
    }
}

/** Pages that already have text open for reading; empty pages open ready for writing. */
internal fun opensInReadMode(body: String): Boolean = body.isNotBlank()

data class JournalUiState(
    val day: LocalDate,
    val body: String = "",
    /** The body as last written to the database. */
    val savedBody: String = "",
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val readMode: Boolean = false,
    val error: UiText? = null,
) {
    /**
     * Only a loaded page can be dirty: saving before the load finished would replace the
     * stored text with an empty body, which deletes the day.
     */
    val hasUnsavedChanges: Boolean get() = loaded && body != savedBody
}
