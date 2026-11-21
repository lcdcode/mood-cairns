package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.data.repo.JournalRepository
import java.io.OutputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Writes every journal page to an encrypted backup stream, a few pages at a time. */
@Singleton
class JournalBackupExporter @Inject constructor(private val repo: JournalRepository) {

    /** Encrypts all pages onto [out] (not closed) and returns how many were written. */
    suspend fun export(passphrase: CharArray, out: OutputStream): Int =
        JournalBackupWriter(out, passphrase).use { writer ->
            writer.writeMeta(
                JournalBackupMeta(
                    kind = JournalBackupFormat.KIND,
                    journalVersion = JournalBackupFormat.JOURNAL_VERSION,
                    exportedAt = Instant.now().toString(),
                    appVersion = BuildConfig.VERSION_NAME,
                ),
            )
            var pages = 0
            repo.forEachDoc { doc ->
                writer.writePage(doc.toBackupDto())
                pages++
            }
            writer.finish()
            pages
        }
}
