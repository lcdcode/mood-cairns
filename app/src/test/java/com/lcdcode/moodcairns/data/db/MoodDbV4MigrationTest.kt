package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.entity.TagCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Exercises the v3 -> v4 migration (the optional scale.defaultValue column plus
 * the MOOD tag seeds) against pure JDBC SQLite, mirroring
 * [ScaleInvertedMigrationTest]. The starting tables come from the createSql Room
 * exported into schemas/.../3.json, and schema guards pin the migration to
 * 4.json so an entity change without a matching migration fails here before a
 * device would crash with an identity-hash mismatch.
 */
class MoodDbV4MigrationTest {

    private val defaultValueColumnSql = ", `defaultValue` REAL)"

    @Test
    fun migration_addsDefaultValueColumn_nullableWithNoSqlDefault() {
        connectV3().use { c ->
            runStatements(c, MoodDbMigrationSql.V3_TO_V4)

            val info = columnInfo(c, "scale", "defaultValue")
            assertEquals("REAL", info.type)
            assertFalse("defaultValue must be nullable", info.notNull)
            assertNull("defaultValue must have no SQL default", info.defaultValue)
        }
    }

    @Test
    fun migration_existingScalesHaveNoDefault() {
        connectV3().use { c ->
            insertV3Scale(c, id = 1, name = "Happiness")

            runStatements(c, MoodDbMigrationSql.V3_TO_V4)

            c.createStatement()
                .executeQuery("SELECT defaultValue FROM scale WHERE id = 1").use { rs ->
                    check(rs.next()) { "scale row missing after migration" }
                    rs.getFloat(1)
                    assertTrue("existing scales must read NULL", rs.wasNull())
                }
        }
    }

    @Test
    fun migration_seedsMoodTags() {
        connectV3().use { c ->
            migrate(c)

            assertEquals(
                SeedTags.moodTags.size,
                count(c, "SELECT COUNT(*) FROM tag WHERE category = 'MOOD'"),
            )
            for (seed in SeedTags.moodTags) {
                assertTrue(
                    "missing mood seed ${seed.name}",
                    rowExists(
                        c,
                        "SELECT 1 FROM tag WHERE name = '${seed.name}' AND category = 'MOOD' " +
                            "AND sortOrder = ${seed.sortOrder}",
                    ),
                )
            }
        }
    }

    @Test
    fun migration_seedsNothingForOtherCategory() {
        connectV3().use { c ->
            migrate(c)

            assertEquals(0, count(c, "SELECT COUNT(*) FROM tag WHERE category = 'OTHER'"))
        }
    }

    @Test
    fun moodSeeding_isIdempotent_andKeepsSameNamedUserTags() {
        connectV3().use { c ->
            // The unique index is (name, category), so a user's "Joy" activity
            // must survive alongside the seeded "Joy" mood.
            exec(c, "INSERT INTO tag (name, category, sortOrder) VALUES ('Joy', 'ACTIVITY', 7)")

            migrate(c)
            runStatements(c, MoodDbMigrationSql.seedTagInserts(SeedTags.moodTags))

            assertEquals(
                SeedTags.moodTags.size,
                count(c, "SELECT COUNT(*) FROM tag WHERE category = 'MOOD'"),
            )
            assertTrue(
                "user's same-named tag in another category must be untouched",
                rowExists(
                    c,
                    "SELECT 1 FROM tag WHERE name = 'Joy' AND category = 'ACTIVITY' " +
                        "AND sortOrder = 7",
                ),
            )
        }
    }

    @Test
    fun fullUpgradeChain_seedsEachTagOnce() {
        // A v1 install upgrades 1 -> 2 -> 3 -> 4 in a single open: V1_TO_V2
        // already seeds the mood tags via SeedTags.tags, so MIGRATION_3_4's
        // reseed must not duplicate them.
        connectV1().use { c ->
            runStatements(c, MoodDbMigrationSql.V1_TO_V2)
            runStatements(c, MoodDbMigrationSql.seedTagInserts(SeedTags.tags))
            runStatements(c, MoodDbMigrationSql.V2_TO_V3)
            runStatements(c, MoodDbMigrationSql.V3_TO_V4)
            runStatements(c, MoodDbMigrationSql.seedTagInserts(SeedTags.moodTags))

            assertEquals(SeedTags.tags.size, count(c, "SELECT COUNT(*) FROM tag"))
        }
    }

    @Test
    fun moodSeeds_areAllInTheMoodCategory() {
        assertTrue(
            "SeedTags.moodTags must only hold MOOD tags",
            SeedTags.moodTags.all { it.category == TagCategory.MOOD },
        )
        assertTrue(
            "SeedTags.tags must contain every mood seed",
            SeedTags.tags.containsAll(SeedTags.moodTags),
        )
    }

    @Test
    fun migrationSql_matchesExportedRoomSchema() {
        val scaleCreateSql = createSqlFromSchema("4.json", "scale")
        assertTrue(
            "4.json scale createSql must declare the migrated column verbatim",
            scaleCreateSql.endsWith(defaultValueColumnSql),
        )
        // Everything before the new column must be exactly the v3 table, so the
        // ALTER TABLE path and a fresh v4 create produce the same structure.
        assertEquals(
            createSqlFromSchema("3.json", "scale").removeSuffix(")"),
            scaleCreateSql.removeSuffix(defaultValueColumnSql),
        )
    }

    @Test
    fun tagTable_isUnchangedInV4() {
        // New TagCategory constants are stored as strings, so seeding moods must
        // not have altered the tag table's structure.
        assertEquals(
            createSqlFromSchema("3.json", "tag"),
            createSqlFromSchema("4.json", "tag"),
        )
    }

    // ---- helpers ----

    private data class ColumnInfo(val type: String, val notNull: Boolean, val defaultValue: String?)

    private fun migrate(c: Connection) {
        runStatements(c, MoodDbMigrationSql.V3_TO_V4)
        runStatements(c, MoodDbMigrationSql.seedTagInserts(SeedTags.moodTags))
    }

    private fun columnInfo(c: Connection, table: String, column: String): ColumnInfo =
        c.createStatement().executeQuery("PRAGMA table_info($table)").use { rs ->
            while (rs.next()) {
                if (rs.getString("name") == column) {
                    return ColumnInfo(
                        type = rs.getString("type"),
                        notNull = rs.getInt("notnull") == 1,
                        defaultValue = rs.getString("dflt_value"),
                    )
                }
            }
            error("column $column not found in $table")
        }

    /** A v3 database: the scale and tag tables, created from the exported 3.json. */
    private fun connectV3(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec(c, createSqlFromSchema("3.json", "scale"))
        exec(c, createSqlFromSchema("3.json", "tag"))
        exec(c, indexSqlFromSchema("3.json", "tag", "index_tag_name_category"))
        return c
    }

    /** A v1 database: the pre-tag scale table only, created from the exported 1.json. */
    private fun connectV1(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec(c, createSqlFromSchema("1.json", "scale"))
        return c
    }

    private fun insertV3Scale(c: Connection, id: Long, name: String) = exec(
        c,
        "INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb, isBuiltIn, " +
            "archived, sortOrder, inverted) VALUES ($id, '$name', 1, 10, 1.0, 0, 1, 0, 0, 0)",
    )

    private fun entityFromSchema(fileName: String, table: String) = schemaFile(fileName)
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
}
