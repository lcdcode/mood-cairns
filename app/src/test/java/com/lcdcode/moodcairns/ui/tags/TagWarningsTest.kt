package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class TagWarningsTest {

    @Test
    fun while_count_loading_omits_the_count() {
        assertEquals(
            UiText.Res(R.string.tag_edit_delete_body, listOf("Home")),
            tagDeleteWarning("Home", affectedEntryCount = null),
        )
    }

    @Test
    fun unused_tag_says_so() {
        assertEquals(
            UiText.Res(R.string.tag_edit_delete_body_unused, listOf("Home")),
            tagDeleteWarning("Home", affectedEntryCount = 0),
        )
    }

    @Test
    fun used_tag_is_plural_on_entry_count() {
        assertEquals(
            UiText.Plural(R.plurals.tag_edit_delete_body_used, 4, listOf(4, "Home")),
            tagDeleteWarning("Home", affectedEntryCount = 4),
        )
    }

    @Test
    fun blank_name_falls_back_to_this_tag() {
        assertEquals(
            UiText.Res(
                R.string.tag_edit_delete_body,
                listOf(UiText.Res(R.string.tag_edit_this_tag)),
            ),
            tagDeleteWarning("", affectedEntryCount = null),
        )
    }
}
