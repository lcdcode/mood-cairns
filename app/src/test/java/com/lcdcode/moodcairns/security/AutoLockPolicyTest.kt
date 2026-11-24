package com.lcdcode.moodcairns.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLockPolicyTest {

    private var now = 0L
    private val policy = AutoLockPolicy(clock = { now })

    @Test
    fun does_not_lock_without_prior_backgrounding() {
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun locks_with_immediate_timeout_after_backgrounding() {
        policy.noteBackgrounded()
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun does_not_lock_before_timeout_elapses() {
        policy.noteBackgrounded()
        now += 29_999L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 30_000L))
    }

    @Test
    fun locks_once_timeout_elapses() {
        policy.noteBackgrounded()
        now += 30_000L
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 30_000L))
    }

    @Test
    fun background_stint_is_only_evaluated_once() {
        policy.noteBackgrounded()
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 0L))
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun new_background_stint_restarts_the_clock() {
        policy.noteBackgrounded()
        now += 60_000L
        policy.noteBackgrounded()
        now += 1_000L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 30_000L))
    }

    @Test
    fun grace_suppresses_immediate_lock() {
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        now += 5_000L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun grace_expires_then_normal_timeout_applies() {
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        now += 120_000L
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun grace_is_one_shot() {
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 0L))
        policy.noteBackgrounded()
        assertTrue(policy.shouldLockOnForeground(timeoutMs = 0L))
    }

    @Test
    fun grace_does_not_shorten_longer_user_timeout() {
        // Timeout 5 min, grace 2 min, backgrounded 3 min: the grace has
        // expired but the user's own timeout hasn't elapsed, so no lock.
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        now += 180_000L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 300_000L))
    }

    @Test
    fun grace_overrides_shorter_user_timeout_while_active() {
        policy.armGrace(durationMs = 120_000L)
        policy.noteBackgrounded()
        now += 60_000L
        assertFalse(policy.shouldLockOnForeground(timeoutMs = 30_000L))
    }
}
