package com.lcdcode.moodcairns.data.dao

import com.lcdcode.moodcairns.data.entity.TagCategory

/**
 * The full category order to store for a reorder request: the [requested] ids that
 * exist in [current], then any existing ids the request left out (in [current] order),
 * with Other always last. Unknown and repeated ids are dropped, so a stale or partial
 * request still yields one slot per existing category.
 */
fun planCategoryOrder(current: List<String>, requested: List<String>): List<String> {
    val existing = current.toSet()
    val ordered = LinkedHashSet<String>()
    requested.filterTo(ordered) { it in existing && it != TagCategory.OTHER_ID }
    current.filterTo(ordered) { it != TagCategory.OTHER_ID }
    if (TagCategory.OTHER_ID in existing) ordered += TagCategory.OTHER_ID
    return ordered.toList()
}
