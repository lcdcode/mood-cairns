package com.lcdcode.moodcairns.data.repo

import com.lcdcode.moodcairns.data.dao.JournalDocMeta
import com.lcdcode.moodcairns.data.dao.JournalSearchRow
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

    suspend fun allDocs(): List<JournalDoc> = dao().allDocs()

    suspend fun replaceAll(docs: List<JournalDoc>) = dao().replaceAll(docs)
}
