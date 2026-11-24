package com.lcdcode.moodcairns.ui.entry

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Covers the timezone math behind the "Change date/time..." control: combining a
 * picked calendar date and wall-clock time into an Instant, and the UTC-midnight
 * millis round-trip the Material date picker uses.
 */
class DateTimeControlTest {

    @Test
    fun combines_date_and_time_in_given_zone() {
        val date = LocalDate.of(2026, 7, 10)
        val time = LocalTime.of(15, 45)
        val zone = ZoneId.of("America/New_York")

        val result = combineDateTime(date, time, zone)

        // 15:45 local (UTC-4 in July) == 19:45 UTC.
        assertEquals(Instant.parse("2026-07-10T19:45:00Z"), result)
    }

    @Test
    fun combine_is_zone_sensitive() {
        val date = LocalDate.of(2026, 1, 1)
        val time = LocalTime.of(0, 30)

        val utc = combineDateTime(date, time, ZoneOffset.UTC)
        val plusTwo = combineDateTime(date, time, ZoneId.of("Europe/Berlin"))

        assertEquals(Instant.parse("2026-01-01T00:30:00Z"), utc)
        // Berlin is UTC+1 in January, so the same wall time is one hour earlier UTC.
        assertEquals(Instant.parse("2025-12-31T23:30:00Z"), plusTwo)
    }

    @Test
    fun date_picker_utc_millis_round_trips_to_same_calendar_date() {
        val date = LocalDate.of(2026, 2, 28)

        val millis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val recovered = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

        assertEquals(date, recovered)
    }
}
