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
    fun noTags_meansNoColumns() {
        assertTrue(emptyList<Tag>().inColumns().isEmpty())
    }

    @Test
    fun allInFirstColumn_isOneColumn() {
        val columns = listOf(tag(1, 0), tag(2, 0), tag(3, 0)).inColumns()

        assertEquals(listOf(listOf(1L, 2L, 3L)), columns.ids())
    }

    @Test
    fun twoColumns_keepEachColumnsOrder() {
        val columns = listOf(tag(1, 0), tag(2, 1), tag(3, 0), tag(4, 1)).inColumns()

        assertEquals(listOf(listOf(1L, 3L), listOf(2L, 4L)), columns.ids())
    }

    @Test
    fun gap_leavesAnEmptyMiddleColumn() {
        val columns = listOf(tag(1, 0), tag(2, 2)).inColumns()

        assertEquals(listOf(listOf(1L), emptyList(), listOf(2L)), columns.ids())
    }

    @Test
    fun onlyLaterColumnsUsed_stillStartsAtTheFirst() {
        assertEquals(listOf(emptyList(), listOf(1L)), listOf(tag(1, 1)).inColumns().ids())
    }

    @Test
    fun outOfRangeColumns_areClamped() {
        val columns = listOf(tag(1, -4), tag(2, Tag.MAX_COLUMNS + 5)).inColumns()

        assertEquals(Tag.MAX_COLUMNS, columns.size)
        assertEquals(listOf(1L), columns.first().map { it.id })
        assertEquals(listOf(2L), columns.last().map { it.id })
    }

    @Test
    fun categoryChange_resetsToFirstColumn() {
        assertEquals(0, columnAfterCategoryChange(2, oldCategoryId = "MOOD", newCategoryId = "X"))
    }

    @Test
    fun sameCategory_keepsTheColumn() {
        assertEquals(2, columnAfterCategoryChange(2, oldCategoryId = "M", newCategoryId = "M"))
    }
}
