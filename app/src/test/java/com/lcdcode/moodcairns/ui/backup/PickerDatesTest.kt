package com.lcdcode.moodcairns.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class PickerDatesTest {

    @Test
    fun days_round_trip_through_picker_millis() {
        val days = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 11, 21),
            LocalDate.of(2028, 2, 29), // leap day
        )
        for (day in days) {
            assertEquals(day, pickerMillisToDay(dayToPickerMillis(day)))
        }
    }

    @Test
    fun conversion_ignores_the_device_time_zone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati")) // UTC+14
            val day = LocalDate.of(2026, 11, 21)
            assertEquals(day, pickerMillisToDay(dayToPickerMillis(day)))
            // 2026-11-21T00:00Z, computed independently with Python's datetime.
            assertEquals(1_795_219_200_000L, dayToPickerMillis(day))
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
