package com.lcdcode.moodcairns.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Exercises the v4 -> v5 migration (the tag_category_label table) against pure
 * JDBC SQLite, pinned to the exported 5.json so an entity change without a
 * matching migration fails here instead of on a device.
 */
class MoodDbV5MigrationTest {

    private val labelTable = "tag_category_label"

    @Test
    fun migrationSql_matchesExportedRoomSchema() {
        assertEquals(listOf(createSqlFromSchema("5.json", labelTable)), MoodDbMigrationSql.V4_TO_V5)
    }

    @Test
    fun existingTables_areUnchangedInV5() {
        for (table in listOf("scale", "entry", "entry_value", "tag", "entry_tag")) {
            assertEquals(
                "$table must not change in v5",
                createSqlFromSchema("4.json", table),
                createSqlFromSchema("5.json", table),
            )
        }
    }

    @Test
    fun migration_createsEmptyTable_andKeepsTags() {
        connectV4().use { c ->
            exec(c, "INSERT INTO tag (name, category, sortOrder) VALUES ('Home', 'PLACE', 0)")

            runStatements(c, MoodDbMigrationSql.V4_TO_V5)

            assertEquals(0, count(c, "SELECT COUNT(*) FROM $labelTable"))
            assertEquals(1, count(c, "SELECT COUNT(*) FROM tag"))
        }
    }

    @Test
    fun migration_isIdempotent() {
        connectV4().use { c ->
            runStatements(c, MoodDbMigrationSql.V4_TO_V5)
            exec(c, "INSERT INTO $labelTable (category, label) VALUES ('PLACE', 'Spots')")

            runStatements(c, MoodDbMigrationSql.V4_TO_V5)

            assertEquals(1, count(c, "SELECT COUNT(*) FROM $labelTable"))
        }
    }

    @Test
    fun category_isUnique() {
        connectV4().use { c ->
            runStatements(c, MoodDbMigrationSql.V4_TO_V5)
            exec(c, "INSERT INTO $labelTable (category, label) VALUES ('PLACE', 'Spots')")

            val duplicateRejected = runCatching {
                exec(c, "INSERT INTO $labelTable (category, label) VALUES ('PLACE', 'Again')")
            }.exceptionOrNull() is SQLException

            assertTrue("one label per category", duplicateRejected)
        }
    }

    // ---- helpers ----

    /** A v4 database: the tag table, created from the exported 4.json. */
    private fun connectV4(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec(c, createSqlFromSchema("4.json", "tag"))
        return c
    }

    private fun createSqlFromSchema(fileName: String, table: String): String =
        schemaFile(fileName)
            .let { Json.parseToJsonElement(it.readText()) }
            .jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["tableName"]!!.jsonPrimitive.content == table }["createSql"]!!
            .jsonPrimitive.content
            .replace("\${TABLE_NAME}", table)

    private fun schemaFile(fileName: String): File = sequenceOf("schemas", "app/schemas")
        .map { File(it, "com.lcdcode.moodcairns.data.db.MoodDatabase/$fileName") }
        .firstOrNull(File::exists)
        ?: error("schemas/.../$fileName not found; run :app:kspDebugKotlin to export it")

    private fun runStatements(c: Connection, statements: List<String>) =
        c.createStatement().use { st -> statements.forEach(st::execute) }

    private fun exec(c: Connection, sql: String) =
        c.createStatement().use { st -> st.execute(sql) }

    private fun count(c: Connection, sql: String): Int =
        c.createStatement().executeQuery(sql).use { rs -> if (rs.next()) rs.getInt(1) else 0 }
}
