package com.lcdcode.moodcairns.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Exercises the v2-plaintext → split-databases migration data flow against a
 * pure JDBC SQLite engine — same engine Room uses on Android, so column
 * widening, FK rules, and explicit-id INSERTs behave identically.
 *
 * What this verifies:
 *  1. Legacy schemas at both Room user_version 1 (INTEGER scale.step) and 2
 *     (REAL scale.step) are readable through the same SELECT statements
 *     [LegacyDatabaseMigration] issues.
 *  2. Target schemas accept explicit-id INSERTs, preserving the original
 *     primary keys so the entry_value FK references survive the split.
 *  3. Row counts on the destination side match what was read from the source.
 *  4. FK enforcement still blocks deletes of referenced scales after the move.
 *
 * The actual app-side migration calls a SQLCipher-encrypted MoodDatabase; the
 * encryption layer is verified by manual / instrumented testing because
 * SQLCipher's native library isn't available to the JVM test runtime.
 */
class LegacyMigrationTest {

    @Test
    fun readsV2Schema_andCopiesAllRowsAcrossSplit() {
        connectMemory().use { source ->
            createV2Schema(source)
            // Two scales, two windows, two entries with one value each.
            insertScale(source, id = 1, name = "Happiness", step = 1.0)
            insertScale(source, id = 2, name = "Sleep", step = 0.5)
            insertWindow(source, id = 1, label = "Morning")
            insertWindow(source, id = 2, label = "Evening")
            insertEntry(source, id = 10, recordedAt = 1_700_000_000_000L)
            insertEntry(source, id = 11, recordedAt = 1_700_086_400_000L)
            insertValue(source, entryId = 10, scaleId = 1, value = 7.5)
            insertValue(source, entryId = 11, scaleId = 2, value = 6.0)

            val readScales = readScales(source)
            val readWindows = readWindows(source)
            val readEntries = readEntries(source)
            val readValues = readValues(source)
            assertEquals(2, readScales.size)
            assertEquals(2, readWindows.size)
            assertEquals(2, readEntries.size)
            assertEquals(2, readValues.size)
            // Fractional step survives the read path (REAL column).
            assertEquals(0.5, readScales.first { it.id == 2L }.step, 1e-9)

            connectMemory().use { schedule ->
                createScheduleTargetSchema(schedule)
                for (w in readWindows) {
                    schedule.prepareStatement(
                        "INSERT INTO prompt_window (id, label, slot, startTime, endTime, enabled) VALUES (?,?,?,?,?,?)",
                    ).use { ps ->
                        ps.setLong(1, w.id); ps.setString(2, w.label); ps.setString(3, w.slot)
                        ps.setString(4, w.startTime); ps.setString(5, w.endTime); ps.setInt(6, w.enabled)
                        ps.executeUpdate()
                    }
                }
                assertEquals(2, count(schedule, "prompt_window"))
                // Ids preserved verbatim — critical for foreign-key references.
                schedule.createStatement().executeQuery("SELECT id FROM prompt_window ORDER BY id").use { rs ->
                    rs.next(); assertEquals(1L, rs.getLong(1))
                    rs.next(); assertEquals(2L, rs.getLong(1))
                }
            }

            connectMemory().use { mood ->
                createMoodTargetSchema(mood)
                for (s in readScales) {
                    mood.prepareStatement(
                        """INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb,
                            isBuiltIn, archived, sortOrder) VALUES (?,?,?,?,?,?,?,?,?)""",
                    ).use { ps ->
                        ps.setLong(1, s.id); ps.setString(2, s.name)
                        ps.setInt(3, s.minValue); ps.setInt(4, s.maxValue)
                        ps.setDouble(5, s.step); ps.setInt(6, s.colorArgb)
                        ps.setInt(7, s.isBuiltIn); ps.setInt(8, s.archived); ps.setInt(9, s.sortOrder)
                        ps.executeUpdate()
                    }
                }
                for (e in readEntries) {
                    mood.prepareStatement(
                        "INSERT INTO entry (id, recordedAt, slot, promptWindowId, note) VALUES (?,?,?,?,?)",
                    ).use { ps ->
                        ps.setLong(1, e.id); ps.setLong(2, e.recordedAt); ps.setString(3, e.slot)
                        ps.setNull(4, java.sql.Types.INTEGER); ps.setNull(5, java.sql.Types.VARCHAR)
                        ps.executeUpdate()
                    }
                }
                for (v in readValues) {
                    mood.prepareStatement(
                        "INSERT INTO entry_value (entryId, scaleId, value) VALUES (?,?,?)",
                    ).use { ps ->
                        ps.setLong(1, v.entryId); ps.setLong(2, v.scaleId); ps.setDouble(3, v.value)
                        ps.executeUpdate()
                    }
                }
                assertEquals(2, count(mood, "scale"))
                assertEquals(2, count(mood, "entry"))
                assertEquals(2, count(mood, "entry_value"))
                // RESTRICT FK on entry_value.scaleId persists after the move.
                mood.createStatement().execute("PRAGMA foreign_keys = ON")
                var blocked = false
                try {
                    mood.createStatement().execute("DELETE FROM scale WHERE id = 1")
                } catch (_: Exception) {
                    blocked = true
                }
                assertTrue("FK should block deletes of referenced scales", blocked)
            }
        }
    }

    @Test
    fun readsV1Schema_withIntegerStepAndValue() {
        // Pre-MIGRATION_1_2 schema: scale.step was INTEGER, entry_value.value was INTEGER.
        // The legacy reader uses getFloat/getDouble which read both INTEGER and REAL
        // columns transparently — so a v1 user upgrading skipping v2 still works.
        connectMemory().use { source ->
            createV1Schema(source)
            insertV1Scale(source, id = 1, name = "Happiness", step = 1)
            insertEntry(source, id = 10, recordedAt = 1_700_000_000_000L)
            insertV1Value(source, entryId = 10, scaleId = 1, value = 8)

            val scales = readScales(source)
            val values = readValues(source)
            assertEquals(1.0, scales.single().step, 1e-9)
            assertEquals(8.0, values.single().value, 1e-9)
        }
    }

    // ---- helpers ----

    private fun connectMemory(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite::memory:")
    }

    private fun createV2Schema(c: Connection) = c.createStatement().use { st ->
        st.execute(
            """CREATE TABLE scale (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, minValue INTEGER NOT NULL, maxValue INTEGER NOT NULL,
                step REAL NOT NULL, colorArgb INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL,
                archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL
            )""".trimIndent(),
        )
        st.execute("CREATE UNIQUE INDEX index_scale_name ON scale (name)")
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
        st.execute(
            """CREATE TABLE prompt_window (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL,
                slot TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL,
                enabled INTEGER NOT NULL
            )""".trimIndent(),
        )
        st.execute("PRAGMA user_version = 2")
    }

    private fun createV1Schema(c: Connection) = c.createStatement().use { st ->
        st.execute(
            """CREATE TABLE scale (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, minValue INTEGER NOT NULL, maxValue INTEGER NOT NULL,
                step INTEGER NOT NULL, colorArgb INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL,
                archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL
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
                entryId INTEGER NOT NULL, scaleId INTEGER NOT NULL, value INTEGER NOT NULL,
                PRIMARY KEY (entryId, scaleId),
                FOREIGN KEY (entryId) REFERENCES entry(id) ON DELETE CASCADE,
                FOREIGN KEY (scaleId) REFERENCES scale(id) ON DELETE RESTRICT
            )""".trimIndent(),
        )
        st.execute(
            """CREATE TABLE prompt_window (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL,
                slot TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL,
                enabled INTEGER NOT NULL
            )""".trimIndent(),
        )
        st.execute("PRAGMA user_version = 1")
    }

    private fun createScheduleTargetSchema(c: Connection) = c.createStatement().use { st ->
        st.execute(
            """CREATE TABLE prompt_window (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL,
                slot TEXT NOT NULL, startTime TEXT NOT NULL, endTime TEXT NOT NULL,
                enabled INTEGER NOT NULL
            )""".trimIndent(),
        )
    }

    private fun createMoodTargetSchema(c: Connection) = c.createStatement().use { st ->
        st.execute(
            """CREATE TABLE scale (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL, minValue INTEGER NOT NULL, maxValue INTEGER NOT NULL,
                step REAL NOT NULL, colorArgb INTEGER NOT NULL, isBuiltIn INTEGER NOT NULL,
                archived INTEGER NOT NULL, sortOrder INTEGER NOT NULL,
                inverted INTEGER NOT NULL DEFAULT 0
            )""".trimIndent(),
        )
        st.execute("CREATE UNIQUE INDEX index_scale_name ON scale (name)")
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

    private fun insertScale(c: Connection, id: Long, name: String, step: Double) =
        c.prepareStatement(
            """INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb,
                isBuiltIn, archived, sortOrder) VALUES (?,?,?,?,?,?,?,?,?)""",
        ).use { ps ->
            ps.setLong(1, id); ps.setString(2, name); ps.setInt(3, 1); ps.setInt(4, 10)
            ps.setDouble(5, step); ps.setInt(6, 0xF6C453); ps.setInt(7, 0); ps.setInt(8, 0); ps.setInt(9, 0)
            ps.executeUpdate()
        }

    private fun insertV1Scale(c: Connection, id: Long, name: String, step: Int) =
        c.prepareStatement(
            """INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb,
                isBuiltIn, archived, sortOrder) VALUES (?,?,?,?,?,?,?,?,?)""",
        ).use { ps ->
            ps.setLong(1, id); ps.setString(2, name); ps.setInt(3, 1); ps.setInt(4, 10)
            ps.setInt(5, step); ps.setInt(6, 0xF6C453); ps.setInt(7, 0); ps.setInt(8, 0); ps.setInt(9, 0)
            ps.executeUpdate()
        }

    private fun insertWindow(c: Connection, id: Long, label: String) =
        c.prepareStatement(
            "INSERT INTO prompt_window (id, label, slot, startTime, endTime, enabled) VALUES (?,?,?,?,?,?)",
        ).use { ps ->
            ps.setLong(1, id); ps.setString(2, label); ps.setString(3, "MORNING")
            ps.setString(4, "08:00"); ps.setString(5, "10:00"); ps.setInt(6, 1)
            ps.executeUpdate()
        }

    private fun insertEntry(c: Connection, id: Long, recordedAt: Long) =
        c.prepareStatement(
            "INSERT INTO entry (id, recordedAt, slot, promptWindowId, note) VALUES (?,?,?,NULL,NULL)",
        ).use { ps ->
            ps.setLong(1, id); ps.setLong(2, recordedAt); ps.setString(3, "MANUAL")
            ps.executeUpdate()
        }

    private fun insertValue(c: Connection, entryId: Long, scaleId: Long, value: Double) =
        c.prepareStatement("INSERT INTO entry_value (entryId, scaleId, value) VALUES (?,?,?)").use { ps ->
            ps.setLong(1, entryId); ps.setLong(2, scaleId); ps.setDouble(3, value)
            ps.executeUpdate()
        }

    private fun insertV1Value(c: Connection, entryId: Long, scaleId: Long, value: Int) =
        c.prepareStatement("INSERT INTO entry_value (entryId, scaleId, value) VALUES (?,?,?)").use { ps ->
            ps.setLong(1, entryId); ps.setLong(2, scaleId); ps.setInt(3, value)
            ps.executeUpdate()
        }

    private data class ScaleRow(
        val id: Long, val name: String, val minValue: Int, val maxValue: Int,
        val step: Double, val colorArgb: Int, val isBuiltIn: Int,
        val archived: Int, val sortOrder: Int,
    )
    private data class WindowRow(
        val id: Long, val label: String, val slot: String,
        val startTime: String, val endTime: String, val enabled: Int,
    )
    private data class EntryRow(val id: Long, val recordedAt: Long, val slot: String)
    private data class ValueRow(val entryId: Long, val scaleId: Long, val value: Double)

    private fun readScales(c: Connection): List<ScaleRow> {
        val out = mutableListOf<ScaleRow>()
        c.createStatement().executeQuery(
            "SELECT id, name, minValue, maxValue, step, colorArgb, isBuiltIn, archived, sortOrder FROM scale",
        ).use { rs ->
            while (rs.next()) out += ScaleRow(
                rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
                rs.getDouble(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9),
            )
        }
        return out
    }

    private fun readWindows(c: Connection): List<WindowRow> {
        val out = mutableListOf<WindowRow>()
        c.createStatement().executeQuery(
            "SELECT id, label, slot, startTime, endTime, enabled FROM prompt_window",
        ).use { rs ->
            while (rs.next()) out += WindowRow(
                rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getInt(6),
            )
        }
        return out
    }

    private fun readEntries(c: Connection): List<EntryRow> {
        val out = mutableListOf<EntryRow>()
        c.createStatement().executeQuery(
            "SELECT id, recordedAt, slot, promptWindowId, note FROM entry",
        ).use { rs ->
            while (rs.next()) out += EntryRow(rs.getLong(1), rs.getLong(2), rs.getString(3))
        }
        return out
    }

    private fun readValues(c: Connection): List<ValueRow> {
        val out = mutableListOf<ValueRow>()
        c.createStatement().executeQuery(
            "SELECT entryId, scaleId, value FROM entry_value",
        ).use { rs ->
            while (rs.next()) out += ValueRow(rs.getLong(1), rs.getLong(2), rs.getDouble(3))
        }
        return out
    }

    private fun count(c: Connection, table: String): Int {
        c.createStatement().executeQuery("SELECT COUNT(*) FROM $table").use { rs ->
            return if (rs.next()) rs.getInt(1) else 0
        }
    }
}
