package com.lcdcode.moodcairns.security

import android.os.SystemClock

/**
 * Tracks background stints and decides when one should lock the app.
 *
 * Must be driven by Activity onStart/onStop (visibility), never
 * onPause/onResume: transient overlays such as the system permission
 * prompt, biometric sheets, and the app's own dialog windows only pause
 * the activity, and with an "Immediate" timeout a pause-driven policy
 * would lock the app under every one of them.
 *
 * [clock] is injectable because SystemClock is unavailable in JVM tests.
 */
class AutoLockPolicy(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private var backgroundedAt: Long? = null
    private var graceUntil = 0L

    fun noteBackgrounded() {
        backgroundedAt = clock()
    }

    /**
     * Suppress locking for the next background stint, for at most
     * [durationMs] from now. Used when the app deliberately hands off to an
     * external activity (the backup file picker) so an "Immediate" timeout
     * doesn't lock mid-flow. One-shot: consumed by the next foreground
     * check, and ignored once the deadline passes, so a user who leaves the
     * device inside the picker is still locked out after [durationMs].
     */
    fun armGrace(durationMs: Long) {
        graceUntil = clock() + durationMs
    }

    /**
     * True if the app spent at least [timeoutMs] in the background since the
     * last [noteBackgrounded] and no armed grace window is still active.
     * Consumes the marker and any grace, so a given background stint is only
     * evaluated once.
     */
    fun shouldLockOnForeground(timeoutMs: Long): Boolean {
        val bg = backgroundedAt ?: return false
        backgroundedAt = null
        val withinGrace = clock() < graceUntil
        graceUntil = 0L
        return !withinGrace && clock() - bg >= timeoutMs
    }
}
