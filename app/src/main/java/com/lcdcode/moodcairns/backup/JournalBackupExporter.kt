package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.data.entity.JournalDoc
import com.lcdcode.moodcairns.data.repo.JournalRepository
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Writes every journal page to an encrypted backup stream, a few pages at a time. */
@Singleton
class JournalBackupExporter @Inject constructor(private val repo: JournalRepository) {

    /**
     * Encrypts all pages onto [out] (not closed) and returns how many were written. Refuses
     * to produce a backup the restore would reject; see [writeJournal].
     */
    suspend fun export(passphrase: CharArray, out: OutputStream): Int =
        JournalBackupWriter(out, passphrase).use { writer ->
            writeJournal(writer, LocalDate.now()) { action -> repo.forEachDoc(action = action) }
        }
}

/** A journal page that a restore would refuse, found while exporting. */
class UnrestorableJournalPageException(val day: LocalDate, val error: ImportError) :
    Exception("Journal page $day would be refused on restore: ${error.logLabel()}")

/**
 * Writes the metadata, every page [forEachDoc] visits (oldest first) and the end marker.
 * Each page passes the checks a restore applies, so a bad page fails now rather than at
 * restore time: throws [UnrestorableJournalPageException] for one the restore would refuse,
 * and [BackupTooLargeException] past the size limits.
 */
internal suspend fun writeJournal(
    writer: JournalBackupWriter,
    today: LocalDate,
    forEachDoc: suspend (action: suspend (JournalDoc) -> Unit) -> Unit,
): Int {
    writer.writeMeta(
        JournalBackupMeta(
            kind = JournalBackupFormat.KIND,
            journalVersion = JournalBackupFormat.JOURNAL_VERSION,
            exportedAt = Instant.now().toString(),
            appVersion = BuildConfig.VERSION_NAME,
        ),
    )
    val validator = JournalPageValidator(today)
    var pages = 0
    forEachDoc { doc ->
        val page = doc.toBackupDto()
        validator.requireRestorable(page, doc.day)
        writer.writePage(page)
        pages++
    }
    writer.finish()
    return pages
}

private fun JournalPageValidator.requireRestorable(page: JournalDayDto, day: LocalDate) {
    try {
        toDoc(page)
    } catch (e: BackupImportException) {
        throw UnrestorableJournalPageException(day, e.error)
    }
}
