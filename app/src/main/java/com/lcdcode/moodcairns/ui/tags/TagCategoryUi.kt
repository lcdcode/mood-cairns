package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory

val TagCategory.displayName: String
    get() = when (this) {
        TagCategory.PLACE -> "Places"
        TagCategory.PERSON -> "People"
        TagCategory.ACTIVITY -> "Activities"
    }

/**
 * Orders tags by the fixed category display order (Place, Person, Activity)
 * rather than the DAO's alphabetical category sort. Stable, so each category's
 * existing sortOrder/name order is preserved.
 */
fun List<Tag>.orderedByCategory(): List<Tag> = sortedBy { it.category.ordinal }
