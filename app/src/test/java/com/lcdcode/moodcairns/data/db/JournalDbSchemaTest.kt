package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.db.JournalSchemaFixture.search
import com.lcdcode.moodcairns.data.db.JournalSchemaFixture.searchDays
import com.lcdcode.moodcairns.data.db.JournalSchemaFixture.updateBodyByDay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.SQLException

/**
 * Builds the current journal database from the exported Room schema (tables, unique index and
 * Room's FTS sync triggers) on JVM SQLite, then runs the DAO's own SQL against it. This
 * covers FTS4 matching, the snippet query, and index sync on insert, update and delete.
 */
class JournalDbSchemaTest {

    private val connection = JournalSchemaFixture.open(CURRENT_VERSION)

    @After
    fun close() = connection.close()

    @Test
    fun body_is_the_last_column() {
        val names = JournalSchemaFixture.columns(connection, "journal_doc").map { it[0] }
        assertEquals(listOf("id", "day", "createdAt", "updatedAt", "preview", "body"), names)
    }

    @Test
    fun whole_word_matches_and_snippet_marks_the_hit() {
        insert("2026-11-01", "Went for a long walk in the park.")
        val hits = search(connection, "park")
        assertEquals(listOf("2026-11-01"), hits.map { it.first })
        val snippet = hits.single().second
        val marked = "${JournalSql.MATCH_START}park${JournalSql.MATCH_END}"
        assertTrue(snippet, snippet.contains(marked))
    }

    @Test
    fun prefix_matches_longer_words() {
        insert("2026-11-01", "walking helped")
        assertEquals(listOf("2026-11-01"), searchDays(connection, "walk"))
    }

    @Test
    fun cyrillic_matching_ignores_case() {
        insert("2026-11-01", "Сегодня было ХОРОШО")
        assertEquals(listOf("2026-11-01"), searchDays(connection, "хорош"))
    }

    @Test
    fun matching_ignores_diacritics() {
        insert("2026-11-01", "Un café con mi madre")
        assertEquals(listOf("2026-11-01"), searchDays(connection, "cafe"))
    }

    @Test
    fun all_terms_must_match() {
        insert("2026-11-01", "rain all day")
        insert("2026-11-02", "rain then sun")
        assertEquals(listOf("2026-11-02"), searchDays(connection, "rain sun"))
    }

    @Test
    fun results_are_newest_day_first() {
        insert("2026-10-30", "tea")
        insert("2026-11-02", "tea")
        insert("2026-11-01", "tea")
        assertEquals(
            listOf("2026-11-02", "2026-11-01", "2026-10-30"),
            searchDays(connection, "tea"),
        )
    }

    @Test
    fun update_re_indexes_the_body_and_refreshes_the_preview() {
        insert("2026-11-01", "old words")
        assertEquals(1, updateBodyByDay(connection, "2026-11-01", "**fresh** text", 2_000L))
        assertEquals(emptyList<String>(), searchDays(connection, "old"))
        assertEquals(listOf("2026-11-01"), searchDays(connection, "fresh"))
        assertEquals(listOf("2026-11-01" to "fresh text"), meta())
    }

    @Test
    fun update_of_a_missing_day_changes_nothing() {
        assertEquals(0, updateBodyByDay(connection, "2026-01-01", "x", 1L))
    }

    @Test
    fun delete_removes_the_day_from_the_index() {
        insert("2026-11-01", "secret plans")
        exec("DELETE FROM journal_doc WHERE day = '2026-11-01'")
        assertEquals(emptyList<String>(), searchDays(connection, "secret"))
    }

    @Test
    fun query_syntax_typed_by_the_user_cannot_break_the_search() {
        insert("2026-11-01", "not raining today")
        for (raw in listOf("\"NOT\" (rain", "rain OR", "body:x*", "a NEAR/3 b", "-rain")) {
            search(connection, raw) // must not throw
        }
        assertEquals(listOf("2026-11-01"), searchDays(connection, "NOT rain"))
    }

    @Test
    fun only_one_document_per_day() {
        insert("2026-11-01", "first")
        assertThrows(SQLException::class.java) { insert("2026-11-01", "second") }
    }

    @Test
    fun meta_query_lists_newest_first_with_the_stored_preview() {
        insert("2026-11-01", "# Monday\n- [ ] milk")
        insert("2026-11-03", "short")
        assertEquals(listOf("2026-11-03" to "short", "2026-11-01" to "Monday milk"), meta())
    }

    @Test
    fun stored_previews_are_bounded() {
        val preview = JournalSql.previewOf("word ".repeat(JournalSql.PREVIEW_CHARS))
        assertTrue(preview.length <= JournalSql.PREVIEW_CHARS + 1) // plus the ellipsis
        assertTrue(preview.endsWith("…"))
    }

    // ---- helpers ----

    /** Inserts like the DAO does: the preview is always derived from the body. */
    private fun insert(day: String, body: String) {
        connection.prepareStatement(
            "INSERT INTO journal_doc (day, createdAt, updatedAt, preview, body) " +
                "VALUES (?, 1, 1, ?, ?)",
        ).use { st ->
            st.setString(1, day)
            st.setString(2, JournalSql.previewOf(body))
            st.setString(3, body)
            st.executeUpdate()
        }
    }

    private fun meta(): List<Pair<String, String>> =
        connection.createStatement().executeQuery(JournalSql.OBSERVE_META).use { rs ->
            buildList { while (rs.next()) add(rs.getString("day") to rs.getString("preview")) }
        }

    private fun exec(sql: String) = connection.createStatement().use { it.execute(sql) }

    private companion object {
        const val CURRENT_VERSION = 2
    }
}
