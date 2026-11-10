package com.lcdcode.moodcairns.ui.tags

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.BuiltInTagCategory
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory

/** Default display name of a built-in category. Its stored id is never shown. */
@StringRes
fun BuiltInTagCategory.displayNameRes(): Int = when (this) {
    BuiltInTagCategory.PLACE -> R.string.enum_tag_category_place
    BuiltInTagCategory.PERSON -> R.string.enum_tag_category_person
    BuiltInTagCategory.ACTIVITY -> R.string.enum_tag_category_activity
    BuiltInTagCategory.MOOD -> R.string.enum_tag_category_mood
    BuiltInTagCategory.OTHER -> R.string.enum_tag_category_other
}

/** The translated default name of a built-in category, or null for a user category. */
@Composable
fun TagCategory.defaultName(): String? =
    BuiltInTagCategory.fromId(id)?.let { stringResource(it.displayNameRes()) }

/** The user's name for this category, else the built-in default. The id is a last resort. */
@Composable
fun TagCategory.displayName(): String = label ?: defaultName() ?: id

/**
 * What to store for a rename: [raw] as typed, or "" (restore the default) when it
 * matches [defaultName]. Storing the default text would freeze it in one language.
 */
fun categoryLabelToStore(raw: String, defaultName: String): String =
    if (TagCategory.sanitizeLabel(raw) == defaultName) "" else raw

/**
 * Groups tags by category id, keeping each group's order. A tag whose category is not
 * in [categories] is grouped under Other, so it stays visible and editable.
 */
fun List<Tag>.groupedByCategory(categories: List<TagCategory>): Map<String, List<Tag>> {
    val knownIds = categories.mapTo(HashSet()) { it.id }
    return groupBy { tag -> tag.categoryId.takeIf { it in knownIds } ?: TagCategory.OTHER_ID }
}

/** Each tag's position in [displayOrder], for [sortedByDisplayRank]. */
fun tagDisplayRank(displayOrder: List<Tag>): Map<Long, Int> =
    displayOrder.withIndex().associate { (index, tag) -> tag.id to index }

/** Sorts tags into display order; tags missing from [rank] go last, in their current order. */
fun List<Tag>.sortedByDisplayRank(rank: Map<Long, Int>): List<Tag> =
    sortedBy { rank[it.id] ?: Int.MAX_VALUE }
