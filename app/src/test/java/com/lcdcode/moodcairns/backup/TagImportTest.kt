package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagImportTest {

    private val other = TagCategory.OTHER_ID

    private fun file(
        version: Int,
        categories: List<TagCategoryDto> = emptyList(),
        labels: List<TagCategoryLabelDto> = emptyList(),
    ) = BackupFile(
        schemaVersion = version,
        exportedAt = "2026-11-10T00:00:00Z",
        appVersion = "test",
        scales = emptyList(),
        promptWindows = emptyList(),
        entries = emptyList(),
        tagCategoryLabels = labels,
        tagCategories = categories,
    )

    private fun cat(id: String, label: String? = null, sortOrder: Int = 0) =
        TagCategoryDto(id = id, label = label, sortOrder = sortOrder)

    private fun tag(id: Long, name: String, category: String, column: Int = 0) =
        TagDto(id = id, name = name, category = category, sortOrder = 0, columnIndex = column)

    // ---- resolveCategories ----

    @Test
    fun pre_v3_file_restores_built_ins_with_v2_names() {
        val categories = resolveCategories(
            file(version = 2, labels = listOf(TagCategoryLabelDto("MOOD", "Feelings"))),
        )

        assertEquals(listOf("PLACE", "PERSON", "ACTIVITY", "MOOD", other), categories.map { it.id })
        assertEquals("Feelings", categories.single { it.id == "MOOD" }.label)
    }

    @Test
    fun pre_v3_file_ignores_any_category_list() {
        val categories = resolveCategories(file(version = 2, categories = listOf(cat("x", "X"))))

        assertTrue(categories.none { it.id == "x" })
    }

    @Test
    fun v3_file_keeps_user_categories_and_order() {
        val categories = resolveCategories(
            file(
                version = 3,
                categories = listOf(
                    cat(other, sortOrder = 9),
                    cat("health", "Health", sortOrder = 4),
                    cat("PLACE", sortOrder = 1),
                ),
            ),
        )

        assertEquals(listOf("PLACE", "health", other), categories.map { it.id })
        assertEquals(listOf(0, 1, 2), categories.map { it.sortOrder })
        assertEquals("Health", categories[1].label)
        assertNull(categories[0].label)
    }

    @Test
    fun v3_file_forces_other_last() {
        val categories = resolveCategories(
            file(
                version = 3,
                categories = listOf(cat(other, sortOrder = 0), cat("PLACE", sortOrder = 5)),
            ),
        )

        assertEquals(listOf("PLACE", other), categories.map { it.id })
    }

    @Test
    fun v3_file_adds_missing_other() {
        val categories = resolveCategories(file(version = 3, categories = listOf(cat("PLACE"))))

        assertEquals(listOf("PLACE", other), categories.map { it.id })
        assertNull(categories.last().label)
    }

    @Test
    fun v3_file_with_no_categories_still_has_other() {
        assertEquals(listOf(other), resolveCategories(file(version = 3)).map { it.id })
    }

    @Test
    fun v3_file_drops_unusable_categories() {
        val categories = resolveCategories(
            file(
                version = 3,
                categories = listOf(
                    cat(" ", "Blank id"),
                    cat("x".repeat(TagCategory.MAX_ID_LENGTH + 1), "Long id"),
                    cat("bad\nid", "Control char"),
                    cat("nameless", label = null),
                    cat("blank-name", label = " \t"),
                ),
            ),
        )

        assertEquals(listOf(other), categories.map { it.id })
    }

    @Test
    fun v3_file_sanitizes_names_and_built_ins_may_be_blank() {
        val categories = resolveCategories(
            file(version = 3, categories = listOf(cat("PLACE", " \n "), cat("c", "  Crew\n"))),
        )

        assertNull(categories.single { it.id == "PLACE" }.label)
        assertEquals("Crew", categories.single { it.id == "c" }.label)
    }

    @Test
    fun others_name_is_dropped_from_v2_and_v3_files() {
        val fromV2 = resolveCategories(
            file(version = 2, labels = listOf(TagCategoryLabelDto(other, "Exercises"))),
        )
        val fromV3 = resolveCategories(
            file(version = 3, categories = listOf(cat(other, "Exercises"))),
        )

        assertNull(fromV2.single { it.id == other }.label)
        assertNull(fromV3.single { it.id == other }.label)
    }

    @Test
    fun v3_file_repeated_id_keeps_the_first() {
        val categories = resolveCategories(
            file(version = 3, categories = listOf(cat("c", "First"), cat("c", "Second"))),
        )

        assertEquals("First", categories.single { it.id == "c" }.label)
    }

    // ---- resolveTags ----

    @Test
    fun tags_in_known_categories_keep_category_and_column() {
        val resolved = resolveTags(
            listOf(tag(1, "Gym", "health", column = 2)),
            setOf("health", other),
        )

        val gym = resolved.tags.single()
        assertEquals("health", gym.categoryId)
        assertEquals(2, gym.columnIndex)
        assertTrue(resolved.replacedIds.isEmpty())
    }

    @Test
    fun blank_names_are_dropped() {
        val dtos = listOf(tag(1, "Gym", other), tag(2, "", other), tag(3, "  ", other))

        val resolved = resolveTags(dtos, setOf(other))

        assertEquals(listOf(1L), resolved.tags.map { it.id })
    }

    @Test
    fun negative_column_index_is_clamped_to_zero() {
        val resolved = resolveTags(listOf(tag(1, "Gym", other, column = -3)), setOf(other))

        assertEquals(0, resolved.tags.single().columnIndex)
    }

    @Test
    fun column_index_above_the_max_is_clamped_to_the_last_column() {
        val resolved = resolveTags(listOf(tag(1, "Gym", other, column = 99)), setOf(other))

        assertEquals(Tag.MAX_COLUMNS - 1, resolved.tags.single().columnIndex)
    }

    @Test
    fun orphan_tag_moves_to_other_in_column_zero() {
        val resolved = resolveTags(listOf(tag(1, "Gym", "gone", column = 2)), setOf(other))

        val gym = resolved.tags.single()
        assertEquals(other, gym.categoryId)
        assertEquals(0, gym.columnIndex)
    }

    @Test
    fun orphan_tag_clashing_with_other_tag_merges_into_it() {
        // The orphan comes first in the file, but the tag already in Other must survive.
        val resolved = resolveTags(
            listOf(tag(1, "Gym", "gone"), tag(2, "Gym", other)),
            setOf(other),
        )

        assertEquals(listOf(2L), resolved.tags.map { it.id })
        assertEquals(mapOf(1L to 2L), resolved.replacedIds)
    }

    @Test
    fun same_name_in_different_categories_is_kept() {
        val resolved = resolveTags(
            listOf(tag(1, "Gym", "health"), tag(2, "Gym", other)),
            setOf("health", other),
        )

        assertEquals(listOf(1L, 2L), resolved.tags.map { it.id })
    }

    @Test
    fun repeated_tag_id_keeps_the_first() {
        val resolved = resolveTags(
            listOf(tag(1, "Gym", other), tag(1, "Yoga", other)),
            setOf(other),
        )

        assertEquals(listOf("Gym"), resolved.tags.map { it.name })
        assertTrue(resolved.replacedIds.isEmpty())
    }
}
