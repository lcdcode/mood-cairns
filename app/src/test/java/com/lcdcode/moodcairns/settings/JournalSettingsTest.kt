package com.lcdcode.moodcairns.settings

import com.lcdcode.moodcairns.testing.InMemoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalSettingsTest {

    @Test
    fun `journal is enabled by default`() {
        assertTrue(JournalSettings(InMemoryPrefs()).enabled.value)
    }

    @Test
    fun `disabling updates the flow and persists`() {
        val prefs = InMemoryPrefs()
        val settings = JournalSettings(prefs)
        settings.setEnabled(false)
        assertFalse(settings.enabled.value)
        assertEquals(false, prefs.values[JournalSettings.KEY_ENABLED])
    }

    @Test
    fun `stored value is read on creation`() {
        val prefs = InMemoryPrefs()
        JournalSettings(prefs).setEnabled(false)
        assertFalse(JournalSettings(prefs).enabled.value)
    }
}
