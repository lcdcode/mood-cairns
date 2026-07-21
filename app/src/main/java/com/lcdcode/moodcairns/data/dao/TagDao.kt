package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lcdcode.moodcairns.data.entity.Tag
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY category, sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Tag>>

    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun byId(id: Long): Tag?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(tags: List<Tag>): List<Long>

    @Update
    suspend fun update(tag: Tag)

    /** Rows in entry_tag referencing this tag are removed by the CASCADE foreign key. */
    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(DISTINCT entryId) FROM entry_tag WHERE tagId = :id")
    suspend fun countEntriesUsing(id: Long): Int

    @Query("UPDATE tag SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tag WHERE category = :category")
    suspend fun maxSortOrder(category: String): Int

    @Transaction
    suspend fun applySortOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }
}
