package com.lcdcode.moodcairns.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.lcdcode.moodcairns.di.LockPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the journal UI is shown. Observable, so Home, Entry and Backup update as soon as
 * the setting changes. It only hides UI: the journal database stays open while unlocked and
 * nothing is ever deleted when the journal is turned off.
 */
@Singleton
class JournalSettings @Inject constructor(@LockPrefs private val prefs: SharedPreferences) {

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _enabled.value = enabled
    }

    companion object {
        internal const val KEY_ENABLED = "journal_enabled"
        const val DEFAULT_ENABLED = true
    }
}
