package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.PromptWindowDao
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Produces a plaintext CSV of the full dataset. Unlike [BackupSerializer] this
 * export is NOT encrypted - it is gated behind the "Allow unsafe exports"
 * setting because it lands in shared storage readable by cloud backup and other
 * apps. The row/column shaping and escaping live in [MoodCsv]; this class only
 * pulls the current data off the DAOs.
 */
@Singleton
class CsvExporter @Inject constructor(
    private val moodHolder: MoodDatabaseHolder,
    private val windowDao: PromptWindowDao,
) {
    private val scaleDao get() = moodHolder.scaleDao()
    private val entryDao get() = moodHolder.entryDao()

    suspend fun exportCsv(): String {
        val scales = scaleDao.observeAll().first()
        val windows = windowDao.observeAll().first()
        val entries = entryDao.observeAll().first()
        return MoodCsv.build(scales, windows, entries)
    }
}
