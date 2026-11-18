package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.dao.EntrySql
import com.lcdcode.moodcairns.data.dao.ScaleDayStatusRow
import com.lcdcode.moodcairns.data.repo.DayScaleLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the entry screen's per-day scale query against the v7 tables. A value at a
 * scale's minimum (0h of Sleep) is "deferred", not answered, so Sleep is asked again.
 */
class ScaleDayStatusSqlTest {

    @Test
    fun valueAboveMinimum_isAnswered() {
        withDb { c ->
            insertValue(c, entryId = 10, recordedAt = DAY_START + HOUR, value = 7.5)

            assertEquals(DayScaleLog(answered = setOf(SLEEP_ID)), dayLog(c))
        }
    }

    @Test
    fun valueAtMinimum_isDeferred_soSleepIsAskedAgain() {
        withDb { c ->
            insertValue(c, entryId = 10, recordedAt = DAY_START + HOUR, value = 0.0)

            assertEquals(DayScaleLog(deferred = setOf(SLEEP_ID)), dayLog(c))
        }
    }

    @Test
    fun minimumThenRealAnswer_isAnswered() {
        withDb { c ->
            insertValue(c, entryId = 10, recordedAt = DAY_START + HOUR, value = 0.0)
            insertValue(c, entryId = 11, recordedAt = DAY_START + 2 * HOUR, value = 6.0)

            assertEquals(DayScaleLog(answered = setOf(SLEEP_ID)), dayLog(c))
        }
    }

    @Test
    fun editedEntry_andOtherDays_areIgnored() {
        withDb { c ->
            insertValue(c, entryId = 10, recordedAt = DAY_START + HOUR, value = 8.0)
            insertValue(c, entryId = 11, recordedAt = DAY_START - HOUR, value = 8.0)
            insertValue(c, entryId = 12, recordedAt = DAY_END, value = 8.0)

            assertEquals(DayScaleLog(), dayLog(c, excludeEntryId = 10))
        }
    }

    // ---- helpers ----

    private fun dayLog(c: Connection, excludeEntryId: Long = 0): DayScaleLog {
        val sql = EntrySql.SCALE_DAY_STATUS_BETWEEN
            .replace(":from", DAY_START.toString())
            .replace(":toExclusive", DAY_END.toString())
            .replace(":excludeEntryId", excludeEntryId.toString())
        val rows = c.createStatement().executeQuery(sql).use { rs ->
            buildList {
                while (rs.next()) {
                    add(ScaleDayStatusRow(rs.getLong("scaleId"), rs.getInt("answered") == 1))
                }
            }
        }
        return DayScaleLog.of(rows)
    }

    private fun withDb(block: (Connection) -> Unit) {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            for (table in listOf("scale", "entry", "entry_value")) exec(c, createSql(table))
            exec(c, MoodDbMigrationSql.seedScaleInsert(Seed.sleepScale))
            block(c)
        }
    }

    private fun insertValue(c: Connection, entryId: Long, recordedAt: Long, value: Double) {
        exec(
            c,
            "INSERT INTO entry (id, recordedAt, slot) VALUES ($entryId, $recordedAt, 'MANUAL')",
        )
        exec(c, "INSERT INTO entry_value VALUES ($entryId, $SLEEP_ID, $value)")
    }

    private fun createSql(table: String): String = sequenceOf("schemas", "app/schemas")
        .map { File(it, "com.lcdcode.moodcairns.data.db.MoodDatabase/7.json") }
        .first(File::exists)
        .let { Json.parseToJsonElement(it.readText()) }
        .jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
        .map { it.jsonObject }
        .first { it["tableName"]!!.jsonPrimitive.content == table }["createSql"]!!
        .jsonPrimitive.content
        .replace("\${TABLE_NAME}", table)

    private fun exec(c: Connection, sql: String) = c.createStatement().use { it.execute(sql) }

    private companion object {
        /** The seed insert lets SQLite assign the id; the first row gets 1. */
        const val SLEEP_ID = 1L
        const val HOUR = 3_600_000L
        const val DAY_START = 1_763_337_600_000L
        const val DAY_END = DAY_START + 24 * HOUR
    }
}
