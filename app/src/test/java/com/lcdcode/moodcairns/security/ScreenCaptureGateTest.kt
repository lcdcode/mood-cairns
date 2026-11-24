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
    fun closed_by_default() {
        assertFalse(gate.isAllowed())
        assertNull(gate.allowedUntil.value)
    }

    @Test
    fun allowed_right_after_opt_in() {
        gate.allowTemporarily()
        assertTrue(gate.isAllowed())
        assertEquals(duration, gate.remainingMs())
    }

    @Test
    fun still_allowed_just_before_deadline() {
        gate.allowTemporarily()
        now += duration - 1
        assertTrue(gate.isAllowed())
    }

    @Test
    fun expires_at_deadline_and_clears_state() {
        gate.allowTemporarily()
        now += duration
        assertFalse(gate.isAllowed())
        assertNull(gate.allowedUntil.value)
    }

    @Test
    fun revoke_closes_immediately() {
        gate.allowTemporarily()
        gate.revoke()
        assertFalse(gate.isAllowed())
    }

    @Test
    fun re_arming_restarts_the_window() {
        gate.allowTemporarily()
        now += duration - 1_000L
        gate.allowTemporarily()
        now += 5_000L
        assertTrue(gate.isAllowed())
    }

    @Test
    fun duration_is_ten_minutes() {
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
    fun debug_build_never_secure() {
        (lockedStates + LockState.Unlocked).forEach { state ->
            assertFalse(shouldSecureWindow(isDebug = true, lockState = state, captureAllowed = false))
        }
    }

    @Test
    fun unlocked_secure_unless_capture_allowed() {
        assertTrue(shouldSecureWindow(false, LockState.Unlocked, captureAllowed = false))
        assertFalse(shouldSecureWindow(false, LockState.Unlocked, captureAllowed = true))
    }

    @Test
    fun non_unlocked_states_always_secure_even_when_allowed() {
        lockedStates.forEach { state ->
            assertTrue(state.toString(), shouldSecureWindow(false, state, captureAllowed = true))
        }
    }
}
