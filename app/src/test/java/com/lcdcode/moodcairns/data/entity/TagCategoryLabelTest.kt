package com.lcdcode.moodcairns.data.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagCategoryLabelTest {

    @Test
    fun sanitize_trimsWhitespace() {
        assertEquals("Spots", TagCategoryLabel.sanitize("  Spots \t"))
    }

    @Test
    fun sanitize_blankMeansDefault() {
        assertNull(TagCategoryLabel.sanitize(""))
        assertNull(TagCategoryLabel.sanitize("   "))
        assertNull(TagCategoryLabel.sanitize("\n\u0000\t"))
    }

    @Test
    fun sanitize_dropsControlCharacters() {
        assertEquals("NearFar", TagCategoryLabel.sanitize("Near\nFar\u0007"))
    }

    @Test
    fun sanitize_keepsInnerSpacesAndUnicode() {
        assertEquals("Люди и места", TagCategoryLabel.sanitize("Люди и места"))
    }

    @Test
    fun sanitize_capsLength() {
        val long = "a".repeat(TagCategoryLabel.MAX_LENGTH + 10)
        assertEquals(TagCategoryLabel.MAX_LENGTH, TagCategoryLabel.sanitize(long)!!.length)
    }

    @Test
    fun sanitize_capDoesNotSplitSurrogatePairs() {
        val emoji = "🪨" // rock, outside the BMP
        val capped = TagCategoryLabel.sanitize(emoji.repeat(TagCategoryLabel.MAX_LENGTH + 1))!!
        assertEquals(TagCategoryLabel.MAX_LENGTH, capped.codePointCount(0, capped.length))
        assertEquals(emoji.repeat(TagCategoryLabel.MAX_LENGTH), capped)
    }

    @Test
    fun sanitize_trimsSpaceLeftAtTheCap() {
        val raw = "a".repeat(TagCategoryLabel.MAX_LENGTH - 1) + " b"
        assertEquals("a".repeat(TagCategoryLabel.MAX_LENGTH - 1), TagCategoryLabel.sanitize(raw))
    }
}
