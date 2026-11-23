package com.lcdcode.moodcairns.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderingTest {

    private val letters = listOf("a", "b", "c", "d")

    @Test
    fun movingDown_shiftsTheItemsBetweenUp() {
        assertEquals(listOf("b", "c", "a", "d"), letters.withItemMoved(0, 2))
    }

    @Test
    fun movingUp_shiftsTheItemsBetweenDown() {
        assertEquals(listOf("a", "d", "b", "c"), letters.withItemMoved(3, 1))
    }

    @Test
    fun movingInPlace_keepsTheOrder() {
        assertEquals(letters, letters.withItemMoved(2, 2))
    }

    @Test
    fun outOfRangeIndices_giveNull() {
        assertNull(letters.withItemMoved(0, -1))
        assertNull(letters.withItemMoved(3, 4))
        assertNull(letters.withItemMoved(-1, 0))
        assertNull(emptyList<String>().withItemMoved(0, 0))
    }

    @Test
    fun theOriginalListIsUnchanged() {
        val original = letters.toList()
        letters.withItemMoved(0, 3)

        assertEquals(original, letters)
    }
}
