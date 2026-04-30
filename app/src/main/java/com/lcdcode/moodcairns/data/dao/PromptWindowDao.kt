package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lcdcode.moodcairns.data.entity.PromptWindow
import kotlinx.coroutines.flow.Flow

@Dao
interface PromptWindowDao {
    @Query("SELECT * FROM prompt_window ORDER BY startTime")
    fun observeAll(): Flow<List<PromptWindow>>

    @Query("SELECT * FROM prompt_window WHERE enabled = 1 ORDER BY startTime")
    suspend fun enabled(): List<PromptWindow>

    @Query("SELECT * FROM prompt_window WHERE id = :id")
    suspend fun byId(id: Long): PromptWindow?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(window: PromptWindow): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(windows: List<PromptWindow>): List<Long>

    @Update
    suspend fun update(window: PromptWindow)

    @Delete
    suspend fun delete(window: PromptWindow)
}
