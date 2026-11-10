package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.lcdcode.moodcairns.data.entity.TagCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface TagCategoryDao {
    @Query("SELECT * FROM tag_category ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TagCategory>>

    @Query("SELECT * FROM tag_category ORDER BY sortOrder, id")
    suspend fun all(): List<TagCategory>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(categories: List<TagCategory>): List<Long>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: TagCategory)

    @Query("UPDATE tag_category SET label = :label WHERE id = :id")
    suspend fun setLabel(id: String, label: String?)

    @Query("SELECT sortOrder FROM tag_category WHERE id = :id")
    suspend fun sortOrderOf(id: String): Int?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tag_category")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE tag_category SET sortOrder = sortOrder + 1 WHERE sortOrder >= :from")
    suspend fun shiftSortOrderFrom(from: Int)

    /** Inserts [category] in Other's slot and pushes Other (and anything after it) down. */
    @Transaction
    suspend fun insertBeforeOther(category: TagCategory) {
        val slot = sortOrderOf(TagCategory.OTHER_ID) ?: (maxSortOrder() + 1)
        shiftSortOrderFrom(slot)
        insert(category.copy(sortOrder = slot))
    }
}
