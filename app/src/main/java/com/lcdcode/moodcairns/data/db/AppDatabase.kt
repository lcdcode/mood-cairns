package com.lcdcode.moodcairns.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lcdcode.moodcairns.data.dao.EntryDao
import com.lcdcode.moodcairns.data.dao.PromptWindowDao
import com.lcdcode.moodcairns.data.dao.ScaleDao
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale

@Database(
    entities = [Scale::class, Entry::class, EntryValue::class, PromptWindow::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scaleDao(): ScaleDao
    abstract fun entryDao(): EntryDao
    abstract fun promptWindowDao(): PromptWindowDao

    companion object {
        const val NAME = "mood_cairns.db"
    }
}
