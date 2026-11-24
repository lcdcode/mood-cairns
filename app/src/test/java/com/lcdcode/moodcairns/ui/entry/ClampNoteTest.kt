package com.lcdcode.moodcairns.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Test

class ClampNoteTest {

    @Test
    fun note_at_limit_is_unchanged() {
        val note = "a".repeat(MAX_NOTE_CHARS)
        assertEquals(note, clampNote(note))
    }

    @Test
    fun note_over_limit_is_truncated() {
        val note = "a".repeat(MAX_NOTE_CHARS + 1)
        assertEquals(MAX_NOTE_CHARS, clampNote(note).length)
    }

    @Test
    fun emoji_straddling_the_limit_is_not_split() {
        val note = "a".repeat(MAX_NOTE_CHARS - 1) + "😀"
        assertEquals("a".repeat(MAX_NOTE_CHARS - 1), clampNote(note))
    }

    @Test
    fun empty_note_stays_empty() {
        assertEquals("", clampNote(""))
    }
}
