package com.lcdcode.moodcairns.ui.common

import android.icu.text.DateFormat as IcuDateFormat
import android.icu.util.TimeZone as IcuTimeZone
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/**
 * The app's current locale. Always pass this explicitly to formatters: on API 29-32
 * Locale.getDefault() can disagree with the per-app language.
 */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Formats dates and times from a CLDR skeleton, which names only the fields wanted
 * (e.g. "MMMd" = abbreviated month and day). ICU picks the field order, separators,
 * and month/day names for the locale, so "MMMd" is "Mar 5" in English and "5. März"
 * in German. Not thread-safe; use from the UI thread.
 */
class SkeletonDateFormat(private val icu: IcuDateFormat) {

    fun format(dateTime: LocalDateTime): String =
        icu.format(Date.from(dateTime.toInstant(ZoneOffset.UTC)))

    fun format(date: LocalDate): String = format(date.atStartOfDay())

    fun format(time: LocalTime): String = format(REFERENCE_DATE.atTime(time))

    private companion object {
        // Any fixed date works for time-only formatting; UTC has no DST gaps.
        val REFERENCE_DATE: LocalDate = LocalDate.of(2000, 1, 1)
    }
}

/**
 * [skeleton] may use "j" for the hour, which follows the device's 12/24-hour setting.
 * Common skeletons: "jm" time, "MMMd" short date, "yMMMd" date with year,
 * "yMMMEd" with weekday.
 */
@Composable
fun rememberSkeletonDateFormat(skeleton: String): SkeletonDateFormat {
    val locale = currentLocale()
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(skeleton, locale, is24Hour) {
        val icu = IcuDateFormat.getInstanceForSkeleton(applyHourCycle(skeleton, is24Hour), locale)
        // Values are converted as UTC wall-clock times, so format them as UTC too.
        icu.timeZone = IcuTimeZone.GMT_ZONE
        SkeletonDateFormat(icu)
    }
}

/** Replaces the locale-preferred hour field "j" with the device's 12/24-hour choice. */
internal fun applyHourCycle(skeleton: String, is24Hour: Boolean): String =
    skeleton.replace('j', if (is24Hour) 'H' else 'h')
