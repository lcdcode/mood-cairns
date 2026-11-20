package com.lcdcode.moodcairns.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.lcdcode.moodcairns.data.entity.JournalDoc
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** SQL shared with the JVM tests so the two cannot drift. */
object JournalSql {
    /** Characters of body loaded per day for list previews; full bodies stay on disk. */
    const val PREVIEW_CHARS = 300

    /** Wraps each search hit inside [JournalSearchRow.snippet]; never typed by users. */
    const val MATCH_START = "\u0002"
    const val MATCH_END = "\u0003"
    const val SNIPPET_ELLIPSIS = "…"
    const val SNIPPET_TOKENS = 16

    const val OBSERVE_META =
        "SELECT id, day, substr(body, 1, $PREVIEW_CHARS) AS preview, updatedAt " +
            "FROM journal_doc ORDER BY day DESC"

    /** :query must come from buildFtsQuery, which quotes every term. */
    const val SEARCH =
        "SELECT d.id AS id, d.day AS day, " +
            "snippet(journal_fts, '$MATCH_START', '$MATCH_END', '$SNIPPET_ELLIPSIS', -1, " +
            "$SNIPPET_TOKENS) AS snippet " +
            "FROM journal_fts JOIN journal_doc d ON d.id = journal_fts.rowid " +
            "WHERE journal_fts MATCH :query ORDER BY d.day DESC"

    const val UPDATE_BODY_BY_DAY =
        "UPDATE journal_doc SET body = :body, updatedAt = :updatedAt WHERE day = :day"
}

data class JournalDocMeta(
    val id: Long,
    val day: LocalDate,
    val preview: String,
    val updatedAt: Instant,
)

data class JournalSearchRow(
    val id: Long,
    val day: LocalDate,
    val snippet: String,
)

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_doc WHERE day = :day")
    suspend fun byDay(day: LocalDate): JournalDoc?

    @Query(JournalSql.OBSERVE_META)
    fun observeMeta(): Flow<List<JournalDocMeta>>

    @Query(JournalSql.SEARCH)
    suspend fun search(query: String): List<JournalSearchRow>

    @Query("SELECT * FROM journal_doc ORDER BY day ASC")
    suspend fun allDocs(): List<JournalDoc>

    @Insert
    suspend fun insert(doc: JournalDoc): Long

    @Insert
    suspend fun insertAll(docs: List<JournalDoc>)

    @Query(JournalSql.UPDATE_BODY_BY_DAY)
    suspend fun updateBodyByDay(day: LocalDate, body: String, updatedAt: Instant): Int

    @Query("DELETE FROM journal_doc WHERE day = :day")
    suspend fun deleteDay(day: LocalDate)

    @Query("DELETE FROM journal_doc")
    suspend fun deleteAll()

    /**
     * Saves a day's body. A blank body removes the day, so opening the journal and writing
     * nothing never leaves an empty entry behind.
     */
    @Transaction
    suspend fun saveDay(day: LocalDate, body: String, now: Instant) {
        if (body.isBlank()) {
            deleteDay(day)
        } else if (updateBodyByDay(day, body, now) == 0) {
            insert(JournalDoc(day = day, body = body, createdAt = now, updatedAt = now))
        }
    }

    @Transaction
    suspend fun replaceAll(docs: List<JournalDoc>) {
        deleteAll()
        insertAll(docs)
    }
}
