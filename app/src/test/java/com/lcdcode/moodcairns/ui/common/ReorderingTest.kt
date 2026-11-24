package com.lcdcode.moodcairns.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderingTest {

    private val letters = listOf("a", "b", "c", "d")

    @Test
    fun moving_down_shifts_the_items_between_up() {
        assertEquals(listOf("b", "c", "a", "d"), letters.withItemMoved(0, 2))
    }

    @Test
    fun moving_up_shifts_the_items_between_down() {
        assertEquals(listOf("a", "d", "b", "c"), letters.withItemMoved(3, 1))
    }

    @Test
    fun moving_in_place_keeps_the_order() {
        assertEquals(letters, letters.withItemMoved(2, 2))
    }

    @Test
    fun out_of_range_indices_give_null() {
        assertNull(letters.withItemMoved(0, -1))
        assertNull(letters.withItemMoved(3, 4))
        assertNull(letters.withItemMoved(-1, 0))
        assertNull(emptyList<String>().withItemMoved(0, 0))
    }

    @Test
    fun the_original_list_is_unchanged() {
        val original = letters.toList()
        letters.withItemMoved(0, 3)

        assertEquals(original, letters)
    }
}
