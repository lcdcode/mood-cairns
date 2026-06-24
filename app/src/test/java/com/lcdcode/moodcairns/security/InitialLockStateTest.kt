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
    fun pinSet_locksRegardlessOfNoPinKey() {
        assertEquals(LockState.Locked, resolveInitialState(pinSet = true, hasNoPinDbKey = false))
        // A stale no-PIN key must never override a real PIN.
        assertEquals(LockState.Locked, resolveInitialState(pinSet = true, hasNoPinDbKey = true))
    }

    @Test
    fun noPin_withStoredKey_boots() {
        assertEquals(LockState.Booting, resolveInitialState(pinSet = false, hasNoPinDbKey = true))
    }

    @Test
    fun noPin_noKey_needsSetup() {
        assertEquals(LockState.NeedsSetup, resolveInitialState(pinSet = false, hasNoPinDbKey = false))
    }
}
