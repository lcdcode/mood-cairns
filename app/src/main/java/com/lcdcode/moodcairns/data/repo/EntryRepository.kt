package com.lcdcode.moodcairns.data.repo

import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.dao.ScaleDayStatusRow
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.PromptSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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

    fun observeDayScaleLog(
        from: Instant,
        toExclusive: Instant,
        excludeEntryId: Long,
    ): Flow<DayScaleLog> = flow {
        val rows = dao().observeScaleDayStatusBetween(from, toExclusive, excludeEntryId)
        emitAll(rows.map(DayScaleLog::of))
    }

    suspend fun save(
        recordedAt: Instant,
        slot: PromptSlot,
        promptWindowId: Long?,
        note: String?,
        values: Map<Long, Float>,
        tagIds: Set<Long> = emptySet(),
    ): Long {
        val entry = Entry(
            recordedAt = recordedAt,
            slot = slot,
            promptWindowId = promptWindowId,
            note = note?.takeIf { it.isNotBlank() },
        )
        return dao().insertEntryWithValues(
            entry,
            values = { id ->
                values.map { (scaleId, value) -> EntryValue(entryId = id, scaleId = scaleId, value = value) }
            },
            tagIds = tagIds,
        )
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
        tagIds: Set<Long> = emptySet(),
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
            tagIds = tagIds,
        )
    }
}

/** How a day's other entries logged each scale, for once-per-day prompting. */
data class DayScaleLog(
    /** Logged above the minimum: answered for the day. */
    val answered: Set<Long> = emptySet(),
    /** Logged only at the minimum, which may mean "not logged yet": ask again. */
    val deferred: Set<Long> = emptySet(),
) {
    companion object {
        fun of(rows: List<ScaleDayStatusRow>): DayScaleLog {
            val (answered, deferred) = rows.partition { it.answered }
            return DayScaleLog(
                answered = answered.mapTo(HashSet()) { it.scaleId },
                deferred = deferred.mapTo(HashSet()) { it.scaleId },
            )
        }
    }
}
