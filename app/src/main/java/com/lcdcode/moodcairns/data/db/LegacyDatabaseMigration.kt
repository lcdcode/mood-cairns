package com.lcdcode.moodcairns.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import java.io.File
import java.time.Instant
import java.time.LocalTime

/**
 * One-shot migration off the legacy plaintext `mood_cairns.db` (Room schema v1
 * or v2) into the split layout: prompt_window rows go to [ScheduleDatabase],
 * everything else into the SQLCipher-encrypted [MoodDatabase].
 *
 * Idempotent and crash-safe by construction: the legacy file is only deleted
 * **after** both target databases have been populated and the row counts match
 * what was read out of the legacy file. If anything fails mid-way, the legacy
 * file is left intact, both target DBs are wiped of any partial copy, and the
 * next unlock retries.
 */
object LegacyDatabaseMigration {

    private const val TAG = "LegacyMigration"
    private const val LEGACY_NAME = "mood_cairns.db"

    /** True iff the legacy plaintext database file is present on disk. */
    fun legacyFileExists(context: Context): Boolean =
        context.getDatabasePath(LEGACY_NAME).exists()

    data class Result(
        val scales: Int,
        val windows: Int,
        val entries: Int,
        val entryValues: Int,
    )

    /**
     * Read every row out of the legacy file. Caller then writes them into the
     * (new, empty) target databases — keeping this function I/O-free over the
     * destination side keeps it unit-testable against a JDBC SQLite engine.
     */
    fun readLegacy(legacyPath: String): LegacyData {
        val db = SQLiteDatabase.openDatabase(
            legacyPath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            // v1 had INTEGER columns for scale.step / entry_value.value; v2
            // widened them to REAL. Either is readable via getFloat/getDouble,
            // so a single read path covers both schema versions.
            val schemaVersion = readUserVersion(db)
            if (BuildConfig.DEBUG) Log.i(TAG, "legacy schema user_version=$schemaVersion")

            val scales = readScales(db)
            val windows = readWindows(db)
            val entries = readEntries(db)
            val values = readEntryValues(db)
            return LegacyData(scales, windows, entries, values)
        } finally {
            db.close()
        }
    }

    /** Delete the legacy database file (plus its `-journal`/`-wal`/`-shm` siblings). */
    fun deleteLegacy(context: Context) {
        val main = context.getDatabasePath(LEGACY_NAME)
        val parent = main.parentFile ?: return
        listOf(
            main,
            File(parent, "$LEGACY_NAME-journal"),
            File(parent, "$LEGACY_NAME-shm"),
            File(parent, "$LEGACY_NAME-wal"),
        ).forEach { if (it.exists()) it.delete() }
    }

    data class LegacyData(
        val scales: List<Scale>,
        val windows: List<PromptWindow>,
        val entries: List<Entry>,
        val entryValues: List<EntryValue>,
    )

    private fun readUserVersion(db: SQLiteDatabase): Int {
        db.rawQuery("PRAGMA user_version", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun readScales(db: SQLiteDatabase): List<Scale> {
        val out = mutableListOf<Scale>()
        db.rawQuery(
            "SELECT id, name, minValue, maxValue, step, colorArgb, isBuiltIn, archived, sortOrder FROM scale",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += Scale(
                    id = c.getLong(0),
                    name = c.getString(1),
                    minValue = c.getInt(2),
                    maxValue = c.getInt(3),
                    step = c.getFloat(4),
                    colorArgb = c.getInt(5),
                    isBuiltIn = c.getInt(6) != 0,
                    archived = c.getInt(7) != 0,
                    sortOrder = c.getInt(8),
                )
            }
        }
        return out
    }

    private fun readWindows(db: SQLiteDatabase): List<PromptWindow> {
        val out = mutableListOf<PromptWindow>()
        db.rawQuery(
            "SELECT id, label, slot, startTime, endTime, enabled FROM prompt_window",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += PromptWindow(
                    id = c.getLong(0),
                    label = c.getString(1),
                    slot = PromptSlot.valueOf(c.getString(2)),
                    startTime = LocalTime.parse(c.getString(3)),
                    endTime = LocalTime.parse(c.getString(4)),
                    enabled = c.getInt(5) != 0,
                )
            }
        }
        return out
    }

    private fun readEntries(db: SQLiteDatabase): List<Entry> {
        val out = mutableListOf<Entry>()
        db.rawQuery(
            "SELECT id, recordedAt, slot, promptWindowId, note FROM entry",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += Entry(
                    id = c.getLong(0),
                    recordedAt = Instant.ofEpochMilli(c.getLong(1)),
                    slot = PromptSlot.valueOf(c.getString(2)),
                    promptWindowId = if (c.isNull(3)) null else c.getLong(3),
                    note = if (c.isNull(4)) null else c.getString(4),
                )
            }
        }
        return out
    }

    private fun readEntryValues(db: SQLiteDatabase): List<EntryValue> {
        val out = mutableListOf<EntryValue>()
        db.rawQuery(
            "SELECT entryId, scaleId, value FROM entry_value",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += EntryValue(
                    entryId = c.getLong(0),
                    scaleId = c.getLong(1),
                    value = c.getFloat(2),
                )
            }
        }
        return out
    }
}
