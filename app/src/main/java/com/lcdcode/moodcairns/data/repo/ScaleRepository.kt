package com.lcdcode.moodcairns.data.repo

import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.entity.Scale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScaleRepository @Inject constructor(private val holder: MoodDatabaseHolder) {
    private fun dao() = holder.scaleDao()

    fun observeActive(): Flow<List<Scale>> = flow { emitAll(dao().observeActive()) }
    fun observeAll(): Flow<List<Scale>> = flow { emitAll(dao().observeAll()) }

    suspend fun byId(id: Long): Scale? = dao().byId(id)
    suspend fun upsert(scale: Scale) {
        if (scale.id == 0L) dao().insert(scale) else dao().update(scale)
    }
    suspend fun updateInvertingData(scale: Scale) = dao().updateScaleReflectingValues(scale)
    suspend fun setArchived(id: Long, archived: Boolean) = dao().setArchived(id, archived)
    suspend fun countEntriesUsing(id: Long): Int = dao().countEntriesUsing(id)
    suspend fun delete(id: Long) = dao().deleteScaleCascade(id)
    suspend fun reorder(orderedIds: List<Long>) = dao().applySortOrder(orderedIds)
    suspend fun nextSortOrder(): Int = dao().maxSortOrder() + 1
}
