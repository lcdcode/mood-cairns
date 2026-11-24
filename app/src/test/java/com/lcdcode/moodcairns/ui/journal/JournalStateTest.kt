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
    fun missing_or_blank_day_argument_means_today() {
        assertEquals(today, resolveJournalDay(null, today))
        assertEquals(today, resolveJournalDay("", today))
    }

    @Test
    fun valid_day_argument_is_used() {
        assertEquals(yesterday, resolveJournalDay("2026-11-19", today))
    }

    @Test
    fun invalid_day_argument_falls_back_to_today() {
        assertEquals(today, resolveJournalDay("2026-13-40", today))
        assertEquals(today, resolveJournalDay("yesterday", today))
    }

    @Test
    fun body_over_the_limit_is_clamped() {
        val body = "a".repeat(MAX_JOURNAL_BODY_CHARS + 10)
        assertEquals(MAX_JOURNAL_BODY_CHARS, clampJournalBody(body).length)
    }

    @Test
    fun clamping_never_splits_an_emoji() {
        val body = "a".repeat(MAX_JOURNAL_BODY_CHARS - 1) + "😀"
        assertEquals(MAX_JOURNAL_BODY_CHARS - 1, clampJournalBody(body).length)
    }

    @Test
    fun pages_with_text_open_for_reading_including_today() {
        assertTrue(opensInReadMode("went hiking"))
    }

    @Test
    fun empty_pages_open_for_writing() {
        assertFalse(opensInReadMode(""))
        assertFalse(opensInReadMode("  \n"))
    }

    @Test
    fun unloaded_page_is_never_dirty_so_it_cannot_overwrite_stored_text() {
        val state = JournalUiState(day = today, body = "typed early", savedBody = "")
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun loaded_page_is_dirty_only_when_the_body_differs_from_what_was_saved() {
        val saved = JournalUiState(day = today, body = "x", savedBody = "x", loaded = true)
        assertFalse(saved.hasUnsavedChanges)
        assertTrue(saved.copy(body = "xy").hasUnsavedChanges)
        assertTrue(saved.copy(body = "").hasUnsavedChanges)
    }
}
