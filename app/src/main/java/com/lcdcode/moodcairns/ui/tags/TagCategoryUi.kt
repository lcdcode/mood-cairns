package com.lcdcode.moodcairns.ui.tags

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.data.entity.TagCategoryLabel

/** Default display name of a category. Categories are stored by enum name, which is never shown. */
@StringRes
fun TagCategory.displayNameRes(): Int = when (this) {
    TagCategory.PLACE -> R.string.enum_tag_category_place
    TagCategory.PERSON -> R.string.enum_tag_category_person
    TagCategory.ACTIVITY -> R.string.enum_tag_category_activity
    TagCategory.MOOD -> R.string.enum_tag_category_mood
    TagCategory.OTHER -> R.string.enum_tag_category_other
}

/** The user's name for this category from [labels], else the translated default. */
@Composable
fun TagCategory.displayName(labels: Map<TagCategory, String>): String =
    labels[this] ?: stringResource(displayNameRes())

/**
 * What to store for a rename: [raw] as typed, or "" (restore the default) when it
 * matches [defaultName]. Storing the default text would freeze it in one language.
 */
fun categoryLabelToStore(raw: String, defaultName: String): String =
    if (TagCategoryLabel.sanitize(raw) == defaultName) "" else raw

/**
 * Orders tags by the fixed category display order (the TagCategory declaration
 * order) rather than the DAO's alphabetical category sort. Stable, so each
 * category's existing sortOrder/name order is preserved.
 */
fun List<Tag>.orderedByCategory(): List<Tag> = sortedBy { it.category.ordinal }
