package com.lcdcode.moodcairns.settings

import com.lcdcode.moodcairns.testing.InMemoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalSettingsTest {

    @Test
    fun journal_is_enabled_by_default() {
        assertTrue(JournalSettings(InMemoryPrefs()).enabled.value)
    }

    @Test
    fun disabling_updates_the_flow_and_persists() {
        val prefs = InMemoryPrefs()
        val settings = JournalSettings(prefs)
        settings.setEnabled(false)
        assertFalse(settings.enabled.value)
        assertEquals(false, prefs.values[JournalSettings.KEY_ENABLED])
    }

    @Test
    fun stored_value_is_read_on_creation() {
        val prefs = InMemoryPrefs()
        JournalSettings(prefs).setEnabled(false)
        assertFalse(JournalSettings(prefs).enabled.value)
    }
}
