package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.entity.BuiltInTagCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
 * Exercises the v5 -> v6 migration (tag_category replaces tag_category_label, plus
 * tag.columnIndex) against pure JDBC SQLite, pinned to the exported 6.json so an
 * entity change without a matching migration fails here instead of on a device.
 */
class MoodDbV6MigrationTest {

    private val columnIndexSql = ", `columnIndex` INTEGER NOT NULL DEFAULT 0)"

    @Test
    fun categoryTableSql_matchesExportedRoomSchema() {
        assertEquals(
            createSqlFromSchema("6.json", "tag_category"),
            MoodDbMigrationSql.V5_TO_V6.first(),
        )
    }

    @Test
    fun tagTable_isV5PlusColumnIndex() {
        val v6TagSql = createSqlFromSchema("6.json", "tag")
        assertTrue("6.json tag must end with columnIndex", v6TagSql.endsWith(columnIndexSql))
        assertEquals(
            createSqlFromSchema("5.json", "tag").removeSuffix(")"),
            v6TagSql.removeSuffix(columnIndexSql),
        )
        assertEquals(
            indexSqlFromSchema("5.json", "tag", "index_tag_name_category"),
            indexSqlFromSchema("6.json", "tag", "index_tag_name_category"),
        )
    }

    @Test
    fun otherTables_areUnchangedInV6() {
        for (table in listOf("scale", "entry", "entry_value", "entry_tag")) {
            assertEquals(
                "$table must not change in v6",
                createSqlFromSchema("5.json", table),
                createSqlFromSchema("6.json", table),
            )
        }
    }

    @Test
    fun migration_seedsBuiltInsInOrder_andKeepsCustomNames() {
        connectV5().use { c ->
            exec(c, "INSERT INTO tag_category_label (category, label) VALUES ('PLACE', 'Spots')")

            runStatements(c, MoodDbMigrationSql.V5_TO_V6)

            val sql = "SELECT id, label, sortOrder FROM tag_category ORDER BY sortOrder"
            val rows = query(c, sql) {
                Triple(it.getString(1), it.getString(2), it.getInt(3))
            }
            val expected = BuiltInTagCategory.entries.map { builtIn ->
                val label = if (builtIn == BuiltInTagCategory.PLACE) "Spots" else null
                Triple(builtIn.id, label, builtIn.ordinal)
            }
            assertEquals(expected, rows)
        }
    }

    @Test
    fun migration_clearsOthersName_becauseOtherCanNoLongerBeRenamed() {
        connectV5().use { c ->
            exec(c, "INSERT INTO tag_category_label VALUES ('OTHER', 'Exercises')")

            runStatements(c, MoodDbMigrationSql.V5_TO_V6)

            assertEquals(
                0,
                count(c, "SELECT COUNT(*) FROM tag_category WHERE label IS NOT NULL"),
            )
        }
    }

    @Test
    fun migration_dropsLabelTable() {
        connectV5().use { c ->
            runStatements(c, MoodDbMigrationSql.V5_TO_V6)

            assertFalse(
                "tag_category_label must be gone",
                rowExists(c, "SELECT 1 FROM sqlite_master WHERE name = 'tag_category_label'"),
            )
        }
    }

    @Test
    fun migration_keepsTags_withColumnIndexZero() {
        connectV5().use { c ->
            exec(
                c,
                "INSERT INTO tag (id, name, category, sortOrder) VALUES (7, 'Home', 'PLACE', 3)",
            )

            runStatements(c, MoodDbMigrationSql.V5_TO_V6)

            val sql = "SELECT name, category, sortOrder, columnIndex FROM tag WHERE id = 7"
            val tag = query(c, sql) {
                listOf(it.getString(1), it.getString(2), it.getInt(3), it.getInt(4))
            }.single()
            assertEquals(listOf("Home", "PLACE", 3, 0), tag)
        }
    }

    @Test
    fun columnIndex_isNotNullWithDefaultZero() {
        connectV5().use { c ->
            runStatements(c, MoodDbMigrationSql.V5_TO_V6)

            val info = query(c, "PRAGMA table_info(tag)") {
                Triple(it.getString("name"), it.getInt("notnull") == 1, it.getString("dflt_value"))
            }.single { it.first == "columnIndex" }
            assertTrue("columnIndex must be NOT NULL", info.second)
            assertEquals("0", info.third)
        }
    }

    @Test
    fun fullUpgradeChain_seedsEachTagAndCategoryOnce() {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            exec(c, createSqlFromSchema("1.json", "scale"))
            runStatements(c, MoodDbMigrationSql.V1_TO_V2)
            runStatements(c, MoodDbMigrationSql.seedTagInserts(SeedTags.tags))
            runStatements(c, MoodDbMigrationSql.V2_TO_V3)
            runStatements(c, MoodDbMigrationSql.V3_TO_V4)
            runStatements(c, MoodDbMigrationSql.seedTagInserts(SeedTags.moodTags))
            runStatements(c, MoodDbMigrationSql.V4_TO_V5)
            runStatements(c, MoodDbMigrationSql.V5_TO_V6)

            assertEquals(SeedTags.tags.size, count(c, "SELECT COUNT(*) FROM tag"))
            assertEquals(SeedTagCategories.all.size, count(c, "SELECT COUNT(*) FROM tag_category"))
            assertEquals(0, count(c, "SELECT COUNT(*) FROM tag_category WHERE label IS NOT NULL"))
            assertEquals(
                "every seeded tag must belong to a seeded category",
                0,
                count(
                    c,
                    "SELECT COUNT(*) FROM tag WHERE category NOT IN (SELECT id FROM tag_category)",
                ),
            )
        }
    }

    @Test
    fun seededCategories_areTheBuiltInsInOrder_withNoCustomNames() {
        assertEquals(BuiltInTagCategory.entries.map { it.id }, SeedTagCategories.all.map { it.id })
        val seeded = SeedTagCategories.all
        assertEquals(seeded.indices.toList(), seeded.map { it.sortOrder })
        assertTrue(SeedTagCategories.all.all { it.label == null })
    }

    // ---- helpers ----

    /** A v5 database: tag (with its unique index) and tag_category_label, from 5.json. */
    private fun connectV5(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec(c, createSqlFromSchema("5.json", "tag"))
        exec(c, indexSqlFromSchema("5.json", "tag", "index_tag_name_category"))
        exec(c, createSqlFromSchema("5.json", "tag_category_label"))
        return c
    }

    private fun entityFromSchema(fileName: String, table: String): JsonObject = schemaFile(fileName)
        .let { Json.parseToJsonElement(it.readText()) }
        .jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        .map { it.jsonObject }
        .first { it["tableName"]!!.jsonPrimitive.content == table }

    private fun createSqlFromSchema(fileName: String, table: String): String =
        entityFromSchema(fileName, table)["createSql"]!!.jsonPrimitive.content
            .replace("\${TABLE_NAME}", table)

    private fun indexSqlFromSchema(fileName: String, table: String, indexName: String): String =
        entityFromSchema(fileName, table)["indices"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["name"]!!.jsonPrimitive.content == indexName }["createSql"]!!
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

    private fun rowExists(c: Connection, sql: String): Boolean =
        c.createStatement().executeQuery(sql).use { rs -> rs.next() }

    private fun <T> query(c: Connection, sql: String, row: (java.sql.ResultSet) -> T): List<T> =
        c.createStatement().executeQuery(sql).use { rs ->
            buildList { while (rs.next()) add(row(rs)) }
        }
}
