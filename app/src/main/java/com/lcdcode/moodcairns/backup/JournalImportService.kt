package com.lcdcode.moodcairns.backup

import android.database.SQLException
import android.net.Uri
import com.lcdcode.moodcairns.data.repo.JournalRepository
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
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
    repo: JournalRepository,
) {
    private val limits = JournalBackupLimits.DEFAULT
    private val restore = JournalRestore(repo, limits)

    /**
     * Streams the file into the database inside one transaction; see [JournalRestore]. Any
     * failure (wrong passphrase, damage, a truncated file, a crossed limit) rolls the
     * transaction back and leaves the current journal exactly as it was.
     */
    suspend fun importReplace(uri: Uri, secret: CharArray): JournalImportResult = try {
        val input = reader.openStream(uri, limits.maxFileBytes, limits.maxMegabytes)
        JournalImportResult.Success(days = restore.restore(input, secret, LocalDate.now()))
    } catch (e: CancellationException) {
        throw e // An IllegalStateException too; it must not read as a write failure.
    } catch (e: BackupImportException) {
        JournalImportResult.Failure(e.error)
    } catch (e: IllegalStateException) {
        writeFailed(e) // The journal database closed (app locked) during the restore.
    } catch (e: SQLException) {
        writeFailed(e)
    }

    private fun writeFailed(cause: Exception) =
        JournalImportResult.Failure(ImportError.WriteFailed(Diagnostic.of(cause)))
}
