package com.lcdcode.moodcairns.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lcdcode.moodcairns.data.dao.JournalDao
import com.lcdcode.moodcairns.data.entity.JournalDoc
import com.lcdcode.moodcairns.data.entity.JournalDocFts

/**
 * SQLCipher-encrypted journal, kept apart from [MoodDatabase] so it has its own backup file
 * and lifecycle. Keyed with [com.lcdcode.moodcairns.security.JournalKey], derived from the
 * mood DB key, and opened and closed alongside it by LockManager.
 */
@Database(
    entities = [JournalDoc::class, JournalDocFts::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        const val NAME = "journal.db"
    }
}
