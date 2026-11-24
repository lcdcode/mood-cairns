package com.lcdcode.moodcairns.data.dao

import com.lcdcode.moodcairns.data.entity.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrderPlanTest {

    private val other = TagCategory.OTHER_ID
    private val current = listOf("PLACE", "MOOD", "custom", other)

    @Test
    fun full_request_is_stored_as_given() {
        val plan = planCategoryOrder(current, listOf("custom", "PLACE", "MOOD", other))

        assertEquals(listOf("custom", "PLACE", "MOOD", other), plan)
    }

    @Test
    fun other_is_moved_last_wherever_it_is_requested() {
        val plan = planCategoryOrder(current, listOf(other, "MOOD", "PLACE", "custom"))

        assertEquals(listOf("MOOD", "PLACE", "custom", other), plan)
    }

    @Test
    fun omitted_categories_follow_the_requested_in_current_order() {
        val plan = planCategoryOrder(current, listOf("custom"))

        assertEquals(listOf("custom", "PLACE", "MOOD", other), plan)
    }

    @Test
    fun unknown_and_repeated_ids_are_dropped() {
        val plan = planCategoryOrder(current, listOf("MOOD", "deleted", "MOOD", "PLACE"))

        assertEquals(listOf("MOOD", "PLACE", "custom", other), plan)
    }

    @Test
    fun other_missing_from_current_is_not_invented() {
        val plan = planCategoryOrder(listOf("PLACE", "MOOD"), listOf("MOOD", other))

        assertEquals(listOf("MOOD", "PLACE"), plan)
    }

    @Test
    fun empty_current_gives_empty_plan() {
        assertEquals(emptyList<String>(), planCategoryOrder(emptyList(), listOf("PLACE")))
    }
}
