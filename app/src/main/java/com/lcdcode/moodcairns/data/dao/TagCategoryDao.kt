package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import kotlinx.coroutines.flow.Flow

/** SQL shared with JVM tests, which run it on sqlite-jdbc with named params substituted. */
internal object TagCategorySql {
    /** Gives [intoTagId] every entry of [fromTagId]; IGNORE skips entries that had both. */
    const val COPY_ENTRY_LINKS =
        "INSERT OR IGNORE INTO entry_tag (entryId, tagId) " +
            "SELECT entryId, :intoTagId FROM entry_tag WHERE tagId = :fromTagId"

    /** Column positions only mean something within a section, so a moved tag restarts at 0. */
    const val MOVE_TAG =
        "UPDATE tag SET category = :categoryId, sortOrder = :sortOrder, columnIndex = 0 " +
            "WHERE id = :tagId"
}

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

    @Update
    suspend fun update(category: TagCategory)

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

    @Query("SELECT COUNT(*) FROM tag WHERE category = :categoryId")
    suspend fun countTagsIn(categoryId: String): Int

    @Query("SELECT * FROM tag WHERE category = :categoryId")
    suspend fun tagsIn(categoryId: String): List<Tag>

    @Query(TagCategorySql.COPY_ENTRY_LINKS)
    suspend fun copyEntryLinks(fromTagId: Long, intoTagId: Long)

    /** Rows in entry_tag referencing this tag are removed by the CASCADE foreign key. */
    @Query("DELETE FROM tag WHERE id = :tagId")
    suspend fun deleteTag(tagId: Long)

    @Query(TagCategorySql.MOVE_TAG)
    suspend fun moveTag(tagId: Long, categoryId: String, sortOrder: Int)

    @Query("DELETE FROM tag_category WHERE id = :id")
    suspend fun deleteRow(id: String)

    /**
     * Deletes category [id] and keeps its tags by moving them to Other (see
     * [planCategoryDeletion]). Other itself can never be deleted.
     */
    @Transaction
    suspend fun deleteMovingTagsToOther(id: String) {
        require(id != TagCategory.OTHER_ID) {
            "Category $id is where deleted categories' tags go, so it cannot be deleted"
        }
        val plan = planCategoryDeletion(tagsIn(id), tagsIn(TagCategory.OTHER_ID))
        for (merge in plan.merges) {
            copyEntryLinks(merge.fromTagId, merge.intoTagId)
            deleteTag(merge.fromTagId)
        }
        for (move in plan.moves) moveTag(move.tagId, TagCategory.OTHER_ID, move.sortOrder)
        deleteRow(id)
    }
}
