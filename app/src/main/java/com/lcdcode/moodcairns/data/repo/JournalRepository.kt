package com.lcdcode.moodcairns.data.repo

import androidx.room.withTransaction
import com.lcdcode.moodcairns.data.dao.JournalDocMeta
import com.lcdcode.moodcairns.data.dao.JournalSearchRow
import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.db.JournalDatabaseHolder
import com.lcdcode.moodcairns.data.entity.JournalDoc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalRepository @Inject constructor(private val holder: JournalDatabaseHolder) {

    private fun dao() = holder.journalDao()

    /** False after a lock closed the database; writers check this instead of crashing. */
    fun isOpen(): Boolean = holder.isOpen()

    suspend fun load(day: LocalDate): JournalDoc? = dao().byDay(day)

    /** Creates, updates or (for a blank body) removes the given day's document. */
    suspend fun save(day: LocalDate, body: String, now: Instant = Instant.now()) =
        dao().saveDay(day, body, now)

    fun observeMeta(): Flow<List<JournalDocMeta>> = flow { emitAll(dao().observeMeta()) }

    suspend fun search(raw: String): List<JournalSearchRow> {
        val query = buildFtsQuery(raw)
        return if (query.isEmpty()) emptyList() else dao().search(query)
    }

    /**
     * Visits every page oldest first, loading [batchSize] at a time so a large journal never
     * sits in memory at once.
     */
    suspend fun forEachDoc(
        batchSize: Int = EXPORT_BATCH_PAGES,
        action: suspend (JournalDoc) -> Unit,
    ) {
        var afterDay = FIRST_DAY_CURSOR
        while (true) {
            val batch = dao().docsAfter(afterDay, batchSize)
            batch.forEach { action(it) }
            if (batch.size < batchSize) return
            afterDay = batch.last().day.toString()
        }
    }

    /**
     * Replaces every page inside one transaction. [produce] receives an insert function and
     * calls it once per page; previews are always recomputed from the body, never trusted.
     * If [produce] throws, the transaction rolls back and the journal is unchanged.
     */
    suspend fun replaceAll(produce: suspend (insert: suspend (JournalDoc) -> Unit) -> Unit) {
        val database = holder.database()
        database.withTransaction {
            val dao = database.journalDao()
            dao.deleteAll()
            produce { doc -> dao.insert(doc.copy(preview = JournalSql.previewOf(doc.body))) }
        }
    }

    private companion object {
        const val EXPORT_BATCH_PAGES = 20
        /** Sorts before every ISO date. */
        const val FIRST_DAY_CURSOR = ""
    }
}
