package com.lcdcode.moodcairns.data.db

import androidx.room.TypeConverter
import com.lcdcode.moodcairns.data.entity.PromptSlot
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter fun instantToEpoch(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun epochToInstant(ms: Long?): Instant? = ms?.let(Instant::ofEpochMilli)

    // ISO-8601 "YYYY-MM-DD": sorts chronologically as text and stays readable in exports.
    @TypeConverter fun localDateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToLocalDate(s: String?): LocalDate? = s?.let(LocalDate::parse)

    @TypeConverter fun localTimeToString(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun stringToLocalTime(s: String?): LocalTime? = s?.let(LocalTime::parse)

    @TypeConverter fun slotToName(s: PromptSlot?): String? = s?.name
    @TypeConverter fun nameToSlot(n: String?): PromptSlot? = n?.let(PromptSlot::valueOf)
}
