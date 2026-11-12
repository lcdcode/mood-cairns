package com.lcdcode.moodcairns.backup

import android.net.Uri
import androidx.room.withTransaction
import com.lcdcode.moodcairns.data.db.MoodDatabase
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.db.ScheduleDatabase
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.work.PromptScheduler
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports the setup (scales, tags, categories, prompt windows) from a configuration file
 * or a full backup, keeping every entry. See [planMoodConfigImport] for what makes an
 * import incompatible. Nothing is written unless the whole mood-side plan applies.
 */
@Singleton
class ConfigImportService @Inject constructor(
    private val reader: BackupFileReader,
    private val serializer: BackupSerializer,
    private val moodHolder: MoodDatabaseHolder,
    private val scheduleDb: ScheduleDatabase,
    private val scheduler: PromptScheduler,
) {
    suspend fun importConfig(uri: Uri, secret: CharArray): ConfigImportResult {
        val incoming = try {
            serializer.parseConfig(reader.readText(uri), secret)
        } catch (e: BackupImportException) {
            return e.error.toConfigResult()
        }

        return try {
            // The mood side goes first: it is the side that can refuse. The schedule
            // side only runs once the mood side has committed.
            val plan = applyMoodConfig(incoming)
            if (plan is MoodConfigPlan.Incompatible) {
                return ConfigImportResult.Incompatible(plan.conflicts)
            }
            val previousWindows = applyWindows(incoming.windows)
            reschedulePrompts(previousWindows)
            ConfigImportResult.Success(
                scales = incoming.scales.size,
                tags = incoming.tags.size,
                windows = incoming.windows.size,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            ConfigImportResult.Failure(ImportError.WriteFailed(t.message ?: t.javaClass.simpleName))
        }
    }

    /**
     * Plans against the state this transaction sees, so an entry logged meanwhile (for
     * example from the widget) is counted, and applies the plan only if it is compatible.
     */
    private suspend fun applyMoodConfig(incoming: ConfigEntities): MoodConfigPlan {
        val db = moodHolder.database()
        return db.withTransaction {
            val plan = planMoodConfigImport(incoming, loadLocalMoodConfig(db))
            if (plan is MoodConfigPlan.Apply) {
                upsertCategories(db, plan.changes.categories)
                applyTags(db, plan.changes.tags)
                applyScales(db, plan.changes.scales)
                deleteCategories(db, plan.changes.categories)
            }
            plan
        }
    }

    private suspend fun loadLocalMoodConfig(db: MoodDatabase) = LocalMoodConfig(
        scales = db.scaleDao().all(),
        tagCategories = db.tagCategoryDao().all(),
        tags = db.tagDao().all(),
        scaleUsage = db.scaleDao().usageStats().associate {
            it.scaleId to ScaleUsage(it.entryCount, it.minValue, it.maxValue)
        },
        tagUsage = db.tagDao().usageCounts().associate { it.tagId to it.entryCount },
    )

    /** Runs before tags, so every tag's category exists when the tag is written. */
    private suspend fun upsertCategories(
        db: MoodDatabase,
        changes: RowChanges<TagCategory, String>,
    ) {
        val dao = db.tagCategoryDao()
        for (category in changes.inserts) dao.insert(category)
        for (category in changes.updates) dao.update(category)
    }

    /** Runs after tags: a deleted category's tags were all deleted (unused) by then. */
    private suspend fun deleteCategories(
        db: MoodDatabase,
        changes: RowChanges<TagCategory, String>,
    ) {
        val dao = db.tagCategoryDao()
        for (id in changes.deleteIds) dao.deleteRow(id)
    }

    // Deletes run before inserts so a freed name can be reused. Never REPLACE: it
    // deletes the old row, which would fire entry_tag's CASCADE.
    private suspend fun applyTags(db: MoodDatabase, changes: RowChanges<Tag, Long>) {
        val dao = db.tagDao()
        for (id in changes.deleteIds) {
            requireUnused(kind = "tag", id = id, entryCount = dao.countEntriesUsing(id))
            dao.delete(id)
        }
        for (tag in changes.updates) dao.update(tag)
        for (tag in changes.inserts) dao.insert(tag)
    }

    private suspend fun applyScales(db: MoodDatabase, changes: RowChanges<Scale, Long>) {
        val dao = db.scaleDao()
        for (id in changes.deleteIds) {
            requireUnused(kind = "scale", id = id, entryCount = dao.countEntriesUsing(id))
            dao.deleteScaleRow(id)
        }
        for (scale in changes.updates) dao.update(scale)
        for (scale in changes.inserts) dao.insert(scale)
    }

    /** Last line of defence behind the planner: throwing rolls the whole transaction back. */
    private fun requireUnused(kind: String, id: Long, entryCount: Int) {
        check(entryCount == 0) {
            "Refusing to delete $kind $id: $entryCount entries still use it"
        }
    }

    /** Replaces the prompt windows and returns the ones that existed before. */
    private suspend fun applyWindows(incoming: List<PromptWindow>): List<PromptWindow> =
        scheduleDb.withTransaction {
            val dao = scheduleDb.promptWindowDao()
            val previous = dao.all()
            val changes = planWindowImport(incoming, previous)
            for (id in changes.deleteIds) dao.deleteById(id)
            for (window in changes.updates) dao.update(window)
            for (window in changes.inserts) dao.insert(window)
            previous
        }

    /**
     * [PromptScheduler.scheduleNow] only adds alarms, so the alarms armed for the old
     * windows are cancelled first; otherwise a deleted or moved window would still fire.
     */
    private fun reschedulePrompts(previousWindows: List<PromptWindow>) {
        for (window in previousWindows) scheduler.cancelForWindow(window.id)
        scheduler.scheduleNow()
    }
}

sealed interface ConfigImportResult {
    data class Success(val scales: Int, val tags: Int, val windows: Int) : ConfigImportResult

    /** Importing would delete or change entry data; nothing was written. */
    data class Incompatible(val conflicts: List<ConfigConflict>) : ConfigImportResult

    /** The file's format is newer (or older) than this app version can import. */
    data class UnsupportedVersion(val found: Int) : ConfigImportResult

    data class Failure(val error: ImportError) : ConfigImportResult
}

private fun ImportError.toConfigResult(): ConfigImportResult =
    if (this is ImportError.UnsupportedVersion) {
        ConfigImportResult.UnsupportedVersion(found)
    } else {
        ConfigImportResult.Failure(this)
    }
