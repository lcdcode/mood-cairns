package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.repo.buildFtsQuery
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/** JVM SQLite helpers for the journal database, built from Room's exported schemas. */
internal object JournalSchemaFixture {

    /** A fresh in-memory database with every table, index and FTS trigger of [version]. */
    fun open(version: Int): Connection {
        Class.forName("org.sqlite.JDBC")
        val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        connection.createStatement().use { st -> statements(version).forEach(st::execute) }
        return connection
    }

    fun createSql(version: Int, table: String): String =
        entities(version).first { it.tableName == table }["createSql"]!!.jsonPrimitive.content
            .withTableName(table)

    /** Runs the DAO's search SQL; returns the matching days, newest first. */
    fun searchDays(connection: Connection, raw: String): List<String> =
        search(connection, raw).map { it.first }

    /** Runs the DAO's search SQL; returns (day, snippet) rows. */
    fun search(connection: Connection, raw: String): List<Pair<String, String>> {
        val query = buildFtsQuery(raw)
        if (query.isEmpty()) return emptyList()
        return connection.prepareStatement(JournalSql.SEARCH.replace(":query", "?")).use { st ->
            st.setString(1, query)
            st.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getString("day") to rs.getString("snippet")) }
            }
        }
    }

    /** (name, type, notNull, primaryKey) per column, in table order. */
    fun columns(connection: Connection, table: String): List<List<Any>> =
        connection.createStatement().executeQuery("PRAGMA table_info($table)").use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        listOf(
                            rs.getString("name"),
                            rs.getString("type"),
                            rs.getInt("notnull"),
                            rs.getInt("pk"),
                        ),
                    )
                }
            }
        }

    fun updateBodyByDay(connection: Connection, day: String, body: String, updatedAt: Long): Int =
        connection.prepareStatement(
            JournalSql.UPDATE_BODY_BY_DAY
                .replace(":body", "?").replace(":preview", "?")
                .replace(":updatedAt", "?").replace(":day", "?"),
        ).use { st ->
            st.setString(1, body)
            st.setString(2, JournalSql.previewOf(body))
            st.setLong(3, updatedAt)
            st.setString(4, day)
            st.executeUpdate()
        }

    private fun statements(version: Int): List<String> = entities(version).flatMap { entity ->
        buildList {
            add(entity["createSql"]!!.jsonPrimitive.content)
            entity["indices"]?.jsonArray?.forEach {
                add(it.jsonObject["createSql"]!!.jsonPrimitive.content)
            }
            entity["contentSyncTriggers"]?.jsonArray?.forEach { add(it.jsonPrimitive.content) }
        }.map { it.withTableName(entity.tableName) }
    }

    private val JsonObject.tableName: String get() = this["tableName"]!!.jsonPrimitive.content

    private fun String.withTableName(table: String) = replace("\${TABLE_NAME}", table)

    private fun entities(version: Int): List<JsonObject> = sequenceOf("schemas", "app/schemas")
        .map { File(it, "com.lcdcode.moodcairns.data.db.JournalDatabase/$version.json") }
        .firstOrNull(File::exists)
        ?.let { Json.parseToJsonElement(it.readText()) }
        ?.jsonObject?.get("database")?.jsonObject?.get("entities")?.jsonArray
        ?.map { it.jsonObject }
        ?: error("JournalDatabase/$version.json not found; run :app:kspDebugKotlin to export it")
}
