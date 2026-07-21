package com.lcdcode.moodcairns.data.db

import android.content.Context
import android.util.Log
import com.lcdcode.moodcairns.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the one-shot v2 → split migration. Reads the legacy `mood_cairns.db`,
 * fans rows into [ScheduleDatabase] and [MoodDatabaseHolder]'s SQLCipher DB,
 * verifies the row counts match what was read, and only then deletes the
 * legacy file. A crash anywhere before the delete leaves the legacy file
 * intact so the next unlock retries.
 */
@Singleton
class LegacyMigrator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduleDb: ScheduleDatabase,
    private val moodHolder: MoodDatabaseHolder,
) {
    private val tag = "LegacyMigrator"

    fun needed(): Boolean = LegacyDatabaseMigration.legacyFileExists(context)

    /** Returns the row counts copied, or null if no legacy file was present. */
    suspend fun migrateIfNeeded(): LegacyDatabaseMigration.Result? {
        val legacyPath = context.getDatabasePath("mood_cairns.db")
        if (!legacyPath.exists()) return null
        check(moodHolder.isOpen()) { "MoodDatabaseHolder must be open before migration" }

        val data = LegacyDatabaseMigration.readLegacy(legacyPath.absolutePath)
        if (BuildConfig.DEBUG) {
            Log.i(
                tag,
                "read legacy: ${data.scales.size} scales / ${data.windows.size} windows / " +
                    "${data.entries.size} entries / ${data.entryValues.size} values",
            )
        }

        // Clear any seed rows that landed in the new (just-created) DBs so the
        // legacy ids can be inserted verbatim — foreign-key references in
        // entry_value depend on stable scale ids surviving the move.
        scheduleDb.clearAllTables()
        moodHolder.database().clearAllTables()

        for (w in data.windows) insertWindowWithId(scheduleDb, w)

        val moodDb = moodHolder.database()
        for (s in data.scales) insertScaleWithId(moodDb, s)
        for (e in data.entries) insertEntryWithId(moodDb, e)
        for (v in data.entryValues) insertValueWithId(moodDb, v)

        // Verify before deleting the legacy file.
        val verify = LegacyDatabaseMigration.Result(
            scales = countRows(moodDb, "scale"),
            windows = countRows(scheduleDb, "prompt_window"),
            entries = countRows(moodDb, "entry"),
            entryValues = countRows(moodDb, "entry_value"),
        )
        require(
            verify.scales == data.scales.size &&
                verify.windows == data.windows.size &&
                verify.entries == data.entries.size &&
                verify.entryValues == data.entryValues.size,
        ) {
            "Migration row-count mismatch: read=${data.scales.size}/${data.windows.size}/" +
                "${data.entries.size}/${data.entryValues.size}, " +
                "wrote=${verify.scales}/${verify.windows}/${verify.entries}/${verify.entryValues}"
        }

        LegacyDatabaseMigration.deleteLegacy(context)

        // clearAllTables() above also wiped the seed tags the fresh v2 DB was
        // created with; legacy databases have no tags, so re-seed the defaults.
        moodHolder.tagDao().insertAllIgnore(SeedTags.tags)

        if (BuildConfig.DEBUG) Log.i(tag, "migration complete, legacy file removed")
        return verify
    }

    /**
     * Raw INSERT with the original primary-key so foreign-key references stay
     * intact across the split. Room's @Insert ignores explicit id=0 -> autogen
     * which is the wrong semantics here.
     */
    private fun insertWindowWithId(db: ScheduleDatabase, w: com.lcdcode.moodcairns.data.entity.PromptWindow) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO prompt_window (id, label, slot, startTime, endTime, enabled) VALUES (?,?,?,?,?,?)",
            arrayOf(w.id, w.label, w.slot.name, w.startTime.toString(), w.endTime.toString(), if (w.enabled) 1 else 0),
        )
    }

    private fun insertScaleWithId(db: MoodDatabase, s: com.lcdcode.moodcairns.data.entity.Scale) {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb, isBuiltIn, archived, sortOrder)
            VALUES (?,?,?,?,?,?,?,?,?)
            """.trimIndent(),
            arrayOf(s.id, s.name, s.minValue, s.maxValue, s.step, s.colorArgb,
                if (s.isBuiltIn) 1 else 0, if (s.archived) 1 else 0, s.sortOrder),
        )
    }

    private fun insertEntryWithId(db: MoodDatabase, e: com.lcdcode.moodcairns.data.entity.Entry) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO entry (id, recordedAt, slot, promptWindowId, note) VALUES (?,?,?,?,?)",
            arrayOf(e.id, e.recordedAt.toEpochMilli(), e.slot.name, e.promptWindowId, e.note),
        )
    }

    private fun insertValueWithId(db: MoodDatabase, v: com.lcdcode.moodcairns.data.entity.EntryValue) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO entry_value (entryId, scaleId, value) VALUES (?,?,?)",
            arrayOf(v.entryId, v.scaleId, v.value),
        )
    }

    private fun countRows(db: androidx.room.RoomDatabase, table: String): Int {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }
}
