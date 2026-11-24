package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class TagCategoryGroupingTest {

    private val categories = listOf(
        TagCategory(id = "PLACE", label = null, sortOrder = 0),
        TagCategory(id = "health", label = "Health", sortOrder = 1),
        TagCategory(id = TagCategory.OTHER_ID, label = null, sortOrder = 2),
    )

    private fun tag(id: Long, categoryId: String) =
        Tag(id = id, name = "t$id", categoryId = categoryId)

    @Test
    fun grouped_by_category_keys_by_category_id_and_keeps_order() {
        val tags = listOf(tag(2, "PLACE"), tag(1, "PLACE"), tag(3, "health"))

        val grouped = tags.groupedByCategory(categories)

        assertEquals(listOf(2L, 1L), grouped["PLACE"]!!.map { it.id })
        assertEquals(listOf(3L), grouped["health"]!!.map { it.id })
    }

    @Test
    fun grouped_by_category_puts_unknown_categories_under_other() {
        val tags = listOf(tag(1, TagCategory.OTHER_ID), tag(2, "deleted-elsewhere"))

        val grouped = tags.groupedByCategory(categories)

        assertEquals(listOf(1L, 2L), grouped[TagCategory.OTHER_ID]!!.map { it.id })
        assertEquals(setOf(TagCategory.OTHER_ID), grouped.keys)
    }

    @Test
    fun sorted_by_display_rank_follows_the_reference_order() {
        val rank = tagDisplayRank(listOf(tag(5, "PLACE"), tag(3, "health"), tag(9, "OTHER")))

        val sorted = listOf(tag(9, "OTHER"), tag(5, "PLACE"), tag(3, "health"))
            .sortedByDisplayRank(rank)

        assertEquals(listOf(5L, 3L, 9L), sorted.map { it.id })
    }

    @Test
    fun sorted_by_display_rank_puts_unranked_tags_last_in_their_order() {
        val rank = tagDisplayRank(listOf(tag(1, "PLACE")))

        val sorted = listOf(tag(8, "PLACE"), tag(7, "PLACE"), tag(1, "PLACE"))
            .sortedByDisplayRank(rank)

        assertEquals(listOf(1L, 8L, 7L), sorted.map { it.id })
    }
}
