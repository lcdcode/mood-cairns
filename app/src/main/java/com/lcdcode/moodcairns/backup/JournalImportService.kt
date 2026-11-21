package com.lcdcode.moodcairns.backup

import android.database.SQLException
import android.net.Uri
import com.lcdcode.moodcairns.data.repo.JournalRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed interface JournalImportResult {
    data class Success(val days: Int) : JournalImportResult
    data class Failure(val error: ImportError) : JournalImportResult
}

/** Restores a journal backup, replacing every page. Mood data is never touched. */
@Singleton
class JournalImportService @Inject constructor(
    private val reader: BackupFileReader,
    private val repo: JournalRepository,
) {
    /**
     * Streams the file into the database inside one transaction: pages are decrypted,
     * validated and inserted one at a time, so memory stays small however big the backup
     * is. Any failure (wrong passphrase, damage, a truncated file) rolls the transaction
     * back and leaves the current journal exactly as it was.
     */
    suspend fun importReplace(uri: Uri, secret: CharArray): JournalImportResult = try {
        reader.openStream(uri).use { input ->
            // Reads the header and metadata: a wrong passphrase or file fails here, before
            // the transaction starts.
            JournalBackupReader(input, secret).use { backup ->
                val validator = JournalPageValidator()
                var pages = 0
                repo.replaceAll { insert ->
                    while (true) {
                        val page = backup.nextPage() ?: break
                        insert(validator.toDoc(page))
                        pages++
                    }
                    backup.requireComplete()
                }
                JournalImportResult.Success(days = pages)
            }
        }
    } catch (e: BackupImportException) {
        JournalImportResult.Failure(e.error)
    } catch (e: IllegalStateException) {
        writeFailed(e) // The journal database closed (app locked) during the restore.
    } catch (e: SQLException) {
        writeFailed(e)
    }

    private fun writeFailed(cause: Exception) = JournalImportResult.Failure(
        ImportError.WriteFailed(cause.message ?: cause.javaClass.simpleName),
    )
}
