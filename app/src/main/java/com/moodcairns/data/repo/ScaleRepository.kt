package com.moodcairns.data.repo

import com.moodcairns.data.dao.ScaleDao
import com.moodcairns.data.entity.Scale
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScaleRepository @Inject constructor(private val dao: ScaleDao) {
    fun observeActive(): Flow<List<Scale>> = dao.observeActive()
    fun observeAll(): Flow<List<Scale>> = dao.observeAll()
    suspend fun byId(id: Long): Scale? = dao.byId(id)
    suspend fun upsert(scale: Scale) {
        if (scale.id == 0L) dao.insert(scale) else dao.update(scale)
    }
    suspend fun setArchived(id: Long, archived: Boolean) = dao.setArchived(id, archived)
}
