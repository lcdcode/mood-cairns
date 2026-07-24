package com.lcdcode.moodcairns.data.repo

import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(private val holder: MoodDatabaseHolder) {
    private fun dao() = holder.tagDao()

    fun observeAll(): Flow<List<Tag>> = flow { emitAll(dao().observeAll()) }

    suspend fun byId(id: Long): Tag? = dao().byId(id)
    suspend fun upsert(tag: Tag) {
        if (tag.id == 0L) dao().insert(tag) else dao().update(tag)
    }
    suspend fun countEntriesUsing(id: Long): Int = dao().countEntriesUsing(id)
    suspend fun delete(id: Long) = dao().delete(id)
    suspend fun reorder(orderedIds: List<Long>) = dao().applySortOrder(orderedIds)
    suspend fun nextSortOrder(category: TagCategory): Int = dao().maxSortOrder(category.name) + 1
}
