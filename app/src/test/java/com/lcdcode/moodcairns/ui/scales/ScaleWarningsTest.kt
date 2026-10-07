package com.lcdcode.moodcairns.ui.scales

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ScaleWarningsTest {

    private val thisScale = UiText.Res(R.string.scale_edit_this_scale)
    private val keepValues = UiText.Res(R.string.scale_edit_remap_keep)

    @Test
    fun remap_isPluralOnEntryCount_andNamesTheKeepButton() {
        assertEquals(
            UiText.Plural(R.plurals.scale_edit_remap_body, 3, listOf(3, "Mood", keepValues)),
            remapWarning("Mood", 3),
        )
    }

    @Test
    fun blankName_fallsBackToThisScale() {
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
    fun delete_whileCountLoading_omitsTheCount() {
        assertEquals(
            UiText.Res(R.string.scale_edit_delete_body, listOf("Mood")),
            deleteWarning("Mood", affectedEntryCount = null),
        )
    }

    @Test
    fun delete_unusedScale_saysSo() {
        assertEquals(
            UiText.Res(R.string.scale_edit_delete_body_unused, listOf("Mood")),
            deleteWarning("Mood", affectedEntryCount = 0),
        )
    }

    @Test
    fun delete_usedScale_isPluralOnEntryCount() {
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
