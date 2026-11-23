package com.lcdcode.moodcairns.settings

import android.content.SharedPreferences
import androidx.core.content.edit
import com.lcdcode.moodcairns.di.LockPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when Home shows the "back up your data" card. The card is due [INTERVAL_MS] after
 * the last dismissal or encrypted backup. The countdown starts on first use, so a new install
 * is never nagged. The user can turn the reminder off entirely in Settings.
 *
 * [clock] is wall-clock time so the countdown survives reboots, and injectable for JVM tests.
 */
@Singleton
class BackupReminder(
    private val prefs: SharedPreferences,
    private val clock: () -> Long,
) {

    @Inject constructor(@LockPrefs prefs: SharedPreferences) :
        this(prefs, System::currentTimeMillis)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _due = MutableStateFlow(false)

    /** Always false while the reminder is turned off. */
    val due: StateFlow<Boolean> = _due.asStateFlow()

    init {
        refresh()
    }

    /** Turning the reminder back on starts a fresh countdown instead of showing it at once. */
    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        _enabled.value = enabled
        if (enabled) postpone() else _due.value = false
    }

    /** Re-evaluates [due] against the current time. */
    fun refresh() {
        if (!_enabled.value) {
            _due.value = false
            return
        }
        val now = clock()
        val nextDueAt = prefs.getLong(KEY_NEXT_DUE_AT, NOT_SET)
        // Missing: start the countdown. Too far ahead: the clock went backwards, so clamp it
        // rather than letting the reminder slip out indefinitely.
        if (nextDueAt == NOT_SET || nextDueAt - now > INTERVAL_MS) {
            postpone()
            return
        }
        _due.value = now >= nextDueAt
    }

    /** Hides the card for another [INTERVAL_MS]. Called on dismissal and after a backup. */
    fun postpone() {
        prefs.edit { putLong(KEY_NEXT_DUE_AT, clock() + INTERVAL_MS) }
        _due.value = false
    }

    companion object {
        internal const val KEY_NEXT_DUE_AT = "backup_reminder_next_due_at"
        internal const val KEY_ENABLED = "backup_reminder_enabled"
        const val DEFAULT_ENABLED = true
        val INTERVAL_MS: Long = TimeUnit.DAYS.toMillis(14)
        private const val NOT_SET = -1L
    }
}
