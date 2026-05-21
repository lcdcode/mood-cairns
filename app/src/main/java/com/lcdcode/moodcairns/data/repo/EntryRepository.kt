package com.lcdcode.moodcairns.data.repo

import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.PromptSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryRepository @Inject constructor(private val holder: MoodDatabaseHolder) {

    private fun dao() = holder.entryDao()

    fun observeAll(): Flow<List<EntryWithValues>> = flow { emitAll(dao().observeAll()) }

    fun observeRange(from: Instant, toExclusive: Instant): Flow<List<EntryWithValues>> =
        flow { emitAll(dao().observeRange(from, toExclusive)) }

    fun observeEarliestRecordedAt(): Flow<Instant?> =
        flow { emitAll(dao().observeEarliestRecordedAt()) }

    suspend fun save(
        recordedAt: Instant,
        slot: PromptSlot,
        promptWindowId: Long?,
        note: String?,
        values: Map<Long, Float>,
    ): Long {
        val entry = Entry(
            recordedAt = recordedAt,
            slot = slot,
            promptWindowId = promptWindowId,
            note = note?.takeIf { it.isNotBlank() },
        )
        return dao().insertEntryWithValues(entry) { id ->
            values.map { (scaleId, value) -> EntryValue(entryId = id, scaleId = scaleId, value = value) }
        }
    }

    suspend fun delete(id: Long) = dao().delete(id)

    suspend fun getById(id: Long): EntryWithValues? = dao().getById(id)

    suspend fun update(
        id: Long,
        recordedAt: Instant,
        slot: PromptSlot,
        promptWindowId: Long?,
        note: String?,
        values: Map<Long, Float>,
    ) {
        val entry = Entry(
            id = id,
            recordedAt = recordedAt,
            slot = slot,
            promptWindowId = promptWindowId,
            note = note?.takeIf { it.isNotBlank() },
        )
        dao().updateEntryWithValues(
            entry,
            values.map { (scaleId, value) -> EntryValue(entryId = id, scaleId = scaleId, value = value) },
        )
    }
}
