package com.lcdcode.moodcairns.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Exercises the v6 -> v7 migration (scale.oncePerDay plus the seeded Sleep scale)
 * against pure JDBC SQLite, pinned to the exported 7.json so an entity change without
 * a matching migration fails here instead of on a device.
 */
class MoodDbV7MigrationTest {

    private val oncePerDaySql = ", `oncePerDay` INTEGER NOT NULL DEFAULT 0)"

    @Test
    fun scaleTable_isV6PlusOncePerDay() {
        val v7ScaleSql = createSqlFromSchema("7.json", "scale")
        assertTrue("7.json scale must end with oncePerDay", v7ScaleSql.endsWith(oncePerDaySql))
        assertEquals(
            createSqlFromSchema("6.json", "scale").removeSuffix(")"),
            v7ScaleSql.removeSuffix(oncePerDaySql),
        )
        assertEquals(
            indexSqlFromSchema("6.json", "scale", SCALE_NAME_INDEX),
            indexSqlFromSchema("7.json", "scale", SCALE_NAME_INDEX),
        )
    }

    @Test
    fun otherTables_areUnchangedInV7() {
        for (table in listOf("entry", "entry_value", "tag", "entry_tag", "tag_category")) {
            assertEquals(
                "$table must not change in v7",
                createSqlFromSchema("6.json", table),
                createSqlFromSchema("7.json", table),
            )
        }
    }

    @Test
    fun migratedScaleTable_matchesV7Columns() {
        connectV6().use { c ->
            runStatements(c, MoodDbMigrationSql.V6_TO_V7)

            val info = query(c, "PRAGMA table_info(scale)") {
                Triple(it.getString("name"), it.getInt("notnull") == 1, it.getString("dflt_value"))
            }.single { it.first == "oncePerDay" }
            assertTrue("oncePerDay must be NOT NULL", info.second)
            assertEquals("0", info.third)
        }
    }

    @Test
    fun migration_seedsSleepAfterExistingScales_withEverySeedField() {
        connectV6().use { c ->
            insertScale(c, id = 1, name = "Happiness", sortOrder = 0)
            insertScale(c, id = 2, name = "Pain", sortOrder = 7)

            runStatements(c, MoodDbMigrationSql.V6_TO_V7)

            val sleep = Seed.sleepScale
            val row = query(c, "SELECT * FROM scale WHERE name = 'Sleep'") { rs ->
                listOf(
                    rs.getInt("minValue"), rs.getInt("maxValue"), rs.getFloat("step"),
                    rs.getInt("colorArgb"), rs.getInt("isBuiltIn"), rs.getInt("archived"),
                    rs.getInt("sortOrder"), rs.getInt("inverted"), rs.getFloat("defaultValue"),
                    rs.getInt("oncePerDay"),
                )
            }.single()
            val expected = listOf(
                sleep.minValue, sleep.maxValue, sleep.step, sleep.colorArgb, 1, 0,
                8, 0, sleep.defaultValue, 1,
            )
            assertEquals(expected, row)
        }
    }

    @Test
    fun migration_leavesExistingScalesDailyNotOncePerDay() {
        connectV6().use { c ->
            insertScale(c, id = 1, name = "Happiness", sortOrder = 0)

            runStatements(c, MoodDbMigrationSql.V6_TO_V7)

            assertEquals(0, count(c, "SELECT oncePerDay FROM scale WHERE id = 1"))
        }
    }

    @Test
    fun migration_keepsUsersOwnSleepScale_untouched() {
        connectV6().use { c ->
            insertScale(c, id = 4, name = "Sleep", sortOrder = 2)

            runStatements(c, MoodDbMigrationSql.V6_TO_V7)

            val row = query(c, "SELECT id, isBuiltIn, oncePerDay, sortOrder FROM scale") { rs ->
                listOf(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4))
            }.single()
            assertEquals(listOf(4, 0, 0, 2), row)
        }
    }

    @Test
    fun migration_onEmptyScaleTable_seedsSleepFirst() {
        connectV6().use { c ->
            runStatements(c, MoodDbMigrationSql.V6_TO_V7)

            assertEquals(0, count(c, "SELECT sortOrder FROM scale WHERE name = 'Sleep'"))
        }
    }

    @Test
    fun sleepSeed_isIdempotent() {
        connectV6().use { c ->
            runStatements(c, MoodDbMigrationSql.V6_TO_V7)
            exec(c, MoodDbMigrationSql.seedScaleInsert(Seed.sleepScale))

            assertEquals(1, count(c, "SELECT COUNT(*) FROM scale WHERE name = 'Sleep'"))
        }
    }

    @Test
    fun freshInstallSeed_includesSleepOncePerDay_andOtherScalesAreNot() {
        val (oncePerDay, regular) = Seed.scales.partition { it.oncePerDay }
        assertEquals(listOf(Seed.sleepScale), oncePerDay)
        assertEquals(Seed.scales.indices.toList(), Seed.scales.map { it.sortOrder })
        assertTrue(regular.all { it.isBuiltIn })
    }

    // ---- helpers ----

    /** A v6 scale table with its unique name index, from 6.json. */
    private fun connectV6(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec(c, createSqlFromSchema("6.json", "scale"))
        exec(c, indexSqlFromSchema("6.json", "scale", SCALE_NAME_INDEX))
        return c
    }

    private fun insertScale(c: Connection, id: Int, name: String, sortOrder: Int) = exec(
        c,
        "INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb, isBuiltIn, " +
            "archived, sortOrder) VALUES ($id, '$name', 1, 10, 1.0, -1, 0, 0, $sortOrder)",
    )

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

    private fun <T> query(c: Connection, sql: String, row: (ResultSet) -> T): List<T> =
        c.createStatement().executeQuery(sql).use { rs ->
            buildList { while (rs.next()) add(row(rs)) }
        }

    private companion object {
        const val SCALE_NAME_INDEX = "index_scale_name"
    }
}
