package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryTag
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.Tag
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class EntryWithValues(
    @Embedded val entry: Entry,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val values: List<EntryValue>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(EntryTag::class, parentColumn = "entryId", entityColumn = "tagId"),
    )
    val tags: List<Tag> = emptyList(),
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
        WHERE recordedAt >= :from AND recordedAt < :toExclusive
        ORDER BY recordedAt ASC
        """,
    )
    fun observeRange(from: Instant, toExclusive: Instant): Flow<List<EntryWithValues>>

    @Query("SELECT MIN(recordedAt) FROM entry")
    fun observeEarliestRecordedAt(): Flow<Instant?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: Entry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValues(values: List<EntryValue>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntryTags(links: List<EntryTag>)

    @Query("DELETE FROM entry_tag WHERE entryId = :entryId")
    suspend fun deleteTagsForEntry(entryId: Long)

    @Transaction
    suspend fun insertEntryWithValues(
        entry: Entry,
        values: (Long) -> List<EntryValue>,
        tagIds: Set<Long> = emptySet(),
    ): Long {
        val id = insertEntry(entry)
        insertValues(values(id))
        insertEntryTags(tagIds.map { EntryTag(entryId = id, tagId = it) })
        return id
    }

    @Query("DELETE FROM entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getById(id: Long): EntryWithValues?

    @Update
    suspend fun updateEntry(entry: Entry)

    @Query("DELETE FROM entry_value WHERE entryId = :entryId")
    suspend fun deleteValuesForEntry(entryId: Long)

    @Transaction
    suspend fun updateEntryWithValues(
        entry: Entry,
        values: List<EntryValue>,
        tagIds: Set<Long> = emptySet(),
    ) {
        updateEntry(entry)
        deleteValuesForEntry(entry.id)
        insertValues(values)
        deleteTagsForEntry(entry.id)
        insertEntryTags(tagIds.map { EntryTag(entryId = entry.id, tagId = it) })
    }
}
