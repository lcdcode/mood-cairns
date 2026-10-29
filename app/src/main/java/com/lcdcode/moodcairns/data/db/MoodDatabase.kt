package com.lcdcode.moodcairns.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lcdcode.moodcairns.data.dao.EntryDao
import com.lcdcode.moodcairns.data.dao.ScaleDao
import com.lcdcode.moodcairns.data.dao.TagCategoryLabelDao
import com.lcdcode.moodcairns.data.dao.TagDao
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryTag
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategoryLabel

/**
 * SQLCipher-encrypted Room database for personal mood data: rating scales and
 * the recorded entries that reference them. Opened only after PIN/biometric
 * unlock, when [com.lcdcode.moodcairns.security.LockManager] has the DB key in
 * memory; closed on lock so the page cache is dropped.
 */
@Database(
    entities = [
        Scale::class, Entry::class, EntryValue::class, Tag::class, EntryTag::class,
        TagCategoryLabel::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MoodDatabase : RoomDatabase() {
    abstract fun scaleDao(): ScaleDao
    abstract fun entryDao(): EntryDao
    abstract fun tagDao(): TagDao
    abstract fun tagCategoryLabelDao(): TagCategoryLabelDao

    companion object {
        const val NAME = "mood.db"
    }
}
