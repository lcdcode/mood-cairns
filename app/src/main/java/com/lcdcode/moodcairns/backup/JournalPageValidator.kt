package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.entity.JournalDoc
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate

/**
 * Checks restored pages one at a time, remembering only the days seen so far. Refuses an
 * unparseable date, a day listed twice, and a body over the editor's limit, rather than
 * silently dropping or cutting text. Throws [BackupImportException].
 */
internal class JournalPageValidator {
    private val seen = HashSet<LocalDate>()

    /** A page ready to insert; its preview is left empty for the repository to compute. */
    fun toDoc(dto: JournalDayDto): JournalDoc {
        val day = parse(dto.day, MalformedReason.INVALID_DAY) { LocalDate.parse(it) }
        if (!seen.add(day)) fail(ImportError.Malformed(MalformedReason.DAYS_OUT_OF_ORDER))
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
