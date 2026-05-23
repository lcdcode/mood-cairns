package com.lcdcode.moodcairns.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lcdcode.moodcairns.data.dao.EntryDao
import com.lcdcode.moodcairns.data.dao.ScaleDao
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.Scale

/**
 * SQLCipher-encrypted Room database for personal mood data: rating scales and
 * the recorded entries that reference them. Opened only after PIN/biometric
 * unlock, when [com.lcdcode.moodcairns.security.LockManager] has the DB key in
 * memory; closed on lock so the page cache is dropped.
 */
@Database(
    entities = [Scale::class, Entry::class, EntryValue::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun scaleDao(): ScaleDao
    abstract fun entryDao(): EntryDao

    companion object {
        const val NAME = "mood.db"
    }
}
