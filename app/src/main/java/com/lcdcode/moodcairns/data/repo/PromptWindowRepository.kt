package com.lcdcode.moodcairns.data.repo

import com.lcdcode.moodcairns.data.dao.PromptWindowDao
import com.lcdcode.moodcairns.data.entity.PromptWindow
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PromptWindowRepository @Inject constructor(private val dao: PromptWindowDao) {
    fun observeAll(): Flow<List<PromptWindow>> = dao.observeAll()
    suspend fun enabled(): List<PromptWindow> = dao.enabled()
    suspend fun byId(id: Long): PromptWindow? = dao.byId(id)
    suspend fun upsert(window: PromptWindow) {
        if (window.id == 0L) dao.insert(window) else dao.update(window)
    }
    suspend fun delete(window: PromptWindow) = dao.delete(window)
}
