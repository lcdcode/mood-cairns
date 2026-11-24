package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.db.JournalSchemaFixture.searchDays
import com.lcdcode.moodcairns.data.db.JournalSchemaFixture.updateBodyByDay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.SQLException

/**
 * Runs the v1 -> v2 journal migration (body moved last, stored preview added) on a v1
 * database built from 1.json, the same steps JOURNAL_MIGRATION_1_2 performs on a device,
 * and checks the result against a fresh v2 database built from 2.json.
 */
class JournalDbV2MigrationTest {

    private val connection = JournalSchemaFixture.open(1)

    @After
    fun close() = connection.close()

    @Test
    fun migration_sql_creates_exactly_the_exported_v2_table() {
        val migrationCreate = JournalDbMigrationSql.V1_TO_V2_TABLE.first()
            .replace("journal_doc_v2", "journal_doc")
            .replace("CREATE TABLE IF NOT EXISTS", "")
        val exportedCreate = JournalSchemaFixture.createSql(2, "journal_doc")
            .replace("CREATE TABLE IF NOT EXISTS", "")
        assertEquals(exportedCreate.trim(), migrationCreate.trim())
    }

    @Test
    fun migrated_table_matches_a_fresh_v2_table_column_for_column() {
        seedV1()
        migrate()
        val fresh = JournalSchemaFixture.open(2)
        fresh.use {
            assertEquals(
                JournalSchemaFixture.columns(it, "journal_doc"),
                JournalSchemaFixture.columns(connection, "journal_doc"),
            )
        }
    }

    @Test
    fun every_page_keeps_its_id_day_timestamps_and_body() {
        seedV1()
        migrate()
        val rows = query("SELECT id, day, createdAt, updatedAt, body FROM journal_doc ORDER BY id")
        assertEquals(
            listOf(
                listOf("1", "2026-11-01", "100", "101", MARKDOWN_BODY),
                listOf("2", "2026-11-02", "200", "201", "Сегодня хорошо"),
                listOf("5", "2026-11-05", "500", "501", "| a | b |\n|---|---|\n| 1 | 2 |"),
            ),
            rows,
        )
    }

    @Test
    fun previews_are_filled_from_the_bodies() {
        seedV1()
        migrate()
        val previews = query("SELECT body, preview FROM journal_doc ORDER BY id")
        previews.forEach { (body, preview) -> assertEquals(JournalSql.previewOf(body), preview) }
        assertEquals("Title milk done", previews.first()[1])
    }

    @Test
    fun search_finds_migrated_pages() {
        seedV1()
        migrate()
        assertEquals(listOf("2026-11-01"), searchDays(connection, "milk"))
        assertEquals(listOf("2026-11-02"), searchDays(connection, "хорошо"))
    }

    @Test
    fun search_stays_in_sync_after_migration() {
        seedV1()
        migrate()
        assertEquals(1, updateBodyByDay(connection, "2026-11-02", "rainy evening", 999L))
        assertEquals(emptyList<String>(), searchDays(connection, "хорошо"))
        assertEquals(listOf("2026-11-02"), searchDays(connection, "rainy"))
        exec("DELETE FROM journal_doc WHERE day = '2026-11-01'")
        assertEquals(emptyList<String>(), searchDays(connection, "milk"))
        insertV2("2026-11-06", "brand new page")
        assertEquals(listOf("2026-11-06"), searchDays(connection, "brand"))
    }

    @Test
    fun day_stays_unique_after_migration() {
        seedV1()
        migrate()
        assertThrows(SQLException::class.java) { insertV2("2026-11-01", "duplicate") }
    }

    @Test
    fun new_ids_continue_after_the_highest_migrated_id() {
        seedV1()
        migrate()
        insertV2("2026-11-07", "next")
        val id = query("SELECT id FROM journal_doc WHERE day = '2026-11-07'").single().single()
        assertTrue("id $id must be above 5", id.toLong() > 5)
    }

    @Test
    fun an_empty_journal_migrates_cleanly() {
        migrate()
        assertEquals(emptyList<List<String>>(), query("SELECT id FROM journal_doc"))
        insertV2("2026-11-01", "first page")
        assertEquals(listOf("2026-11-01"), searchDays(connection, "first"))
    }

    // ---- helpers ----

    /** Three v1 pages with an id gap, in the v1 column order (body before the timestamps). */
    private fun seedV1() {
        insertV1(1, "2026-11-01", MARKDOWN_BODY, 100, 101)
        insertV1(2, "2026-11-02", "Сегодня хорошо", 200, 201)
        insertV1(5, "2026-11-05", "| a | b |\n|---|---|\n| 1 | 2 |", 500, 501)
    }

    private fun insertV1(id: Long, day: String, body: String, createdAt: Long, updatedAt: Long) {
        connection.prepareStatement(
            "INSERT INTO journal_doc (id, day, body, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?)",
        ).use { st ->
            st.setLong(1, id)
            st.setString(2, day)
            st.setString(3, body)
            st.setLong(4, createdAt)
            st.setLong(5, updatedAt)
            st.executeUpdate()
        }
    }

    private fun insertV2(day: String, body: String) {
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

    /** The JVM twin of JOURNAL_MIGRATION_1_2.migrate: same SQL, same preview function. */
    private fun migrate() {
        connection.createStatement().use { st ->
            JournalDbMigrationSql.V1_TO_V2_TABLE.forEach(st::execute)
        }
        val previews = query(JournalDbMigrationSql.SELECT_BODIES)
            .map { (id, body) -> id.toLong() to JournalSql.previewOf(body) }
        connection.prepareStatement(JournalDbMigrationSql.UPDATE_PREVIEW).use { st ->
            previews.forEach { (id, preview) ->
                st.setString(1, preview)
                st.setLong(2, id)
                st.executeUpdate()
            }
        }
        connection.createStatement().use { st ->
            JournalDbMigrationSql.V1_TO_V2_FTS.forEach(st::execute)
        }
    }

    private fun query(sql: String): List<List<String>> =
        connection.createStatement().executeQuery(sql).use { rs ->
            val columns = rs.metaData.columnCount
            buildList { while (rs.next()) add((1..columns).map { rs.getString(it) }) }
        }

    private fun exec(sql: String) = connection.createStatement().use { it.execute(sql) }

    private fun Connection.use(block: (Connection) -> Unit) = try {
        block(this)
    } finally {
        close()
    }

    private companion object {
        const val MARKDOWN_BODY = "# Title\n- [ ] milk\n- [x] **done**"
    }
}
