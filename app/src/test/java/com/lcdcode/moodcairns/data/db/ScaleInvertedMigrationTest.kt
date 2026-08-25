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

/**
 * Exercises the v2 -> v3 migration SQL (scale.inverted column) against pure
 * JDBC SQLite, mirroring [MoodDbMigrationTest]. The starting scale table is
 * built from the createSql Room exported into schemas/.../2.json, and a
 * schema-guard pins the migration's ADD COLUMN to 3.json so an entity change
 * without a matching migration fails here before a device would crash with an
 * identity-hash mismatch.
 */
class ScaleInvertedMigrationTest {

    @Test
    fun migration_addsInvertedColumn_notNullDefaultZero() {
        connectV2().use { c ->
            runStatements(c, MoodDbMigrationSql.V2_TO_V3)

            val info = columnInfo(c, "inverted")
            assertEquals("INTEGER", info.type)
            assertTrue("inverted must be NOT NULL", info.notNull)
            assertEquals("0", info.defaultValue)
        }
    }

    @Test
    fun migration_existingRowsReadAsNotInverted() {
        connectV2().use { c ->
            exec(
                c,
                "INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb, " +
                    "isBuiltIn, archived, sortOrder) VALUES (1, 'Happiness', 1, 10, 1.0, 0, 1, 0, 0)",
            )

            runStatements(c, MoodDbMigrationSql.V2_TO_V3)

            val inverted = c.createStatement()
                .executeQuery("SELECT inverted FROM scale WHERE id = 1").use { rs ->
                    check(rs.next()) { "seeded scale row missing after migration" }
                    rs.getInt(1)
                }
            assertEquals(0, inverted)
        }
    }

    @Test
    fun migrationSql_matchesExportedRoomSchema() {
        val scaleCreateSql = scaleCreateSqlFromSchema("3.json")
        assertTrue(
            "3.json scale createSql must declare the migrated column verbatim",
            scaleCreateSql.endsWith(", `inverted` INTEGER NOT NULL DEFAULT 0)"),
        )
        // Everything before the new column must be exactly the v2 table, so the
        // ALTER TABLE path and a fresh v3 create produce the same structure.
        val v2CreateSql = scaleCreateSqlFromSchema("2.json")
        assertEquals(
            v2CreateSql.removeSuffix(")"),
            scaleCreateSql.removeSuffix(", `inverted` INTEGER NOT NULL DEFAULT 0)"),
        )
    }

    // ---- helpers ----

    private data class ColumnInfo(val type: String, val notNull: Boolean, val defaultValue: String?)

    private fun columnInfo(c: Connection, column: String): ColumnInfo =
        c.createStatement().executeQuery("PRAGMA table_info(scale)").use { rs ->
            while (rs.next()) {
                if (rs.getString("name") == column) {
                    return ColumnInfo(
                        type = rs.getString("type"),
                        notNull = rs.getInt("notnull") == 1,
                        defaultValue = rs.getString("dflt_value"),
                    )
                }
            }
            error("column $column not found in scale")
        }

    /** A v2 database: just the scale table, created from the exported 2.json. */
    private fun connectV2(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec(c, scaleCreateSqlFromSchema("2.json"))
        return c
    }

    private fun scaleCreateSqlFromSchema(fileName: String): String {
        val schemaFile = sequenceOf("schemas", "app/schemas")
            .map { File(it, "com.lcdcode.moodcairns.data.db.MoodDatabase/$fileName") }
            .firstOrNull(File::exists)
            ?: error("schemas/.../$fileName not found; run :app:kspDebugKotlin to export it")

        val scale = Json.parseToJsonElement(schemaFile.readText())
            .jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["tableName"]!!.jsonPrimitive.content == "scale" }

        return scale["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", "scale")
    }

    private fun runStatements(c: Connection, statements: List<String>) =
        c.createStatement().use { st -> statements.forEach(st::execute) }

    private fun exec(c: Connection, sql: String) =
        c.createStatement().use { st -> st.execute(sql) }
}
