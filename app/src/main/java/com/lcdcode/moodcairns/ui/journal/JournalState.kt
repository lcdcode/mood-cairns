package com.lcdcode.moodcairns.ui.journal

import com.lcdcode.moodcairns.ui.common.UiText
import com.lcdcode.moodcairns.ui.common.takeWholeChars
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * About 16,000 English words per day. SQLite itself has no practical limit; this keeps the
 * editor responsive and each row far below Android's 2 MB cursor window.
 */
internal const val MAX_JOURNAL_BODY_CHARS = 100_000

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

/**
 * Past days with text open for reading; today and empty days open for writing, since
 * that is what the user most likely came to do.
 */
internal fun opensInReadMode(day: LocalDate, today: LocalDate, body: String): Boolean =
    day != today && body.isNotBlank()

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
