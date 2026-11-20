package com.lcdcode.moodcairns.ui.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JournalStateTest {

    private val today = LocalDate.of(2026, 11, 20)
    private val yesterday = today.minusDays(1)

    @Test
    fun `missing or blank day argument means today`() {
        assertEquals(today, resolveJournalDay(null, today))
        assertEquals(today, resolveJournalDay("", today))
    }

    @Test
    fun `valid day argument is used`() {
        assertEquals(yesterday, resolveJournalDay("2026-11-19", today))
    }

    @Test
    fun `invalid day argument falls back to today`() {
        assertEquals(today, resolveJournalDay("2026-13-40", today))
        assertEquals(today, resolveJournalDay("yesterday", today))
    }

    @Test
    fun `body over the limit is clamped`() {
        val body = "a".repeat(MAX_JOURNAL_BODY_CHARS + 10)
        assertEquals(MAX_JOURNAL_BODY_CHARS, clampJournalBody(body).length)
    }

    @Test
    fun `clamping never splits an emoji`() {
        val body = "a".repeat(MAX_JOURNAL_BODY_CHARS - 1) + "😀"
        assertEquals(MAX_JOURNAL_BODY_CHARS - 1, clampJournalBody(body).length)
    }

    @Test
    fun `past days with text open for reading`() {
        assertTrue(opensInReadMode(yesterday, today, "went hiking"))
    }

    @Test
    fun `today and empty days open for writing`() {
        assertFalse(opensInReadMode(today, today, "already started"))
        assertFalse(opensInReadMode(yesterday, today, "  \n"))
    }

    @Test
    fun `unloaded page is never dirty, so it cannot overwrite stored text`() {
        val state = JournalUiState(day = today, body = "typed early", savedBody = "")
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun `loaded page is dirty only when the body differs from what was saved`() {
        val saved = JournalUiState(day = today, body = "x", savedBody = "x", loaded = true)
        assertFalse(saved.hasUnsavedChanges)
        assertTrue(saved.copy(body = "xy").hasUnsavedChanges)
        assertTrue(saved.copy(body = "").hasUnsavedChanges)
    }
}
