package com.lcdcode.moodcairns.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class JournalDayOfTest {

    // 23:30 UTC on Nov 19 is already Nov 20 in Berlin and still Nov 19 in Chicago.
    private val lateEvening = Instant.parse("2026-11-19T23:30:00Z")

    @Test
    fun day_follows_the_local_calendar_not_utc() {
        assertEquals(
            LocalDate.of(2026, 11, 20),
            journalDayOf(lateEvening, ZoneId.of("Europe/Berlin")),
        )
        assertEquals(
            LocalDate.of(2026, 11, 19),
            journalDayOf(lateEvening, ZoneId.of("America/Chicago")),
        )
    }

    @Test
    fun backdated_entry_opens_its_own_day() {
        val lastWeek = Instant.parse("2026-11-12T09:00:00Z")
        assertEquals(LocalDate.of(2026, 11, 12), journalDayOf(lastWeek, ZoneId.of("UTC")))
    }
}
