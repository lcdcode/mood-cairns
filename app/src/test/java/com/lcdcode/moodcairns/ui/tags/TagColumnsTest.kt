package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.data.entity.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagColumnsTest {

    private fun tag(id: Long, column: Int) = Tag(
        id = id,
        name = "t$id",
        categoryId = "MOOD",
        sortOrder = id.toInt(),
        columnIndex = column,
    )

    private fun List<List<Tag>>.ids() = map { column -> column.map { it.id } }

    @Test
    fun no_tags_means_no_columns() {
        assertTrue(emptyList<Tag>().inColumns().isEmpty())
    }

    @Test
    fun all_in_first_column_is_one_column() {
        val columns = listOf(tag(1, 0), tag(2, 0), tag(3, 0)).inColumns()

        assertEquals(listOf(listOf(1L, 2L, 3L)), columns.ids())
    }

    @Test
    fun two_columns_keep_each_columns_order() {
        val columns = listOf(tag(1, 0), tag(2, 1), tag(3, 0), tag(4, 1)).inColumns()

        assertEquals(listOf(listOf(1L, 3L), listOf(2L, 4L)), columns.ids())
    }

    @Test
    fun gap_leaves_an_empty_middle_column() {
        val columns = listOf(tag(1, 0), tag(2, 2)).inColumns()

        assertEquals(listOf(listOf(1L), emptyList(), listOf(2L)), columns.ids())
    }

    @Test
    fun only_later_columns_used_still_starts_at_the_first() {
        assertEquals(listOf(emptyList(), listOf(1L)), listOf(tag(1, 1)).inColumns().ids())
    }

    @Test
    fun out_of_range_columns_are_clamped() {
        val columns = listOf(tag(1, -4), tag(2, Tag.MAX_COLUMNS + 5)).inColumns()

        assertEquals(Tag.MAX_COLUMNS, columns.size)
        assertEquals(listOf(1L), columns.first().map { it.id })
        assertEquals(listOf(2L), columns.last().map { it.id })
    }

    @Test
    fun category_change_resets_to_first_column() {
        assertEquals(0, columnAfterCategoryChange(2, oldCategoryId = "MOOD", newCategoryId = "X"))
    }

    @Test
    fun same_category_keeps_the_column() {
        assertEquals(2, columnAfterCategoryChange(2, oldCategoryId = "M", newCategoryId = "M"))
    }
}
