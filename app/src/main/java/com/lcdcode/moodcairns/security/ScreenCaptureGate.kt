package com.lcdcode.moodcairns.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Time-boxed opt-out from the app-wide FLAG_SECURE. The user can allow screen
 * capture for [ALLOW_DURATION_MS]; the window closes on expiry, on lock, or on
 * process death. Held in memory only, so nothing on disk can keep it open.
 *
 * [clock] is monotonic so wall-clock changes can't stretch the window, and
 * injectable because SystemClock is unavailable in JVM tests.
 */
@Singleton
class ScreenCaptureGate(private val clock: () -> Long) {

    @Inject constructor() : this(SystemClock::elapsedRealtime)

    private val _allowedUntil = MutableStateFlow<Long?>(null)

    /** Monotonic deadline ([clock] time base) while capture is allowed, else null. */
    val allowedUntil: StateFlow<Long?> = _allowedUntil.asStateFlow()

    /** Allow capture for [ALLOW_DURATION_MS] from now, restarting any open window. */
    fun allowTemporarily() {
        _allowedUntil.value = clock() + ALLOW_DURATION_MS
    }

    fun revoke() {
        _allowedUntil.value = null
    }

    /** Remaining allowed time in ms, 0 if closed. Clears the state once expired. */
    fun remainingMs(): Long {
        val until = _allowedUntil.value ?: return 0L
        val remaining = until - clock()
        if (remaining <= 0L) {
            revoke()
            return 0L
        }
        return remaining
    }

    fun isAllowed(): Boolean = remainingMs() > 0L

    companion object {
        const val ALLOW_DURATION_MINUTES = 10
        const val ALLOW_DURATION_MS: Long = ALLOW_DURATION_MINUTES * 60_000L
    }
}

/**
 * Whether the window must carry FLAG_SECURE. Debug builds never do, so store
 * screenshots can be captured. Otherwise every non-Unlocked state (lock screen,
 * PIN setup, migration) stays secure regardless of the user's opt-out.
 */
internal fun shouldSecureWindow(
    isDebug: Boolean,
    lockState: LockState,
    captureAllowed: Boolean,
): Boolean = !isDebug && (lockState !is LockState.Unlocked || !captureAllowed)
