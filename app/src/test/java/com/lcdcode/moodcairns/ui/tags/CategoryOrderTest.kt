package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.data.entity.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryOrderTest {

    private fun category(id: String) = TagCategory(id = id, label = null, sortOrder = 0)

    private val other = TagCategory.OTHER_ID
    private val order = listOf("PLACE", "MOOD", "custom", other).map(::category)

    private fun List<TagCategory>?.ids() = this?.map { it.id }

    @Test
    fun withOtherLast_movesOnlyOther_keepingTheRestInOrder() {
        val shuffled = listOf("PLACE", other, "MOOD", "custom").map(::category)

        assertEquals(listOf("PLACE", "MOOD", "custom", other), shuffled.withOtherLast().ids())
    }

    @Test
    fun categoriesMoveAmongThemselves() {
        assertEquals(
            listOf("custom", "PLACE", "MOOD", other),
            order.withCategoryMoved(2, 0).ids(),
        )
        assertEquals(
            listOf("MOOD", "custom", "PLACE", other),
            order.withCategoryMoved(0, 2).ids(),
        )
    }

    @Test
    fun otherCannotBeMoved() {
        assertNull(order.withCategoryMoved(3, 0))
    }

    @Test
    fun nothingCanMoveBelowOther() {
        assertNull(order.withCategoryMoved(2, 3))
    }

    @Test
    fun outOfRangeMoves_giveNull() {
        assertNull(order.withCategoryMoved(0, -1))
        assertNull(order.withCategoryMoved(-1, 0))
    }
}
