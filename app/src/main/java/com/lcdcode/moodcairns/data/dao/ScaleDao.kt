package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
}
