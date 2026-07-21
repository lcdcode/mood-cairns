package com.lcdcode.moodcairns.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Exercises the v1 -> v2 migration SQL against pure JDBC SQLite (same engine
 * Room drives on Android; the SQLCipher layer needs manual/instrumented
 * verification). Also pins [MoodDbMigrationSql.V1_TO_V2] to the createSql Room
 * exported into schemas/.../2.json: if the Tag/EntryTag entities change without
 * the migration being updated, the schema-guard test fails before a device
 * would crash with an identity-hash mismatch.
 */
class MoodDbMigrationTest {

    @Test
    fun migration_createsTables_andSeedsDefaults() {
        connectV1().use { c ->
            migrate(c)

            assertTrue(tableExists(c, "tag"))
            assertTrue(tableExists(c, "entry_tag"))
            assertEquals(SeedTags.tags.size, count(c, "SELECT COUNT(*) FROM tag"))

            // Every seed present with its category.
            for (seed in SeedTags.tags) {
                assertTrue(
                    "missing seed ${seed.name}",
                    rowExists(
                        c,
                        "SELECT 1 FROM tag WHERE name = '${seed.name}' " +
                            "AND category = '${seed.category.name}' AND sortOrder = ${seed.sortOrder}",
                    ),
                )
            }
        }
    }

    @Test
    fun seeding_isIdempotent() {
        connectV1().use { c ->
            migrate(c)
            runStatements(c, MoodDbMigrationSql.seedTagInserts(SeedTags.tags))
            assertEquals(SeedTags.tags.size, count(c, "SELECT COUNT(*) FROM tag"))
        }
    }

    @Test
    fun deletingEntry_cascadesEntryTagLinks_butKeepsTag() {
        connectV1().use { c ->
            migrate(c)
            insertEntry(c, id = 10)
            val tagId = firstTagId(c)
            exec(c, "INSERT INTO entry_tag (entryId, tagId) VALUES (10, $tagId)")

            exec(c, "DELETE FROM entry WHERE id = 10")

            assertFalse(rowExists(c, "SELECT 1 FROM entry_tag WHERE entryId = 10"))
            assertTrue(rowExists(c, "SELECT 1 FROM tag WHERE id = $tagId"))
        }
    }

    @Test
    fun deletingTag_cascadesEntryTagLinks_butKeepsEntry() {
        connectV1().use { c ->
            migrate(c)
            insertEntry(c, id = 10)
            val tagId = firstTagId(c)
            exec(c, "INSERT INTO entry_tag (entryId, tagId) VALUES (10, $tagId)")

            exec(c, "DELETE FROM tag WHERE id = $tagId")

            assertFalse(rowExists(c, "SELECT 1 FROM entry_tag WHERE tagId = $tagId"))
            assertTrue(rowExists(c, "SELECT 1 FROM entry WHERE id = 10"))
        }
    }

    @Test
    fun migrationSql_matchesExportedRoomSchema() {
        val schemaFile = sequenceOf("schemas", "app/schemas")
            .map { File(it, "com.lcdcode.moodcairns.data.db.MoodDatabase/2.json") }
            .firstOrNull(File::exists)
            ?: error("schemas/.../2.json not found; run :app:kspDebugKotlin to export it")

        val entities = Json.parseToJsonElement(schemaFile.readText())
            .jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }

        val expected = buildList {
            for (entity in entities) {
                val table = entity["tableName"]!!.jsonPrimitive.content
                if (table != "tag" && table != "entry_tag") continue
                add(entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                entity["indices"]!!.jsonArray.forEach { index ->
                    add(
                        index.jsonObject["createSql"]!!.jsonPrimitive.content
                            .replace("\${TABLE_NAME}", table),
                    )
                }
            }
        }

        assertEquals(expected.toSet(), MoodDbMigrationSql.V1_TO_V2.toSet())
        assertEquals(expected.size, MoodDbMigrationSql.V1_TO_V2.size)
    }

    // ---- helpers ----

    private fun migrate(c: Connection) {
        runStatements(c, MoodDbMigrationSql.V1_TO_V2)
        runStatements(c, MoodDbMigrationSql.seedTagInserts(SeedTags.tags))
    }

    private fun runStatements(c: Connection, statements: List<String>) =
        c.createStatement().use { st -> statements.forEach(st::execute) }

    /** A v1 database: entry table only (scale/entry_value are irrelevant here). */
    private fun connectV1(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        c.createStatement().use { st ->
            st.execute("PRAGMA foreign_keys = ON")
            st.execute(
                """CREATE TABLE entry (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    recordedAt INTEGER NOT NULL, slot TEXT NOT NULL,
                    promptWindowId INTEGER, note TEXT
                )""".trimIndent(),
            )
        }
        return c
    }

    private fun exec(c: Connection, sql: String) =
        c.createStatement().use { st -> st.execute(sql) }

    private fun insertEntry(c: Connection, id: Long) =
        exec(c, "INSERT INTO entry (id, recordedAt, slot) VALUES ($id, 1700000000000, 'MANUAL')")

    private fun firstTagId(c: Connection): Long =
        c.createStatement().executeQuery("SELECT id FROM tag ORDER BY id LIMIT 1").use { rs ->
            check(rs.next()) { "no tags seeded" }
            rs.getLong(1)
        }

    private fun tableExists(c: Connection, name: String): Boolean =
        rowExists(c, "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '$name'")

    private fun count(c: Connection, sql: String): Int =
        c.createStatement().executeQuery(sql).use { rs -> if (rs.next()) rs.getInt(1) else 0 }

    private fun rowExists(c: Connection, sql: String): Boolean =
        c.createStatement().executeQuery(sql).use { rs -> rs.next() }
}
