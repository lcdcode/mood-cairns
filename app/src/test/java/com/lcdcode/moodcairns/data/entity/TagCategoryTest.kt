package com.lcdcode.moodcairns.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagCategoryTest {

    @Test
    fun sanitizeLabel_trimsWhitespace() {
        assertEquals("Spots", TagCategory.sanitizeLabel("  Spots \t"))
    }

    @Test
    fun sanitizeLabel_blankIsNull() {
        assertNull(TagCategory.sanitizeLabel(""))
        assertNull(TagCategory.sanitizeLabel("   "))
        assertNull(TagCategory.sanitizeLabel("\n\u0000\t"))
    }

    @Test
    fun sanitizeLabel_dropsControlCharacters() {
        assertEquals("NearFar", TagCategory.sanitizeLabel("Near\nFar\u0007"))
    }

    @Test
    fun sanitizeLabel_keepsInnerSpacesAndUnicode() {
        assertEquals("Люди и места", TagCategory.sanitizeLabel("Люди и места"))
    }

    @Test
    fun sanitizeLabel_capsLength() {
        val long = "a".repeat(TagCategory.MAX_LABEL_LENGTH + 10)
        assertEquals(TagCategory.MAX_LABEL_LENGTH, TagCategory.sanitizeLabel(long)!!.length)
    }

    @Test
    fun sanitizeLabel_capDoesNotSplitSurrogatePairs() {
        val emoji = "🪨" // rock, outside the BMP
        val capped = TagCategory.sanitizeLabel(emoji.repeat(TagCategory.MAX_LABEL_LENGTH + 1))!!
        assertEquals(TagCategory.MAX_LABEL_LENGTH, capped.codePointCount(0, capped.length))
        assertEquals(emoji.repeat(TagCategory.MAX_LABEL_LENGTH), capped)
    }

    @Test
    fun sanitizeLabel_trimsSpaceLeftAtTheCap() {
        val raw = "a".repeat(TagCategory.MAX_LABEL_LENGTH - 1) + " b"
        assertEquals("a".repeat(TagCategory.MAX_LABEL_LENGTH - 1), TagCategory.sanitizeLabel(raw))
    }

    @Test
    fun otherId_isTheBuiltInOther() {
        assertEquals("OTHER", TagCategory.OTHER_ID)
        assertEquals(BuiltInTagCategory.OTHER, BuiltInTagCategory.fromId(TagCategory.OTHER_ID))
    }

    @Test
    fun customIds_areUniqueAndNeverBuiltIn() {
        val first = TagCategory.newCustomId()
        assertNotEquals(first, TagCategory.newCustomId())
        assertNull(BuiltInTagCategory.fromId(first))
    }

    @Test
    fun builtInIds_areTheStoredNames() {
        // These strings are persisted in tag.category; renaming a constant orphans tags.
        assertEquals(
            listOf("PLACE", "PERSON", "ACTIVITY", "MOOD", "OTHER"),
            BuiltInTagCategory.entries.map { it.id },
        )
    }
}
