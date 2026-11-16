package com.lcdcode.moodcairns.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Test

class ClampNoteTest {

    @Test
    fun `note at limit is unchanged`() {
        val note = "a".repeat(MAX_NOTE_CHARS)
        assertEquals(note, clampNote(note))
    }

    @Test
    fun `note over limit is truncated`() {
        val note = "a".repeat(MAX_NOTE_CHARS + 1)
        assertEquals(MAX_NOTE_CHARS, clampNote(note).length)
    }

    @Test
    fun `empty note stays empty`() {
        assertEquals("", clampNote(""))
    }
}
