package com.lcdcode.moodcairns.ui.backup

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.backup.ConfigConflict
import com.lcdcode.moodcairns.data.entity.BuiltInTagCategory
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.ui.common.SeedNames
import com.lcdcode.moodcairns.ui.common.UiText
import com.lcdcode.moodcairns.ui.common.displayNameArg
import com.lcdcode.moodcairns.ui.tags.displayNameRes

/** Why a configuration could not be imported. Nothing was changed in either case. */
sealed interface ConfigIssue {
    /** [fromConfigFile] is false when the file was a full backup. */
    data class UnsupportedVersion(val found: Int, val fromConfigFile: Boolean) : ConfigIssue
    data class WouldLoseData(val conflicts: List<ConfigConflict>) : ConfigIssue
}

/** Most conflicts listed in the dialog; the rest are summarized as "and N more". */
const val MAX_LISTED_CONFLICTS = 10

/** The dialog's paragraphs: an explanation, then one line per listed conflict. */
fun ConfigIssue.toUiTexts(): List<UiText> = when (this) {
    is ConfigIssue.UnsupportedVersion -> {
        val message = if (fromConfigFile) {
            R.string.backup_config_incompatible_version
        } else {
            R.string.backup_config_incompatible_backup_version
        }
        listOf(UiText.Res(message, listOf(found)))
    }
    is ConfigIssue.WouldLoseData -> {
        val listed = conflicts.take(MAX_LISTED_CONFLICTS).map {
            UiText.Res(R.string.backup_config_conflict_item, listOf(it.toUiText()))
        }
        val hidden = conflicts.size - listed.size
        val more = if (hidden > 0) {
            listOf(UiText.Plural(R.plurals.backup_config_conflict_more, hidden))
        } else {
            emptyList()
        }
        listOf(UiText.Res(R.string.backup_config_incompatible_data_loss)) + listed + more
    }
}

fun ConfigConflict.toUiText(): UiText = when (this) {
    is ConfigConflict.ScaleMissing -> UiText.Plural(
        R.plurals.backup_config_conflict_scale_missing,
        entryCount,
        listOf(entryCount, scale.displayNameArg()),
    )
    is ConfigConflict.ScaleDirectionChanged -> UiText.Plural(
        R.plurals.backup_config_conflict_scale_direction,
        entryCount,
        listOf(entryCount, scale.displayNameArg()),
    )
    is ConfigConflict.ScaleRangeTooNarrow -> UiText.Plural(
        R.plurals.backup_config_conflict_scale_range,
        entryCount,
        listOf(entryCount, scale.displayNameArg(), newMin, newMax),
    )
    is ConfigConflict.TagMissing -> UiText.Plural(
        R.plurals.backup_config_conflict_tag_missing,
        entryCount,
        listOf(entryCount, tag.nameArg(), categoryNameArg(category)),
    )
}

private fun Tag.nameArg(): Any = SeedNames.tagRes(name)?.let { UiText.Res(it) } ?: name

/** A tag whose category row is missing is shown under Other, so it is named that here too. */
private fun categoryNameArg(category: TagCategory?): Any {
    category?.label?.let { return it }
    val builtIn = category?.let { BuiltInTagCategory.fromId(it.id) } ?: BuiltInTagCategory.OTHER
    return UiText.Res(builtIn.displayNameRes())
}
