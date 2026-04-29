package com.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.moodcairns.data.entity.Entry
import com.moodcairns.data.entity.EntryValue
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class EntryWithValues(
    @Embedded val entry: Entry,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val values: List<EntryValue>,
)

@Dao
interface EntryDao {

    @Transaction
    @Query("SELECT * FROM entry ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<EntryWithValues>>

    @Transaction
    @Query(
        """
        SELECT * FROM entry
        WHERE recordedAt BETWEEN :from AND :to
        ORDER BY recordedAt ASC
        """,
    )
    fun observeRange(from: Instant, to: Instant): Flow<List<EntryWithValues>>

    @Query("SELECT MIN(recordedAt) FROM entry")
    fun observeEarliestRecordedAt(): Flow<Instant?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: Entry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(values: List<EntryValue>)

    @Transaction
    suspend fun insertEntryWithValues(entry: Entry, values: (Long) -> List<EntryValue>): Long {
        val id = insertEntry(entry)
        insertValues(values(id))
        return id
    }

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun delete(id: Long)
}
