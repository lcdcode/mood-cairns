package com.moodcairns.data.repo

import com.moodcairns.data.dao.EntryDao
import com.moodcairns.data.dao.EntryWithValues
import com.moodcairns.data.entity.Entry
import com.moodcairns.data.entity.EntryValue
import com.moodcairns.data.entity.PromptSlot
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(private val dao: EntryDao) {

    fun observeAll(): Flow<List<EntryWithValues>> = dao.observeAll()

    fun observeRange(from: Instant, to: Instant): Flow<List<EntryWithValues>> =
        dao.observeRange(from, to)

    fun observeEarliestRecordedAt(): Flow<Instant?> = dao.observeEarliestRecordedAt()

    suspend fun save(
        recordedAt: Instant,
        slot: PromptSlot,
        promptWindowId: Long?,
        note: String?,
        values: Map<Long, Int>,
    ): Long {
        val entry = Entry(
            recordedAt = recordedAt,
            slot = slot,
            promptWindowId = promptWindowId,
            note = note?.takeIf { it.isNotBlank() },
        )
        return dao.insertEntryWithValues(entry) { id ->
            values.map { (scaleId, value) -> EntryValue(entryId = id, scaleId = scaleId, value = value) }
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
