package com.lcdcode.moodcairns.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lcdcode.moodcairns.data.dao.PromptWindowDao
import com.lcdcode.moodcairns.data.entity.PromptWindow

/**
 * Plaintext Room database for prompt-scheduling metadata only.
 *
 * Kept unencrypted so background work — [com.lcdcode.moodcairns.work.PromptScheduler],
 * [com.lcdcode.moodcairns.work.DailyScheduleWorker] and the boot receiver — can
 * read the enabled prompt windows while the app is locked and the encrypted
 * mood-data DB is closed. Contains no personal mood data.
 */
@Database(
    entities = [PromptWindow::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun promptWindowDao(): PromptWindowDao

    companion object {
        const val NAME = "schedule.db"
    }
}
