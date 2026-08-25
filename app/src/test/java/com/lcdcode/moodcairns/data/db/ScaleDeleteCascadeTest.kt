package com.lcdcode.moodcairns.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Verifies the permanent scale-delete cascade against a pure JDBC SQLite engine
 * (same engine Room uses on Android; SQLCipher's native layer is verified by
 * manual / instrumented testing since it isn't on the JVM test runtime).
 *
 * The statements here are byte-for-byte the SQL issued by
 * [com.lcdcode.moodcairns.data.dao.ScaleDao.deleteScaleCascade] and
 * [com.lcdcode.moodcairns.data.dao.ScaleDao.countEntriesUsing]. The cascade must:
 *  1. Remove the scale row.
 *  2. Delete entries that recorded ONLY the deleted scale (and their values,
 *     via the entry->entry_value CASCADE).
 *  3. Preserve entries shared with other scales, dropping only the deleted
 *     scale's value from them.
 *  4. Leave entries that never used the scale completely untouched.
 */
class ScaleDeleteCascadeTest {

    private val countSql =
        "SELECT COUNT(DISTINCT entryId) FROM entry_value WHERE scaleId = ?"
    private val deleteOnlyUsingSql =
        """
        DELETE FROM entry
        WHERE id IN (SELECT entryId FROM entry_value WHERE scaleId = ?)
          AND id NOT IN (SELECT entryId FROM entry_value WHERE scaleId != ?)
        """.trimIndent()
    private val deleteValuesSql = "DELETE FROM entry_value WHERE scaleId = ?"
    private val deleteScaleSql = "DELETE FROM scale WHERE id = ?"

    @Test
    fun cascade_deletesScale_orphanEntries_andItsValues_butKeepsSharedAndUnrelated() {
        connectMemory().use { c ->
            createSchema(c)
            // Target scale 1 ("Energy") and bystander scale 2 ("Mood").
            insertScale(c, id = 1, name = "Energy")
            insertScale(c, id = 2, name = "Mood")
            // Entry 10: only the target scale -> should vanish entirely.
            insertEntry(c, id = 10)
            insertValue(c, entryId = 10, scaleId = 1, value = 4.0)
            // Entry 11: shared -> survives, keeps Mood, loses Energy.
            insertEntry(c, id = 11)
            insertValue(c, entryId = 11, scaleId = 1, value = 7.0)
            insertValue(c, entryId = 11, scaleId = 2, value = 3.0)
            // Entry 12: never used the target -> untouched.
            insertEntry(c, id = 12)
            insertValue(c, entryId = 12, scaleId = 2, value = 9.0)

            // countEntriesUsing(1) counts every entry holding an Energy value.
            assertEquals(2, count(c, countSql, 1))

            deleteScaleCascade(c, scaleId = 1)

            // Scale gone, bystander remains.
            assertFalse(rowExists(c, "SELECT 1 FROM scale WHERE id = 1"))
            assertTrue(rowExists(c, "SELECT 1 FROM scale WHERE id = 2"))
            // Orphan entry and its value gone.
            assertFalse(rowExists(c, "SELECT 1 FROM entry WHERE id = 10"))
            assertFalse(rowExists(c, "SELECT 1 FROM entry_value WHERE entryId = 10"))
            // Shared entry survives; only the Energy value was dropped.
            assertTrue(rowExists(c, "SELECT 1 FROM entry WHERE id = 11"))
            assertFalse(rowExists(c, "SELECT 1 FROM entry_value WHERE entryId = 11 AND scaleId = 1"))
            assertTrue(rowExists(c, "SELECT 1 FROM entry_value WHERE entryId = 11 AND scaleId = 2"))
            // Unrelated entry untouched.
            assertTrue(rowExists(c, "SELECT 1 FROM entry WHERE id = 12 "))
            assertTrue(rowExists(c, "SELECT 1 FROM entry_value WHERE entryId = 12 AND scaleId = 2"))
            // No value rows for the deleted scale anywhere.
            assertFalse(rowExists(c, "SELECT 1 FROM entry_value WHERE scaleId = 1"))
        }
    }

    @Test
    fun cascade_onScaleWithNoEntries_removesOnlyTheScale() {
        connectMemory().use { c ->
            createSchema(c)
            insertScale(c, id = 1, name = "Unused")
            insertScale(c, id = 2, name = "Mood")
            insertEntry(c, id = 10)
            insertValue(c, entryId = 10, scaleId = 2, value = 5.0)

            assertEquals(0, count(c, countSql, 1))

            deleteScaleCascade(c, scaleId = 1)

            assertFalse(rowExists(c, "SELECT 1 FROM scale WHERE id = 1"))
            assertTrue(rowExists(c, "SELECT 1 FROM entry WHERE id = 10"))
            assertTrue(rowExists(c, "SELECT 1 FROM entry_value WHERE entryId = 10 AND scaleId = 2"))
        }
    }

    // ---- the operation under test ----

    private fun deleteScaleCascade(c: Connection, scaleId: Long) {
        c.prepareStatement(deleteOnlyUsingSql).use { ps ->
            ps.setLong(1, scaleId); ps.setLong(2, scaleId); ps.executeUpdate()
        }
        c.prepareStatement(deleteValuesSql).use { ps ->
            ps.setLong(1, scaleId); ps.executeUpdate()
        }
        c.prepareStatement(deleteScaleSql).use { ps ->
            ps.setLong(1, scaleId); ps.executeUpdate()
        }
    }

    // ---- helpers ----

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

    private fun insertScale(c: Connection, id: Long, name: String) =
        c.prepareStatement(
            """INSERT INTO scale (id, name, minValue, maxValue, step, colorArgb,
                isBuiltIn, archived, sortOrder) VALUES (?,?,?,?,?,?,?,?,?)""",
        ).use { ps ->
            ps.setLong(1, id); ps.setString(2, name); ps.setInt(3, 1); ps.setInt(4, 10)
            ps.setDouble(5, 1.0); ps.setInt(6, 0xF6C453); ps.setInt(7, 0); ps.setInt(8, 0); ps.setInt(9, 0)
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

    private fun count(c: Connection, sql: String, scaleId: Long): Int =
        c.prepareStatement(sql).use { ps ->
            ps.setLong(1, scaleId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }

    private fun rowExists(c: Connection, sql: String): Boolean =
        c.createStatement().executeQuery(sql).use { rs -> rs.next() }
}
