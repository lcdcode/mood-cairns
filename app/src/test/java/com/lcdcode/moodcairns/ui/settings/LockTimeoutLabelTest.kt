package com.lcdcode.moodcairns.ui.settings

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class LockTimeoutLabelTest {

    @Test
    fun zero_isImmediate() {
        assertEquals(UiText.Res(R.string.settings_auto_lock_immediate), lockTimeoutLabel(0L))
    }

    @Test
    fun subMinute_isSeconds() {
        assertEquals(
            UiText.Plural(R.plurals.common_duration_seconds_short, 30),
            lockTimeoutLabel(30_000L),
        )
    }

    @Test
    fun wholeMinutes_areMinutes() {
        assertEquals(
            UiText.Plural(R.plurals.common_duration_minutes_short, 1),
            lockTimeoutLabel(60_000L),
        )
        assertEquals(
            UiText.Plural(R.plurals.common_duration_minutes_short, 15),
            lockTimeoutLabel(900_000L),
        )
    }

    @Test
    fun nonWholeMinutes_fallBackToSeconds() {
        assertEquals(
            UiText.Plural(R.plurals.common_duration_seconds_short, 90),
            lockTimeoutLabel(90_000L),
        )
    }
}
