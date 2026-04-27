package com.moodcairns.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.moodcairns.data.dao.EntryDao
import com.moodcairns.data.dao.PromptWindowDao
import com.moodcairns.data.dao.ScaleDao
import com.moodcairns.data.entity.Entry
import com.moodcairns.data.entity.EntryValue
import com.moodcairns.data.entity.PromptWindow
import com.moodcairns.data.entity.Scale

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
