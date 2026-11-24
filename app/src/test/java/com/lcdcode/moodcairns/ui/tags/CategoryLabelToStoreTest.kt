package com.lcdcode.moodcairns.ui.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLabelToStoreTest {

    private val defaultName = "Places"

    @Test
    fun custom_name_is_stored_as_typed() {
        assertEquals("Spots", categoryLabelToStore("Spots", defaultName))
    }

    @Test
    fun default_name_restores_default() {
        assertEquals("", categoryLabelToStore("Places", defaultName))
    }

    @Test
    fun default_name_with_stray_spaces_restores_default() {
        assertEquals("", categoryLabelToStore("  Places\n", defaultName))
    }

    @Test
    fun default_name_in_different_case_is_a_custom_name() {
        assertEquals("places", categoryLabelToStore("places", defaultName))
    }

    @Test
    fun blank_is_passed_through_for_the_repository_to_reset() {
        assertEquals("   ", categoryLabelToStore("   ", defaultName))
    }

    @Test
    fun user_category_with_blank_name_is_missing_a_name() {
        assertTrue(isMissingCategoryName("  \n", defaultName = null))
        assertFalse(isMissingCategoryName("Health", defaultName = null))
    }

    @Test
    fun built_in_with_blank_name_is_fine_because_it_restores_the_default() {
        assertFalse(isMissingCategoryName("", defaultName))
    }
}
