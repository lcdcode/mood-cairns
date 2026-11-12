package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.dao.ScaleSql
import com.lcdcode.moodcairns.data.dao.TagSql
import org.junit.Assert.assertEquals
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the usage queries a configuration import relies on against sqlite-jdbc, and pins
 * why that import must update rows in place rather than INSERT OR REPLACE them.
 */
class ConfigUsageSqlTest {

    @Test
    fun scaleUsage_countsEntriesAndValueExtremes_andSkipsUnusedScales() {
        connectMemory().use { c ->
            createSchema(c)
            insertScale(c, id = 1, name = "Mood")
            insertScale(c, id = 2, name = "Unused")
            insertEntry(c, id = 10)
            insertEntry(c, id = 11)
            insertValue(c, entryId = 10, scaleId = 1, value = 2.5)
            insertValue(c, entryId = 11, scaleId = 1, value = 8.0)

            val rows = query(c, ScaleSql.USAGE_STATS) { rs ->
                listOf(
                    rs.getLong("scaleId"),
                    rs.getInt("entryCount"),
                    rs.getFloat("minValue"),
                    rs.getFloat("maxValue"),
                )
            }

            assertEquals(listOf(listOf(1L, 2, 2.5f, 8.0f)), rows)
        }
    }

    @Test
    fun tagUsage_countsDistinctEntries() {
        connectMemory().use { c ->
            createSchema(c)
            insertTag(c, id = 1, name = "Gym")
            insertTag(c, id = 2, name = "Unused")
            insertEntry(c, id = 10)
            insertEntry(c, id = 11)
            linkTag(c, entryId = 10, tagId = 1)
            linkTag(c, entryId = 11, tagId = 1)

            val rows = query(c, TagSql.USAGE_COUNTS) { rs ->
                rs.getLong("tagId") to rs.getInt("entryCount")
            }

            assertEquals(listOf(1L to 2), rows)
        }
    }

    @Test
    fun replacingATag_dropsItsEntryLinks_whileUpdatingKeepsThem() {
        connectMemory().use { c ->
            createSchema(c)
            insertTag(c, id = 1, name = "Gym")
            insertTag(c, id = 2, name = "Home")
            insertEntry(c, id = 10)
            linkTag(c, entryId = 10, tagId = 1)
            linkTag(c, entryId = 10, tagId = 2)

            exec(c, "UPDATE tag SET sortOrder = 5 WHERE id = 1")
            // What @Insert(onConflict = REPLACE) issues: delete + insert, firing the CASCADE.
            exec(
                c,
                "INSERT OR REPLACE INTO tag (id, name, category, sortOrder) " +
                    "VALUES (2, 'Home', 'PLACE', 5)",
            )

            val linked = query(c, "SELECT tagId FROM entry_tag ORDER BY tagId") { it.getLong(1) }
            assertEquals(listOf(1L), linked)
        }
    }

    // ---- helpers ----

    private fun connectMemory(): Connection {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite::memory:").also {
            // Room enables foreign keys; it leaves recursive_triggers off, so this does too.
            exec(it, "PRAGMA foreign_keys = ON")
        }
    }

    /** The columns these queries touch, with the app's foreign keys. */
    private fun createSchema(c: Connection) {
        exec(c, "CREATE TABLE scale (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL)")
        exec(
            c,
            "CREATE TABLE tag (id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, " +
                "category TEXT NOT NULL, sortOrder INTEGER NOT NULL)",
        )
        exec(c, "CREATE TABLE entry (id INTEGER PRIMARY KEY NOT NULL)")
        exec(
            c,
            "CREATE TABLE entry_value (entryId INTEGER NOT NULL, scaleId INTEGER NOT NULL, " +
                "value REAL NOT NULL, PRIMARY KEY (entryId, scaleId), " +
                "FOREIGN KEY (entryId) REFERENCES entry(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (scaleId) REFERENCES scale(id) ON DELETE RESTRICT)",
        )
        exec(
            c,
            "CREATE TABLE entry_tag (entryId INTEGER NOT NULL, tagId INTEGER NOT NULL, " +
                "PRIMARY KEY (entryId, tagId), " +
                "FOREIGN KEY (entryId) REFERENCES entry(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (tagId) REFERENCES tag(id) ON DELETE CASCADE)",
        )
    }

    private fun insertScale(c: Connection, id: Long, name: String) =
        exec(c, "INSERT INTO scale (id, name) VALUES ($id, '$name')")

    private fun insertTag(c: Connection, id: Long, name: String) =
        exec(c, "INSERT INTO tag (id, name, category, sortOrder) VALUES ($id, '$name', 'PLACE', 0)")

    private fun insertEntry(c: Connection, id: Long) =
        exec(c, "INSERT INTO entry (id) VALUES ($id)")

    private fun insertValue(c: Connection, entryId: Long, scaleId: Long, value: Double) =
        exec(
            c,
            "INSERT INTO entry_value (entryId, scaleId, value) " +
                "VALUES ($entryId, $scaleId, $value)",
        )

    private fun linkTag(c: Connection, entryId: Long, tagId: Long) =
        exec(c, "INSERT INTO entry_tag (entryId, tagId) VALUES ($entryId, $tagId)")

    private fun exec(c: Connection, sql: String) {
        c.createStatement().use { it.execute(sql) }
    }

    private fun <T> query(c: Connection, sql: String, row: (java.sql.ResultSet) -> T): List<T> =
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(row(rs)) }
            }
        }
}
