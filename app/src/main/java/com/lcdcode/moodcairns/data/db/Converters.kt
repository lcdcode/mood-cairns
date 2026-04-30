package com.lcdcode.moodcairns.data.db

import androidx.room.TypeConverter
import com.lcdcode.moodcairns.data.entity.PromptSlot
import java.time.Instant
import java.time.LocalTime

class Converters {
    @TypeConverter fun instantToEpoch(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun epochToInstant(ms: Long?): Instant? = ms?.let(Instant::ofEpochMilli)

    @TypeConverter fun localTimeToString(t: LocalTime?): String? = t?.toString()
    @TypeConverter fun stringToLocalTime(s: String?): LocalTime? = s?.let(LocalTime::parse)

    @TypeConverter fun slotToName(s: PromptSlot?): String? = s?.name
    @TypeConverter fun nameToSlot(n: String?): PromptSlot? = n?.let(PromptSlot::valueOf)
}
