package com.lcdcode.moodcairns.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenCaptureGateTest {

    private var now = 1_000L
    private val gate = ScreenCaptureGate(clock = { now })
    private val duration = ScreenCaptureGate.ALLOW_DURATION_MS

    @Test
    fun closedByDefault() {
        assertFalse(gate.isAllowed())
        assertNull(gate.allowedUntil.value)
    }

    @Test
    fun allowed_rightAfterOptIn() {
        gate.allowTemporarily()
        assertTrue(gate.isAllowed())
        assertEquals(duration, gate.remainingMs())
    }

    @Test
    fun stillAllowed_justBeforeDeadline() {
        gate.allowTemporarily()
        now += duration - 1
        assertTrue(gate.isAllowed())
    }

    @Test
    fun expiresAtDeadline_andClearsState() {
        gate.allowTemporarily()
        now += duration
        assertFalse(gate.isAllowed())
        assertNull(gate.allowedUntil.value)
    }

    @Test
    fun revoke_closesImmediately() {
        gate.allowTemporarily()
        gate.revoke()
        assertFalse(gate.isAllowed())
    }

    @Test
    fun reArming_restartsTheWindow() {
        gate.allowTemporarily()
        now += duration - 1_000L
        gate.allowTemporarily()
        now += 5_000L
        assertTrue(gate.isAllowed())
    }

    @Test
    fun durationIsTenMinutes() {
        assertEquals(10 * 60_000L, duration)
    }
}

class ShouldSecureWindowTest {

    private val lockedStates = listOf(
        LockState.NeedsSetup,
        LockState.Locked,
        LockState.Booting,
        LockState.Migrating,
        LockState.MigrationFailed("x"),
    )

    @Test
    fun debugBuild_neverSecure() {
        (lockedStates + LockState.Unlocked).forEach { state ->
            assertFalse(shouldSecureWindow(isDebug = true, lockState = state, captureAllowed = false))
        }
    }

    @Test
    fun unlocked_secureUnlessCaptureAllowed() {
        assertTrue(shouldSecureWindow(false, LockState.Unlocked, captureAllowed = false))
        assertFalse(shouldSecureWindow(false, LockState.Unlocked, captureAllowed = true))
    }

    @Test
    fun nonUnlockedStates_alwaysSecure_evenWhenAllowed() {
        lockedStates.forEach { state ->
            assertTrue(state.toString(), shouldSecureWindow(false, state, captureAllowed = true))
        }
    }
}
