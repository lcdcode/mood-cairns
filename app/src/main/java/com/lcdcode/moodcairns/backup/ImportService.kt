package com.lcdcode.moodcairns.backup

import android.net.Uri
import androidx.room.withTransaction
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.db.ScheduleDatabase
import com.lcdcode.moodcairns.data.db.SeedTags
import com.lcdcode.moodcairns.data.entity.EntryTag
import com.lcdcode.moodcairns.data.entity.EntryValue
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportService @Inject constructor(
    private val reader: BackupFileReader,
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
    suspend fun importReplace(uri: Uri, secret: CharArray): ImportResult {
        val raw = try {
            reader.readText(uri)
        } catch (e: BackupImportException) {
            return ImportResult.Failure(e.error)
        }

        val entities = try {
            val file = serializer.parse(raw, secret)
            serializer.toEntities(file)
        } catch (e: BackupImportException) {
            return ImportResult.Failure(e.error)
        } catch (t: Throwable) {
            // toEntities rejects bad timestamps, slots, etc. in a decrypted file.
            return ImportResult.Failure(ImportError.Malformed(t.message ?: t.javaClass.simpleName))
        }

        return try {
            val moodDb = moodHolder.database()
            moodDb.withTransaction {
                moodDb.clearAllTables()
                val scaleDao = moodDb.scaleDao()
                val entryDao = moodDb.entryDao()
                val tagDao = moodDb.tagDao()
                for (s in entities.scales) scaleDao.insert(s)
                // Tags keep their original ids (like scales) so entry_tag links
                // need no remapping. v1 backups predate tags entirely; seed the
                // defaults so those users are not left with an empty tag list.
                for (t in entities.tags) tagDao.insert(t)
                if (entities.schemaVersion < 2) tagDao.insertAllIgnore(SeedTags.tags)
                moodDb.tagCategoryDao().insertAllIgnore(entities.tagCategories)
                val knownTagIds = entities.tags.map { it.id }.toSet()
                for ((entry, values, tagIds) in entities.entries) {
                    val newId = entryDao.insertEntry(entry.copy(id = 0))
                    entryDao.insertValues(
                        values.map { EntryValue(entryId = newId, scaleId = it.scaleId, value = it.value) },
                    )
                    // Drop links to tags absent from the file (hand-edited or
                    // truncated backups) instead of aborting the whole import.
                    entryDao.insertEntryTags(
                        tagIds.filter { it in knownTagIds }.map { EntryTag(entryId = newId, tagId = it) },
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
                tags = entities.tags.size,
            )
        } catch (t: Throwable) {
            ImportResult.Failure(ImportError.WriteFailed(t.message ?: t.javaClass.simpleName))
        }
    }
}

sealed interface ImportResult {
    data class Success(
        val scales: Int,
        val windows: Int,
        val entries: Int,
        val tags: Int = 0,
    ) : ImportResult
    data class Failure(val error: ImportError) : ImportResult
}
