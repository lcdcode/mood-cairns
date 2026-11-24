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
    fun with_other_last_moves_only_other_keeping_the_rest_in_order() {
        val shuffled = listOf("PLACE", other, "MOOD", "custom").map(::category)

        assertEquals(listOf("PLACE", "MOOD", "custom", other), shuffled.withOtherLast().ids())
    }

    @Test
    fun categories_move_among_themselves() {
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
    fun other_cannot_be_moved() {
        assertNull(order.withCategoryMoved(3, 0))
    }

    @Test
    fun nothing_can_move_below_other() {
        assertNull(order.withCategoryMoved(2, 3))
    }

    @Test
    fun out_of_range_moves_give_null() {
        assertNull(order.withCategoryMoved(0, -1))
        assertNull(order.withCategoryMoved(-1, 0))
    }
}
