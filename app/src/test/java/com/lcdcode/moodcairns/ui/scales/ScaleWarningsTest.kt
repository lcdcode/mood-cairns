package com.lcdcode.moodcairns.ui.scales

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleWarningsTest {

    private val thisScale = UiText.Res(R.string.scale_edit_this_scale)
    private val keepValues = UiText.Res(R.string.scale_edit_remap_keep)

    @Test
    fun remap_is_plural_on_entry_count_and_names_the_keep_button() {
        assertEquals(
            UiText.Plural(R.plurals.scale_edit_remap_body, 3, listOf(3, "Mood", keepValues)),
            remapWarning("Mood", 3),
        )
    }

    @Test
    fun blank_name_falls_back_to_this_scale() {
        assertEquals(
            UiText.Plural(R.plurals.scale_edit_remap_body, 1, listOf(1, thisScale, keepValues)),
            remapWarning("  ", 1),
        )
        assertEquals(
            UiText.Res(R.string.scale_edit_delete_body, listOf(thisScale)),
            deleteWarning("", affectedEntryCount = null),
        )
    }

    @Test
    fun delete_while_count_loading_omits_the_count() {
        assertEquals(
            UiText.Res(R.string.scale_edit_delete_body, listOf("Mood")),
            deleteWarning("Mood", affectedEntryCount = null),
        )
    }

    @Test
    fun delete_unused_scale_says_so() {
        assertEquals(
            UiText.Res(R.string.scale_edit_delete_body_unused, listOf("Mood")),
            deleteWarning("Mood", affectedEntryCount = 0),
        )
    }

    @Test
    fun delete_used_scale_is_plural_on_entry_count() {
        assertEquals(
            UiText.Plural(R.plurals.scale_edit_delete_body_used, 1, listOf(1, "Mood")),
            deleteWarning("Mood", affectedEntryCount = 1),
        )
        assertEquals(
            UiText.Plural(R.plurals.scale_edit_delete_body_used, 12, listOf(12, "Mood")),
            deleteWarning("Mood", affectedEntryCount = 12),
        )
    }
}
