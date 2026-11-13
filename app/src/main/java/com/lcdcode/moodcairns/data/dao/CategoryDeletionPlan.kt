package com.lcdcode.moodcairns.data.dao

import com.lcdcode.moodcairns.data.entity.Tag

/** A tag moving into Other at [sortOrder]. */
data class TagMove(val tagId: Long, val sortOrder: Int)

/** A tag folded into the Other tag of the same name, which takes over its entries. */
data class TagMerge(val fromTagId: Long, val intoTagId: Long)

data class CategoryDeletionPlan(val moves: List<TagMove>, val merges: List<TagMerge>)

/**
 * Decides what happens to a deleted category's [tags] when they go to Other.
 *
 * A tag whose name is already used in Other is merged into that tag, since the unique
 * (name, category) index would reject the move. The rest are appended after Other's
 * [otherTags], keeping their current relative order.
 */
fun planCategoryDeletion(tags: List<Tag>, otherTags: List<Tag>): CategoryDeletionPlan {
    val otherByName = otherTags.associateBy { it.name }
    var nextSortOrder = (otherTags.maxOfOrNull { it.sortOrder } ?: -1) + 1
    val moves = mutableListOf<TagMove>()
    val merges = mutableListOf<TagMerge>()
    for (tag in tags.sortedWith(compareBy({ it.sortOrder }, { it.name.lowercase() }))) {
        val sameName = otherByName[tag.name]
        if (sameName != null) {
            merges += TagMerge(fromTagId = tag.id, intoTagId = sameName.id)
        } else {
            moves += TagMove(tagId = tag.id, sortOrder = nextSortOrder++)
        }
    }
    return CategoryDeletionPlan(moves, merges)
}
