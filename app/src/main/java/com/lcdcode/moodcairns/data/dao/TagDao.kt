package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lcdcode.moodcairns.data.entity.Tag
import kotlinx.coroutines.flow.Flow

/** SQL shared with JVM tests, which run it on sqlite-jdbc. */
object TagSql {
    /** Per used tag: how many entries carry it. */
    const val USAGE_COUNTS =
        "SELECT tagId, COUNT(DISTINCT entryId) AS entryCount FROM entry_tag GROUP BY tagId"
}

/** A row of [TagSql.USAGE_COUNTS]. */
data class TagUsageRow(val tagId: Long, val entryCount: Int)

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY id")
    suspend fun all(): List<Tag>

    @Query(TagSql.USAGE_COUNTS)
    suspend fun usageCounts(): List<TagUsageRow>

    /**
     * All tags in display order: by category sortOrder, then tag sortOrder. A tag whose
     * category row is missing sorts with Other, where the UI also shows it.
     */
    @Query(
        "SELECT tag.* FROM tag LEFT JOIN tag_category ON tag_category.id = tag.category " +
            "ORDER BY COALESCE(tag_category.sortOrder, " +
            "(SELECT sortOrder FROM tag_category WHERE id = 'OTHER')), " +
            "tag.sortOrder, tag.name COLLATE NOCASE",
    )
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

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tag WHERE category = :categoryId")
    suspend fun maxSortOrder(categoryId: String): Int

    @Transaction
    suspend fun applySortOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }
}
