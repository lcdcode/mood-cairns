package com.lcdcode.moodcairns.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockPolicyTest {

    private var now = 0L
    private val policy = AutoLockPolicy(clock = { now })

    @Test
    fun doesNotLock_withoutPriorBackgrounding() {
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun locks_withImmediateTimeout_afterBackgrounding() {
        policy.noteBackgrounded()
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun doesNotLock_beforeTimeoutElapses() {
        policy.noteBackgrounded()
        now += 29_999L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 30_000L))
    }

    @Test
    fun locks_onceTimeoutElapses() {
        policy.noteBackgrounded()
        now += 30_000L
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 30_000L))
    }

    @Test
    fun backgroundStint_isOnlyEvaluatedOnce() {
        policy.noteBackgrounded()
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 0L))
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun newBackgroundStint_restartsTheClock() {
        policy.noteBackgrounded()
        now += 60_000L
        policy.noteBackgrounded()
        now += 1_000L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 30_000L))
    }

    @Test
    fun grace_suppressesImmediateLock() {
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        now += 5_000L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun grace_expires_thenNormalTimeoutApplies() {
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        now += 120_000L
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun grace_isOneShot() {
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 0L))
        policy.noteBackgrounded()
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun grace_doesNotShortenLongerUserTimeout() {
        // Timeout 5 min, grace 2 min, backgrounded 3 min: the grace has
        // expired but the user's own timeout hasn't elapsed, so no lock.
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        now += 180_000L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 300_000L))
    }

    @Test
    fun grace_overridesShorterUserTimeout_whileActive() {
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        now += 60_000L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 30_000L))
    }
}
