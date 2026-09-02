package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.dao.ScaleSql
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Verifies the invert-data remap against pure JDBC SQLite, executing
 * [ScaleSql.REFLECT_VALUES] itself (named parameters swapped for positional
 * ones) so the tested statement cannot drift from the one
 * [com.lcdcode.moodcairns.data.dao.ScaleDao.reflectValuesForScale] issues.
 * Each value v on the toggled scale becomes rangeSum - v (rangeSum =
 * oldMin + oldMax), clamped into the saved range. The reflection must only
 * touch the target scale, be its own inverse when the range is unchanged, and
 * keep values on the step grid.
 */
class ScaleValueRemapTest {

    /** Named params in order of appearance: rangeSum, newMin, newMax, scaleId. */
    private val remapSql = ScaleSql.REFLECT_VALUES.replace(Regex(":\\w+"), "?")

    @Test
    fun remap_reflectsValues_onlyForTargetScale() {
        connectMemory().use { c ->
            createSchema(c)
            insertScale(c, id = 1, name = "Pain", min = 1, max = 10)
            insertScale(c, id = 2, name = "Mood", min = 1, max = 10)
            insertEntry(c, id = 10)
            insertValue(c, entryId = 10, scaleId = 1, value = 3.0)
            insertValue(c, entryId = 10, scaleId = 2, value = 7.0)

            remap(c, scaleId = 1, rangeSum = 11.0, newMin = 1.0, newMax = 10.0)

            assertEquals(8.0, value(c, entryId = 10, scaleId = 1), 1e-6)
            assertEquals(7.0, value(c, entryId = 10, scaleId = 2), 1e-6)
        }
    }

    @Test
    fun remap_handlesNegativeRanges() {
        connectMemory().use { c ->
            createSchema(c)
            insertScale(c, id = 1, name = "Balance", min = -5, max = 5)
            insertEntry(c, id = 10)
            insertValue(c, entryId = 10, scaleId = 1, value = -2.0)
            insertEntry(c, id = 11)
            insertValue(c, entryId = 11, scaleId = 1, value = -5.0)

            remap(c, scaleId = 1, rangeSum = 0.0, newMin = -5.0, newMax = 5.0)

            assertEquals(2.0, value(c, entryId = 10, scaleId = 1), 1e-6)
            assertEquals(5.0, value(c, entryId = 11, scaleId = 1), 1e-6)
        }
    }

    @Test
    fun remap_appliedTwice_restoresOriginals_andStaysOnStepGrid() {
        connectMemory().use { c ->
            createSchema(c)
            // 0..10 with step 2.5: recorded values sit on the quarter-grid.
            insertScale(c, id = 1, name = "Focus", min = 0, max = 10)
            insertEntry(c, id = 10)
            insertValue(c, entryId = 10, scaleId = 1, value = 2.5)

            remap(c, scaleId = 1, rangeSum = 10.0, newMin = 0.0, newMax = 10.0)
            assertEquals(7.5, value(c, entryId = 10, scaleId = 1), 1e-6)

            remap(c, scaleId = 1, rangeSum = 10.0, newMin = 0.0, newMax = 10.0)
            assertEquals(2.5, value(c, entryId = 10, scaleId = 1), 1e-6)
        }
    }

    @Test
    fun remap_clampsIntoNewRange_whenSameSaveShrinksIt() {
        connectMemory().use { c ->
            createSchema(c)
            // Scale was 1..10; the same save flips direction AND shrinks max to 5.
            insertScale(c, id = 1, name = "Pain", min = 1, max = 10)
            insertEntry(c, id = 10)
            insertValue(c, entryId = 10, scaleId = 1, value = 2.0)
            insertEntry(c, id = 11)
            insertValue(c, entryId = 11, scaleId = 1, value = 8.0)

            remap(c, scaleId = 1, rangeSum = 11.0, newMin = 1.0, newMax = 5.0)

            // 11 - 2 = 9 clamps to the new max; 11 - 8 = 3 is in range.
            assertEquals(5.0, value(c, entryId = 10, scaleId = 1), 1e-6)
            assertEquals(3.0, value(c, entryId = 11, scaleId = 1), 1e-6)
        }
    }

    // ---- helpers ----

    private fun remap(
        c: Connection,
        scaleId: Long,
        rangeSum: Double,
        newMin: Double,
        newMax: Double,
    ) = c.prepareStatement(remapSql).use { ps ->
        ps.setDouble(1, rangeSum); ps.setDouble(2, newMin); ps.setDouble(3, newMax)
        ps.setLong(4, scaleId)
        ps.executeUpdate()
    }

    private fun value(c: Connection, entryId: Long, scaleId: Long): Double =
        c.prepareStatement(
            "SELECT value FROM entry_value WHERE entryId = ? AND scaleId = ?",
        ).use { ps ->
            ps.setLong(1, entryId); ps.setLong(2, scaleId)
            ps.executeQuery().use { rs ->
                check(rs.next()) { "no value for entry $entryId scale $scaleId" }
                rs.getDouble(1)
            }
        }

    private fun connectMemory(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite::memory:").also {
            it.createStatement().use { st -> st.execute("PRAGMA foreign_keys = ON") }
        }
    }

    private fun createSchema(c: Connection) = c.createStatement().use { st ->
        st.execute(
            """CREATE TABLE scale (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, minValue INTEGER NOT NULL, maxValue INTEGER NOT NULL,
                step REAL NOT NULL, colorArgb INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL,
                archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL,
                inverted INTEGER NOT NULL DEFAULT 0
            )""".trimIndent(),
        )
        st.execute(
            """CREATE TABLE entry (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                recordedAt INTEGER NOT NULL, slot TEXT NOT NULL,
                promptWindowId INTEGER, note TEXT
            )""".trimIndent(),
        )
        st.execute(
            """CREATE TABLE entry_value (
                entryId INTEGER NOT NULL, scaleId INTEGER NOT NULL, value REAL NOT NULL,
                PRIMARY KEY (entryId, scaleId),
                FOREIGN KEY (entryId) REFERENCES entry(id) ON DELETE CASCADE,
                FOREIGN KEY (scaleId) REFERENCES scale(id) ON DELETE RESTRICT
            )""".trimIndent(),
        )
    }

    private fun insertScale(c: Connection, id: Long, name: String, min: Int, max: Int) =
        c.prepareStatement(
            """INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb,
                isBuiltIn, archived, sortOrder) VALUES (?,?,?,?,?,?,?,?,?)""",
        ).use { ps ->
            ps.setLong(1, id); ps.setString(2, name); ps.setInt(3, min); ps.setInt(4, max)
            ps.setDouble(5, 1.0); ps.setInt(6, 0); ps.setInt(7, 0); ps.setInt(8, 0); ps.setInt(9, 0)
            ps.executeUpdate()
        }

    private fun insertEntry(c: Connection, id: Long) =
        c.prepareStatement(
            "INSERT INTO entry (id, recordedAt, slot, promptWindowId, note) VALUES (?,?,?,NULL,NULL)",
        ).use { ps ->
            ps.setLong(1, id); ps.setLong(2, 1_700_000_000_000L); ps.setString(3, "MANUAL")
            ps.executeUpdate()
        }

    private fun insertValue(c: Connection, entryId: Long, scaleId: Long, value: Double) =
        c.prepareStatement("INSERT INTO entry_value (entryId, scaleId, value) VALUES (?,?,?)").use { ps ->
            ps.setLong(1, entryId); ps.setLong(2, scaleId); ps.setDouble(3, value)
            ps.executeUpdate()
        }
}
