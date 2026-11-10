package com.lcdcode.moodcairns.ui.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLabelToStoreTest {

    private val defaultName = "Places"

    @Test
    fun customName_isStoredAsTyped() {
        assertEquals("Spots", categoryLabelToStore("Spots", defaultName))
    }

    @Test
    fun defaultName_restoresDefault() {
        assertEquals("", categoryLabelToStore("Places", defaultName))
    }

    @Test
    fun defaultNameWithStraySpaces_restoresDefault() {
        assertEquals("", categoryLabelToStore("  Places\n", defaultName))
    }

    @Test
    fun defaultNameInDifferentCase_isACustomName() {
        assertEquals("places", categoryLabelToStore("places", defaultName))
    }

    @Test
    fun blank_isPassedThroughForTheRepositoryToReset() {
        assertEquals("   ", categoryLabelToStore("   ", defaultName))
    }

    @Test
    fun userCategory_withBlankName_isMissingAName() {
        assertTrue(isMissingCategoryName("  \n", defaultName = null))
        assertFalse(isMissingCategoryName("Health", defaultName = null))
    }

    @Test
    fun builtIn_withBlankName_isFine_becauseItRestoresTheDefault() {
        assertFalse(isMissingCategoryName("", defaultName))
    }
}
