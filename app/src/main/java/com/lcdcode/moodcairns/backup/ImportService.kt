package com.lcdcode.moodcairns.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lcdcode.moodcairns.data.db.AppDatabase
import com.lcdcode.moodcairns.data.entity.EntryValue
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val serializer: BackupSerializer,
) {
    /**
     * Read JSON from [uri], decrypt with [pin], and replace all local data
     * with its contents. Atomic: either everything swaps or nothing does.
     */
    suspend fun importReplace(uri: Uri, pin: CharArray): ImportResult {
        val raw = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
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
            db.withTransaction {
                db.clearAllTables()
                val scaleDao = db.scaleDao()
                val windowDao = db.promptWindowDao()
                val entryDao = db.entryDao()
                for (s in entities.scales) scaleDao.insert(s)
                for (w in entities.windows) windowDao.insert(w)
                for ((entry, values) in entities.entries) {
                    val newId = entryDao.insertEntry(entry.copy(id = 0))
                    entryDao.insertValues(
                        values.map { EntryValue(entryId = newId, scaleId = it.scaleId, value = it.value) },
                    )
                }
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
