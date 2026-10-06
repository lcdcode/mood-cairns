package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.UiText

/** Body of the tag delete dialog. [affectedEntryCount] is null until it has loaded. */
internal fun tagDeleteWarning(name: String, affectedEntryCount: Int?): UiText {
    val label: Any = name.ifBlank { UiText.Res(R.string.tag_edit_this_tag) }
    return when (affectedEntryCount) {
        null -> UiText.Res(R.string.tag_edit_delete_body, listOf(label))
        0 -> UiText.Res(R.string.tag_edit_delete_body_unused, listOf(label))
        else -> UiText.Plural(
            R.plurals.tag_edit_delete_body_used,
            affectedEntryCount,
            listOf(affectedEntryCount, label),
        )
    }
}
