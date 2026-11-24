package com.lcdcode.moodcairns.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagCategoryTest {

    @Test
    fun sanitize_label_trims_whitespace() {
        assertEquals("Spots", TagCategory.sanitizeLabel("  Spots \t"))
    }

    @Test
    fun sanitize_label_blank_is_null() {
        assertNull(TagCategory.sanitizeLabel(""))
        assertNull(TagCategory.sanitizeLabel("   "))
        assertNull(TagCategory.sanitizeLabel("\n\u0000\t"))
    }

    @Test
    fun sanitize_label_drops_control_characters() {
        assertEquals("NearFar", TagCategory.sanitizeLabel("Near\nFar\u0007"))
    }

    @Test
    fun sanitize_label_keeps_inner_spaces_and_unicode() {
        assertEquals("Люди и места", TagCategory.sanitizeLabel("Люди и места"))
    }

    @Test
    fun sanitize_label_caps_length() {
        val long = "a".repeat(TagCategory.MAX_LABEL_LENGTH + 10)
        assertEquals(TagCategory.MAX_LABEL_LENGTH, TagCategory.sanitizeLabel(long)!!.length)
    }

    @Test
    fun sanitize_label_cap_does_not_split_surrogate_pairs() {
        val emoji = "🪨" // rock, outside the BMP
        val capped = TagCategory.sanitizeLabel(emoji.repeat(TagCategory.MAX_LABEL_LENGTH + 1))!!
        assertEquals(TagCategory.MAX_LABEL_LENGTH, capped.codePointCount(0, capped.length))
        assertEquals(emoji.repeat(TagCategory.MAX_LABEL_LENGTH), capped)
    }

    @Test
    fun sanitize_label_trims_space_left_at_the_cap() {
        val raw = "a".repeat(TagCategory.MAX_LABEL_LENGTH - 1) + " b"
        assertEquals("a".repeat(TagCategory.MAX_LABEL_LENGTH - 1), TagCategory.sanitizeLabel(raw))
    }

    @Test
    fun other_id_is_the_built_in_other() {
        assertEquals("OTHER", TagCategory.OTHER_ID)
        assertEquals(BuiltInTagCategory.OTHER, BuiltInTagCategory.fromId(TagCategory.OTHER_ID))
    }

    @Test
    fun custom_ids_are_unique_and_never_built_in() {
        val first = TagCategory.newCustomId()
        assertNotEquals(first, TagCategory.newCustomId())
        assertNull(BuiltInTagCategory.fromId(first))
    }

    @Test
    fun built_in_ids_are_the_stored_names() {
        // These strings are persisted in tag.category; renaming a constant orphans tags.
        assertEquals(
            listOf("PLACE", "PERSON", "ACTIVITY", "MOOD", "OTHER"),
            BuiltInTagCategory.entries.map { it.id },
        )
    }
}
