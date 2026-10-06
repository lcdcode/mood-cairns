package com.lcdcode.moodcairns.ui.scales

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.UiText

/** The scale's name for use inside a sentence, or "this scale" when it is blank. */
internal fun scaleLabel(name: String): Any =
    name.ifBlank { UiText.Res(R.string.scale_edit_this_scale) }

/** Body of the "remap logged values?" dialog shown when a scale's direction flips. */
internal fun remapWarning(name: String, entryCount: Int): UiText = UiText.Plural(
    R.plurals.scale_edit_remap_body,
    entryCount,
    listOf(entryCount, scaleLabel(name), UiText.Res(R.string.scale_edit_remap_keep)),
)

/** Body of the delete dialog. [affectedEntryCount] is null until it has loaded. */
internal fun deleteWarning(name: String, affectedEntryCount: Int?): UiText {
    val label = scaleLabel(name)
    return when (affectedEntryCount) {
        null -> UiText.Res(R.string.scale_edit_delete_body, listOf(label))
        0 -> UiText.Res(R.string.scale_edit_delete_body_unused, listOf(label))
        else -> UiText.Plural(
            R.plurals.scale_edit_delete_body_used,
            affectedEntryCount,
            listOf(affectedEntryCount, label),
        )
    }
}
