package com.lcdcode.moodcairns.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class LockoutDurationTest {

    @Test
    fun partial_seconds_round_up() {
        assertEquals(0L to 1L, lockoutMinutesSeconds(1L))
        assertEquals(0L to 30L, lockoutMinutesSeconds(29_001L))
    }

    @Test
    fun non_positive_is_at_least_one_second() {
        assertEquals(0L to 1L, lockoutMinutesSeconds(0L))
    }

    @Test
    fun minutes_split_from_seconds() {
        assertEquals(1L to 0L, lockoutMinutesSeconds(60_000L))
        assertEquals(1L to 30L, lockoutMinutesSeconds(90_000L))
        assertEquals(15L to 0L, lockoutMinutesSeconds(900_000L))
    }
}
