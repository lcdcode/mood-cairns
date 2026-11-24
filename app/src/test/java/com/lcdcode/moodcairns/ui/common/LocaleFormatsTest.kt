package com.lcdcode.moodcairns.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleFormatsTest {

    @Test
    fun hour_cycle_follows_device_setting() {
        assertEquals("Hm", applyHourCycle("jm", is24Hour = true))
        assertEquals("hm", applyHourCycle("jm", is24Hour = false))
        assertEquals("MMMdHm", applyHourCycle("MMMdjm", is24Hour = true))
    }

    @Test
    fun hour_cycle_leaves_date_only_skeletons_alone() {
        assertEquals("yMMMEd", applyHourCycle("yMMMEd", is24Hour = true))
    }
}
