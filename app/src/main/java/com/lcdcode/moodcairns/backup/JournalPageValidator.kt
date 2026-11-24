package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.entity.JournalDoc
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate

/**
 * Checks pages one at a time in file order, remembering only the previous day. Refuses an
 * unparseable date, a day outside [JournalBackupFormat.allowedDays], a day not after the
 * previous one (which includes repeats), and a body over the editor's limit, rather than
 * silently dropping or cutting text. Throws [BackupImportException].
 */
internal class JournalPageValidator(today: LocalDate) {
    private val allowedDays = JournalBackupFormat.allowedDays(today)
    private var previousDay: LocalDate? = null

    /** A page ready to insert; its preview is left empty for the repository to compute. */
    fun toDoc(dto: JournalDayDto): JournalDoc {
        val day = parse(dto.day, MalformedReason.INVALID_DAY) { LocalDate.parse(it) }
        if (day !in allowedDays) fail(ImportError.Malformed(MalformedReason.DAY_OUT_OF_RANGE))
        val previous = previousDay
        if (previous != null && day <= previous) {
            fail(ImportError.Malformed(MalformedReason.DAYS_OUT_OF_ORDER))
        }
        previousDay = day
        if (dto.body.length > JournalSql.MAX_BODY_CHARS) {
            fail(ImportError.Malformed(MalformedReason.BODY_TOO_LONG))
        }
        return JournalDoc(
            day = day,
            createdAt = parse(dto.createdAt, MalformedReason.INVALID_CREATED_AT, Instant::parse),
            updatedAt = parse(dto.updatedAt, MalformedReason.INVALID_UPDATED_AT, Instant::parse),
            preview = "",
            body = dto.body,
        )
    }

    /** The rejected value itself is never reported: it is file content of any size. */
    private fun <T> parse(value: String, invalid: MalformedReason, parser: (String) -> T): T =
        try {
            parser(value)
        } catch (_: DateTimeException) {
            fail(ImportError.Malformed(invalid))
        }
}

internal fun JournalDoc.toBackupDto() = JournalDayDto(
    day = day.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    body = body,
)

private fun fail(error: ImportError): Nothing = throw BackupImportException(error)
