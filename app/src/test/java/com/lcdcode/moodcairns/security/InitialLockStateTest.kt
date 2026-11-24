package com.lcdcode.moodcairns.security

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Truth table for [resolveInitialState], the launch-state decision that now has
 * to distinguish a deliberate no-PIN install from a genuine first run. Getting
 * this wrong would either re-prompt setup over existing data or auto-open a DB
 * that should sit behind the lock screen.
 */
class InitialLockStateTest {

    @Test
    fun pin_set_locks_regardless_of_no_pin_key() {
        assertEquals(LockState.Locked, resolveInitialState(pinSet = true, hasNoPinDbKey = false))
        // A stale no-PIN key must never override a real PIN.
        assertEquals(LockState.Locked, resolveInitialState(pinSet = true, hasNoPinDbKey = true))
    }

    @Test
    fun no_pin_with_stored_key_boots() {
        assertEquals(LockState.Booting, resolveInitialState(pinSet = false, hasNoPinDbKey = true))
    }

    @Test
    fun no_pin_no_key_needs_setup() {
        assertEquals(LockState.NeedsSetup, resolveInitialState(pinSet = false, hasNoPinDbKey = false))
    }
}
