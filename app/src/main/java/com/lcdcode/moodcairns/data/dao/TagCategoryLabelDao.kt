package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.data.entity.TagCategoryLabel
import kotlinx.coroutines.flow.Flow

@Dao
interface TagCategoryLabelDao {
    @Query("SELECT * FROM tag_category_label")
    fun observeAll(): Flow<List<TagCategoryLabel>>

    @Query("SELECT * FROM tag_category_label")
    suspend fun all(): List<TagCategoryLabel>

    @Upsert
    suspend fun upsert(label: TagCategoryLabel)

    @Upsert
    suspend fun upsertAll(labels: List<TagCategoryLabel>)

    @Query("DELETE FROM tag_category_label WHERE category = :category")
    suspend fun delete(category: TagCategory)
}
