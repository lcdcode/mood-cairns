package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.repo.buildFtsQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Builds the journal database from the exported Room schema (tables, unique index and
 * Room's FTS sync triggers) on JVM SQLite, then runs the DAO's own SQL against it. This
 * covers FTS4 matching, the snippet query, and index sync on insert, update and delete.
 */
class JournalDbSchemaTest {

    private val connection: Connection = openSchemaDb()

    @After
    fun close() = connection.close()

    @Test
    fun `whole word matches and snippet marks the hit`() {
        insert("2026-11-01", "Went for a long walk in the park.")
        val hits = search("park")
        assertEquals(listOf("2026-11-01"), hits.map { it.first })
        val snippet = hits.single().second
        assertTrue(snippet, snippet.contains("${JournalSql.MATCH_START}park${JournalSql.MATCH_END}"))
    }

    @Test
    fun `prefix matches longer words`() {
        insert("2026-11-01", "walking helped")
        assertEquals(listOf("2026-11-01"), search("walk").map { it.first })
    }

    @Test
    fun `cyrillic matching ignores case`() {
        insert("2026-11-01", "Сегодня было ХОРОШО")
        assertEquals(listOf("2026-11-01"), search("хорош").map { it.first })
    }

    @Test
    fun `matching ignores diacritics`() {
        insert("2026-11-01", "Un café con mi madre")
        assertEquals(listOf("2026-11-01"), search("cafe").map { it.first })
    }

    @Test
    fun `all terms must match`() {
        insert("2026-11-01", "rain all day")
        insert("2026-11-02", "rain then sun")
        assertEquals(listOf("2026-11-02"), search("rain sun").map { it.first })
    }

    @Test
    fun `results are newest day first`() {
        insert("2026-10-30", "tea")
        insert("2026-11-02", "tea")
        insert("2026-11-01", "tea")
        assertEquals(listOf("2026-11-02", "2026-11-01", "2026-10-30"), search("tea").map { it.first })
    }

    @Test
    fun `update re-indexes the body`() {
        insert("2026-11-01", "old words")
        connection.prepareStatement(
            JournalSql.UPDATE_BODY_BY_DAY
                .replace(":body", "?").replace(":updatedAt", "?").replace(":day", "?"),
        ).use { st ->
            st.setString(1, "fresh text")
            st.setLong(2, 2_000L)
            st.setString(3, "2026-11-01")
            assertEquals(1, st.executeUpdate())
        }
        assertEquals(emptyList<String>(), search("old").map { it.first })
        assertEquals(listOf("2026-11-01"), search("fresh").map { it.first })
    }

    @Test
    fun `update of a missing day changes nothing`() {
        connection.prepareStatement(
            JournalSql.UPDATE_BODY_BY_DAY
                .replace(":body", "?").replace(":updatedAt", "?").replace(":day", "?"),
        ).use { st ->
            st.setString(1, "x")
            st.setLong(2, 1L)
            st.setString(3, "2026-01-01")
            assertEquals(0, st.executeUpdate())
        }
    }

    @Test
    fun `delete removes the day from the index`() {
        insert("2026-11-01", "secret plans")
        exec("DELETE FROM journal_doc WHERE day = '2026-11-01'")
        assertEquals(emptyList<String>(), search("secret").map { it.first })
    }

    @Test
    fun `query syntax typed by the user cannot break the search`() {
        insert("2026-11-01", "not raining today")
        for (raw in listOf("\"NOT\" (rain", "rain OR", "body:x*", "a NEAR/3 b", "-rain")) {
            search(raw) // must not throw
        }
        assertEquals(listOf("2026-11-01"), search("NOT rain").map { it.first })
    }

    @Test
    fun `only one document per day`() {
        insert("2026-11-01", "first")
        assertThrows(SQLException::class.java) { insert("2026-11-01", "second") }
    }

    @Test
    fun `meta query lists newest first with a bounded preview`() {
        insert("2026-11-01", "a".repeat(JournalSql.PREVIEW_CHARS + 50))
        insert("2026-11-03", "short")
        val rows = connection.createStatement().executeQuery(JournalSql.OBSERVE_META).use { rs ->
            buildList { while (rs.next()) add(rs.getString("day") to rs.getString("preview")) }
        }
        assertEquals(listOf("2026-11-03", "2026-11-01"), rows.map { it.first })
        assertEquals(JournalSql.PREVIEW_CHARS, rows[1].second.length)
    }

    // ---- helpers ----

    private fun insert(day: String, body: String) {
        connection.prepareStatement(
            "INSERT INTO journal_doc (day, body, createdAt, updatedAt) VALUES (?, ?, 1, 1)",
        ).use { st ->
            st.setString(1, day)
            st.setString(2, body)
            st.executeUpdate()
        }
    }

    /** Runs the DAO's search SQL; returns (day, snippet) rows. */
    private fun search(raw: String): List<Pair<String, String>> {
        val query = buildFtsQuery(raw)
        if (query.isEmpty()) return emptyList()
        return connection.prepareStatement(JournalSql.SEARCH.replace(":query", "?")).use { st ->
            st.setString(1, query)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString("day") to rs.getString("snippet")) }
            }
        }
    }

    private fun exec(sql: String) = connection.createStatement().use { it.execute(sql) }

    private fun openSchemaDb(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        val statements = schemaEntities().flatMap { entity ->
            val table = entity["tableName"]!!.jsonPrimitive.content
            buildList {
                add(entity["createSql"]!!.jsonPrimitive.content)
                entity["indices"]?.jsonArray?.forEach {
                    add(it.jsonObject["createSql"]!!.jsonPrimitive.content)
                }
                entity["contentSyncTriggers"]?.jsonArray?.forEach { add(it.jsonPrimitive.content) }
            }.map { it.replace("\${TABLE_NAME}", table) }
        }
        c.createStatement().use { st -> statements.forEach(st::execute) }
        return c
    }

    private fun schemaEntities(): List<JsonObject> = sequenceOf("schemas", "app/schemas")
        .map { File(it, "com.lcdcode.moodcairns.data.db.JournalDatabase/1.json") }
        .firstOrNull(File::exists)
        ?.let { Json.parseToJsonElement(it.readText()) }
        ?.jsonObject?.get("database")?.jsonObject?.get("entities")?.jsonArray
        ?.map { it.jsonObject }
        ?: error("JournalDatabase/1.json not found; run :app:kspDebugKotlin to export it")
}
