package com.lcdcode.moodcairns.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleFormatsTest {

    @Test
    fun hourCycle_followsDeviceSetting() {
        assertEquals("Hm", applyHourCycle("jm", is24Hour = true))
        assertEquals("hm", applyHourCycle("jm", is24Hour = false))
        assertEquals("MMMdHm", applyHourCycle("MMMdjm", is24Hour = true))
    }

    @Test
    fun hourCycle_leavesDateOnlySkeletonsAlone() {
        assertEquals("yMMMEd", applyHourCycle("yMMMEd", is24Hour = true))
    }
}
