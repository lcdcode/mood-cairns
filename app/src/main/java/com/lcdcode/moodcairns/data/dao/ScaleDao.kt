package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lcdcode.moodcairns.data.entity.Scale
import kotlinx.coroutines.flow.Flow

@Dao
interface ScaleDao {
    @Query("SELECT * FROM scale ORDER BY archived, sortOrder, name")
    fun observeAll(): Flow<List<Scale>>

    @Query("SELECT * FROM scale WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<Scale>>

    @Query("SELECT * FROM scale WHERE id = :id")
    suspend fun byId(id: Long): Scale?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(scale: Scale): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(scales: List<Scale>): List<Long>

    @Update
    suspend fun update(scale: Scale)

    @Query("UPDATE scale SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("SELECT COUNT(DISTINCT entryId) FROM entry_value WHERE scaleId = :id")
    suspend fun countEntriesUsing(id: Long): Int

    /** Delete entries whose only recorded value belongs to [scaleId]; their
     *  entry_value rows are removed by the CASCADE foreign key. */
    @Query(
        """
        DELETE FROM entry
        WHERE id IN (SELECT entryId FROM entry_value WHERE scaleId = :scaleId)
          AND id NOT IN (SELECT entryId FROM entry_value WHERE scaleId != :scaleId)
        """,
    )
    suspend fun deleteEntriesOnlyUsing(scaleId: Long)

    @Query("DELETE FROM entry_value WHERE scaleId = :scaleId")
    suspend fun deleteValuesForScale(scaleId: Long)

    @Query("DELETE FROM scale WHERE id = :id")
    suspend fun deleteScaleRow(id: Long)

    /**
     * Permanently delete a scale and every value logged on it. Entries that
     * only used this scale are removed entirely; entries shared with other
     * scales keep their remaining values. Ordered to satisfy the RESTRICT
     * foreign key on entry_value.scaleId.
     */
    @Transaction
    suspend fun deleteScaleCascade(id: Long) {
        deleteEntriesOnlyUsing(id)
        deleteValuesForScale(id)
        deleteScaleRow(id)
    }

    @Query("UPDATE entry_value SET value = :rangeSum - value WHERE scaleId = :scaleId")
    suspend fun reflectValuesForScale(scaleId: Long, rangeSum: Float)

    /**
     * Save an edited scale and reflect every value logged on it across the
     * range midpoint (v -> rangeSum - v, where rangeSum = oldMin + oldMax), so
     * past entries keep their meaning when the scale's direction flips. The
     * reflection is its own inverse and preserves step-grid alignment.
     */
    @Transaction
    suspend fun updateScaleReflectingValues(scale: Scale, rangeSum: Float) {
        update(scale)
        reflectValuesForScale(scale.id, rangeSum)
    }

    @Query("UPDATE scale SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM scale")
    suspend fun maxSortOrder(): Int

    @Transaction
    suspend fun applySortOrder(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index) }
    }
}
