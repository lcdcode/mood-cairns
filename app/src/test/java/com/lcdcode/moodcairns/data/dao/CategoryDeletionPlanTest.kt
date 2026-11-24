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
    fun empty_category_plans_nothing() {
        val plan = planCategoryDeletion(emptyList(), listOf(other(9, "Misc", 0)))

        assertTrue(plan.moves.isEmpty())
        assertTrue(plan.merges.isEmpty())
    }

    @Test
    fun tags_append_after_other_in_their_current_order() {
        val plan = planCategoryDeletion(
            tags = listOf(tag(1, "Yoga", sortOrder = 1), tag(2, "Gym", sortOrder = 0)),
            otherTags = listOf(other(9, "Misc", sortOrder = 4)),
        )

        assertEquals(listOf(TagMove(2, 5), TagMove(1, 6)), plan.moves)
        assertTrue(plan.merges.isEmpty())
    }

    @Test
    fun empty_other_starts_at_zero() {
        val plan = planCategoryDeletion(listOf(tag(1, "Gym", sortOrder = 3)), emptyList())

        assertEquals(listOf(TagMove(1, 0)), plan.moves)
    }

    @Test
    fun same_name_as_an_other_tag_merges_instead_of_moving() {
        val plan = planCategoryDeletion(
            tags = listOf(tag(1, "Gym", sortOrder = 0), tag(2, "Yoga", sortOrder = 1)),
            otherTags = listOf(other(9, "Gym", sortOrder = 0)),
        )

        assertEquals(listOf(TagMerge(fromTagId = 1, intoTagId = 9)), plan.merges)
        assertEquals(listOf(TagMove(2, 1)), plan.moves)
    }

    @Test
    fun name_match_is_case_sensitive_like_the_unique_index() {
        val plan = planCategoryDeletion(
            tags = listOf(tag(1, "gym", sortOrder = 0)),
            otherTags = listOf(other(9, "Gym", sortOrder = 0)),
        )

        assertEquals(listOf(TagMove(1, 1)), plan.moves)
        assertTrue(plan.merges.isEmpty())
    }

    @Test
    fun sort_order_ties_break_by_name() {
        val plan = planCategoryDeletion(
            tags = listOf(tag(1, "yoga", sortOrder = 0), tag(2, "Gym", sortOrder = 0)),
            otherTags = emptyList(),
        )

        assertEquals(listOf(2L, 1L), plan.moves.map { it.tagId })
    }
}
