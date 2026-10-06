package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class TagWarningsTest {

    @Test
    fun whileCountLoading_omitsTheCount() {
        assertEquals(
            UiText.Res(R.string.tag_edit_delete_body, listOf("Home")),
            tagDeleteWarning("Home", affectedEntryCount = null),
        )
    }

    @Test
    fun unusedTag_saysSo() {
        assertEquals(
            UiText.Res(R.string.tag_edit_delete_body_unused, listOf("Home")),
            tagDeleteWarning("Home", affectedEntryCount = 0),
        )
    }

    @Test
    fun usedTag_isPluralOnEntryCount() {
        assertEquals(
            UiText.Plural(R.plurals.tag_edit_delete_body_used, 4, listOf(4, "Home")),
            tagDeleteWarning("Home", affectedEntryCount = 4),
        )
    }

    @Test
    fun blankName_fallsBackToThisTag() {
        assertEquals(
            UiText.Res(
                R.string.tag_edit_delete_body,
                listOf(UiText.Res(R.string.tag_edit_this_tag)),
            ),
            tagDeleteWarning("", affectedEntryCount = null),
        )
    }
}
