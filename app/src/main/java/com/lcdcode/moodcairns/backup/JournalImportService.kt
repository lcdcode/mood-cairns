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
    private val serializer: JournalBackupSerializer,
    private val repo: JournalRepository,
) {
    /**
     * Reads, decrypts and fully validates the file before writing anything, then replaces
     * all pages in one transaction: a failure leaves the current journal as it was.
     */
    suspend fun importReplace(uri: Uri, secret: CharArray): JournalImportResult {
        val docs = try {
            serializer.parse(reader.readText(uri), secret)
        } catch (e: BackupImportException) {
            return JournalImportResult.Failure(e.error)
        }
        return try {
            repo.replaceAll(docs)
            JournalImportResult.Success(days = docs.size)
        } catch (e: IllegalStateException) {
            writeFailed(e) // The journal database closed (app locked) before the write.
        } catch (e: SQLException) {
            writeFailed(e)
        }
    }

    private fun writeFailed(cause: Exception) = JournalImportResult.Failure(
        ImportError.WriteFailed(cause.message ?: cause.javaClass.simpleName),
    )
}
