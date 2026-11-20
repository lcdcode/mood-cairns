package com.lcdcode.moodcairns.settings

import android.content.SharedPreferences
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

    /** Minimal SharedPreferences: only what the settings classes use. */
    private class InMemoryPrefs : SharedPreferences {
        val values = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = values.toMap()
        override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?) =
            values[key] as Set<String>? ?: defValues
        override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
        override fun getFloat(key: String, defValue: Float) = values[key] as Float? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
        override fun contains(key: String) = key in values
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener,
        ) = Unit

        private inner class Editor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private val removals = mutableSetOf<String>()
            private var clearAll = false

            override fun putString(key: String, value: String?) = apply { pending[key] = value }
            override fun putStringSet(key: String, values: Set<String>?) =
                apply { pending[key] = values }
            override fun putInt(key: String, value: Int) = apply { pending[key] = value }
            override fun putLong(key: String, value: Long) = apply { pending[key] = value }
            override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
            override fun remove(key: String) = apply { removals += key }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                if (clearAll) values.clear()
                removals.forEach(values::remove)
                values.putAll(pending)
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}
