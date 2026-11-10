package com.lcdcode.moodcairns.data.dao

import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the deletion SQL from [TagCategorySql] on sqlite-jdbc with foreign keys on, in
 * the same order as [TagCategoryDao.deleteMovingTagsToOther], against tables created
 * from the exported 6.json.
 */
class CategoryDeletionSqlTest {

    private val other = TagCategory.OTHER_ID

    @Test
    fun deletingACategory_movesAndMergesTags_keepingEntryLinks() {
        connectV6().use { c ->
            exec(c, "INSERT INTO tag_category VALUES ('health', 'Health', 0), ('$other', NULL, 1)")
            insertTag(c, Tag(id = 1, name = "Gym", categoryId = "health", sortOrder = 0))
            insertTag(
                c,
                Tag(id = 2, name = "Yoga", categoryId = "health", sortOrder = 1, columnIndex = 2),
            )
            insertTag(c, Tag(id = 3, name = "Gym", categoryId = other, sortOrder = 0))
            insertTag(c, Tag(id = 4, name = "Walk", categoryId = other, sortOrder = 1))
            for (entryId in 1..3) insertEntry(c, entryId)
            link(c, entryId = 1, tagId = 1)
            link(c, entryId = 2, tagId = 1)
            link(c, entryId = 2, tagId = 3)
            link(c, entryId = 3, tagId = 2)

            deleteMovingTagsToOther(c, "health")

            assertEquals(
                "Gym merged into Other's Gym; Yoga moved after Walk in column 0",
                listOf("3:Gym:OTHER:0:0", "4:Walk:OTHER:1:0", "2:Yoga:OTHER:2:0"),
                strings(
                    c,
                    "SELECT id || ':' || name || ':' || category || ':' || sortOrder || ':' || " +
                        "columnIndex FROM tag ORDER BY sortOrder",
                ),
            )
            assertEquals(
                "both entries keep Gym, with no duplicate link for entry 2",
                listOf("1:3", "2:3", "3:2"),
                strings(c, "SELECT entryId || ':' || tagId FROM entry_tag ORDER BY entryId, tagId"),
            )
            assertEquals(listOf(other), strings(c, "SELECT id FROM tag_category"))
        }
    }

    // ---- helpers ----

    /** Mirrors [TagCategoryDao.deleteMovingTagsToOther] using the shared SQL. */
    private fun deleteMovingTagsToOther(c: Connection, id: String) {
        val plan = planCategoryDeletion(tagsIn(c, id), tagsIn(c, other))
        for (merge in plan.merges) {
            exec(
                c,
                TagCategorySql.COPY_ENTRY_LINKS
                    .replace(":intoTagId", merge.intoTagId.toString())
                    .replace(":fromTagId", merge.fromTagId.toString()),
            )
            exec(c, "DELETE FROM tag WHERE id = ${merge.fromTagId}")
        }
        for (move in plan.moves) {
            exec(
                c,
                TagCategorySql.MOVE_TAG
                    .replace(":categoryId", "'$other'")
                    .replace(":sortOrder", move.sortOrder.toString())
                    .replace(":tagId", move.tagId.toString()),
            )
        }
        exec(c, "DELETE FROM tag_category WHERE id = '$id'")
    }

    private fun connectV6(): Connection {
        Class.forName("org.sqlite.JDBC")
        val c = DriverManager.getConnection("jdbc:sqlite::memory:")
        exec(c, "PRAGMA foreign_keys = ON")
        for (table in listOf("entry", "tag", "entry_tag", "tag_category")) {
            exec(c, createSqlFromSchema(table))
        }
        return c
    }

    private fun tagsIn(c: Connection, categoryId: String): List<Tag> =
        c.createStatement().executeQuery(
            "SELECT id, name, category, sortOrder, columnIndex FROM tag " +
                "WHERE category = '$categoryId'",
        ).use { rs ->
            buildList {
                while (rs.next()) {
                    add(
                        Tag(
                            id = rs.getLong(1),
                            name = rs.getString(2),
                            categoryId = rs.getString(3),
                            sortOrder = rs.getInt(4),
                            columnIndex = rs.getInt(5),
                        ),
                    )
                }
            }
        }

    private fun insertTag(c: Connection, t: Tag) = exec(
        c,
        "INSERT INTO tag (id, name, category, sortOrder, columnIndex) " +
            "VALUES (${t.id}, '${t.name}', '${t.categoryId}', ${t.sortOrder}, ${t.columnIndex})",
    )

    private fun insertEntry(c: Connection, id: Int) =
        exec(c, "INSERT INTO entry (id, recordedAt, slot) VALUES ($id, 0, 'MANUAL')")

    private fun link(c: Connection, entryId: Int, tagId: Int) =
        exec(c, "INSERT INTO entry_tag (entryId, tagId) VALUES ($entryId, $tagId)")

    private fun createSqlFromSchema(table: String): String {
        val entity: JsonObject = schemaFile()
            .let { Json.parseToJsonElement(it.readText()) }
            .jsonObject["database"]!!.jsonObject["entities"]!!.jsonArray
            .map { it.jsonObject }
            .first { it["tableName"]!!.jsonPrimitive.content == table }
        return entity["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
    }

    private fun schemaFile(): File = sequenceOf("schemas", "app/schemas")
        .map { File(it, "com.lcdcode.moodcairns.data.db.MoodDatabase/6.json") }
        .firstOrNull(File::exists)
        ?: error("schemas/.../6.json not found; run :app:kspDebugKotlin to export it")

    private fun exec(c: Connection, sql: String) =
        c.createStatement().use { st -> st.execute(sql) }

    private fun strings(c: Connection, sql: String): List<String> =
        c.createStatement().executeQuery(sql).use { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
}
