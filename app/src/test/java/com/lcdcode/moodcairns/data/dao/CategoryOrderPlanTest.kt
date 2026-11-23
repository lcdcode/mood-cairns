package com.lcdcode.moodcairns.data.dao

import com.lcdcode.moodcairns.data.entity.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryOrderPlanTest {

    private val other = TagCategory.OTHER_ID
    private val current = listOf("PLACE", "MOOD", "custom", other)

    @Test
    fun fullRequest_isStoredAsGiven() {
        val plan = planCategoryOrder(current, listOf("custom", "PLACE", "MOOD", other))

        assertEquals(listOf("custom", "PLACE", "MOOD", other), plan)
    }

    @Test
    fun otherIsMovedLast_wherever_itIsRequested() {
        val plan = planCategoryOrder(current, listOf(other, "MOOD", "PLACE", "custom"))

        assertEquals(listOf("MOOD", "PLACE", "custom", other), plan)
    }

    @Test
    fun omittedCategories_followTheRequested_inCurrentOrder() {
        val plan = planCategoryOrder(current, listOf("custom"))

        assertEquals(listOf("custom", "PLACE", "MOOD", other), plan)
    }

    @Test
    fun unknownAndRepeatedIds_areDropped() {
        val plan = planCategoryOrder(current, listOf("MOOD", "deleted", "MOOD", "PLACE"))

        assertEquals(listOf("MOOD", "PLACE", "custom", other), plan)
    }

    @Test
    fun otherMissingFromCurrent_isNotInvented() {
        val plan = planCategoryOrder(listOf("PLACE", "MOOD"), listOf("MOOD", other))

        assertEquals(listOf("MOOD", "PLACE"), plan)
    }

    @Test
    fun emptyCurrent_givesEmptyPlan() {
        assertEquals(emptyList<String>(), planCategoryOrder(emptyList(), listOf("PLACE")))
    }
}
