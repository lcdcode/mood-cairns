package com.lcdcode.moodcairns.data.dao

import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryDeletionPlanTest {

    private fun tag(id: Long, name: String, sortOrder: Int, categoryId: String = "health") =
        Tag(id = id, name = name, categoryId = categoryId, sortOrder = sortOrder)

    private fun other(id: Long, name: String, sortOrder: Int) =
        tag(id, name, sortOrder, TagCategory.OTHER_ID)

    @Test
    fun emptyCategory_plansNothing() {
        val plan = planCategoryDeletion(emptyList(), listOf(other(9, "Misc", 0)))

        assertTrue(plan.moves.isEmpty())
        assertTrue(plan.merges.isEmpty())
    }

    @Test
    fun tags_appendAfterOther_inTheirCurrentOrder() {
        val plan = planCategoryDeletion(
            tags = listOf(tag(1, "Yoga", sortOrder = 1), tag(2, "Gym", sortOrder = 0)),
            otherTags = listOf(other(9, "Misc", sortOrder = 4)),
        )

        assertEquals(listOf(TagMove(2, 5), TagMove(1, 6)), plan.moves)
        assertTrue(plan.merges.isEmpty())
    }

    @Test
    fun emptyOther_startsAtZero() {
        val plan = planCategoryDeletion(listOf(tag(1, "Gym", sortOrder = 3)), emptyList())

        assertEquals(listOf(TagMove(1, 0)), plan.moves)
    }

    @Test
    fun sameNameAsAnOtherTag_mergesInsteadOfMoving() {
        val plan = planCategoryDeletion(
            tags = listOf(tag(1, "Gym", sortOrder = 0), tag(2, "Yoga", sortOrder = 1)),
            otherTags = listOf(other(9, "Gym", sortOrder = 0)),
        )

        assertEquals(listOf(TagMerge(fromTagId = 1, intoTagId = 9)), plan.merges)
        assertEquals(listOf(TagMove(2, 1)), plan.moves)
    }

    @Test
    fun nameMatch_isCaseSensitive_likeTheUniqueIndex() {
        val plan = planCategoryDeletion(
            tags = listOf(tag(1, "gym", sortOrder = 0)),
            otherTags = listOf(other(9, "Gym", sortOrder = 0)),
        )

        assertEquals(listOf(TagMove(1, 1)), plan.moves)
        assertTrue(plan.merges.isEmpty())
    }

    @Test
    fun sortOrderTies_breakByName() {
        val plan = planCategoryDeletion(
            tags = listOf(tag(1, "yoga", sortOrder = 0), tag(2, "Gym", sortOrder = 0)),
            otherTags = emptyList(),
        )

        assertEquals(listOf(2L, 1L), plan.moves.map { it.tagId })
    }
}
