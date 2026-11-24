package com.lcdcode.moodcairns.ui.backup

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material's DatePicker works in milliseconds at UTC midnight, independent of the device
 * time zone; these convert to and from calendar days without shifting them.
 */
internal fun pickerMillisToDay(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

internal fun dayToPickerMillis(day: LocalDate): Long =
    day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
