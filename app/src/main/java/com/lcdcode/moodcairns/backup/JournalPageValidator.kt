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
        val day = parse("day", dto.day) { LocalDate.parse(it) }
        if (!seen.add(day)) fail(ImportError.Malformed("Day ${dto.day} appears twice"))
        if (dto.body.length > JournalSql.MAX_BODY_CHARS) {
            fail(ImportError.Malformed("Page for ${dto.day} is longer than the editor allows"))
        }
        return JournalDoc(
            day = day,
            createdAt = parse("createdAt", dto.createdAt) { Instant.parse(it) },
            updatedAt = parse("updatedAt", dto.updatedAt) { Instant.parse(it) },
            preview = "",
            body = dto.body,
        )
    }

    private fun <T> parse(field: String, value: String, parser: (String) -> T): T = try {
        parser(value)
    } catch (_: DateTimeException) {
        fail(ImportError.Malformed("Invalid $field \"$value\""))
    }
}

internal fun JournalDoc.toBackupDto() = JournalDayDto(
    day = day.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
    body = body,
)

private fun fail(error: ImportError): Nothing = throw BackupImportException(error)
