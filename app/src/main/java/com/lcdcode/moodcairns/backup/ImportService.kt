package com.lcdcode.moodcairns.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.db.ScheduleDatabase
import com.lcdcode.moodcairns.data.entity.EntryValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleDb: ScheduleDatabase,
    private val moodHolder: MoodDatabaseHolder,
    private val serializer: BackupSerializer,
) {
    /**
     * Read JSON from [uri], decrypt with [pin], and replace all local data
     * with its contents. The two physical databases (plaintext schedule +
     * encrypted mood) are written under their own transactions; a failure in
     * the mood-side write rolls that side back, but anything already committed
     * to the schedule side will persist. That trade-off keeps each table's
     * data on the right disk file without needing a cross-DB 2PC; in practice
     * the schedule side is tiny (a handful of rows) and overwriting it
     * idempotently is cheap on retry.
     */
    suspend fun importReplace(uri: Uri, pin: CharArray): ImportResult {
        // Cap the input to avoid OOM on a huge (intentional or accidental) file.
        // Real backups are well under a megabyte even with thousands of entries;
        // 50 MB is a generous ceiling that still keeps the JVM heap safe.
        val knownSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (knownSize > MAX_BACKUP_BYTES) {
            return ImportResult.Failure(
                "Backup file too large (${knownSize / 1024 / 1024} MB; max ${MAX_BACKUP_BYTES / 1024 / 1024} MB)",
            )
        }

        val raw = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // Stream up to MAX+1 bytes so we can detect overruns when the
                // provider didn't report a length (knownSize == -1).
                val bytes = input.readNBytesCapped(MAX_BACKUP_BYTES + 1)
                if (bytes.size > MAX_BACKUP_BYTES) {
                    return ImportResult.Failure("Backup file too large")
                }
                String(bytes, Charsets.UTF_8)
            } ?: return ImportResult.Failure("Could not read file")
        } catch (t: Throwable) {
            return ImportResult.Failure("Could not read file: ${t.message ?: "IO error"}")
        }

        val entities = try {
            val file = serializer.parse(raw, pin)
            serializer.toEntities(file)
        } catch (t: Throwable) {
            return ImportResult.Failure(t.message ?: "Invalid backup file")
        }

        return try {
            val moodDb = moodHolder.database()
            moodDb.withTransaction {
                moodDb.clearAllTables()
                val scaleDao = moodDb.scaleDao()
                val entryDao = moodDb.entryDao()
                for (s in entities.scales) scaleDao.insert(s)
                for ((entry, values) in entities.entries) {
                    val newId = entryDao.insertEntry(entry.copy(id = 0))
                    entryDao.insertValues(
                        values.map { EntryValue(entryId = newId, scaleId = it.scaleId, value = it.value) },
                    )
                }
            }
            scheduleDb.withTransaction {
                scheduleDb.clearAllTables()
                val windowDao = scheduleDb.promptWindowDao()
                for (w in entities.windows) windowDao.insert(w)
            }
            ImportResult.Success(
                scales = entities.scales.size,
                windows = entities.windows.size,
                entries = entities.entries.size,
            )
        } catch (t: Throwable) {
            ImportResult.Failure("Import aborted: ${t.message ?: "database error"}")
        }
    }
}

sealed interface ImportResult {
    data class Success(val scales: Int, val windows: Int, val entries: Int) : ImportResult
    data class Failure(val message: String) : ImportResult
}

private const val MAX_BACKUP_BYTES: Long = 50L * 1024 * 1024

private fun java.io.InputStream.readNBytesCapped(max: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8 * 1024)
    var total = 0L
    while (total < max) {
        val n = read(buf, 0, minOf(buf.size.toLong(), max - total).toInt())
        if (n <= 0) break
        out.write(buf, 0, n)
        total += n
    }
    return out.toByteArray()
}
