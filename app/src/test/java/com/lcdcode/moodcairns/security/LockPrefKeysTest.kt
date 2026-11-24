package com.lcdcode.moodcairns.security

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins every lock pref key name. Installed apps already store values under these names, so a
 * change here means a lost PIN hash or DB key. Never update a literal in this test.
 */
class LockPrefKeysTest {

    @Test
    fun stored_key_names_never_change() {
        assertEquals("pin_hash", LockPrefKeys.PIN_HASH)
        assertEquals("pin_salt", LockPrefKeys.PIN_SALT)
        assertEquals("pin_iterations", LockPrefKeys.PIN_ITERATIONS)
        assertEquals("lock_timeout_ms", LockPrefKeys.TIMEOUT)
        assertEquals("biometric_enabled", LockPrefKeys.BIOMETRIC_ENABLED)
        assertEquals("allow_unsafe_exports", LockPrefKeys.ALLOW_UNSAFE_EXPORTS)
        assertEquals("pin_failed_attempts", LockPrefKeys.FAILED_ATTEMPTS)
        assertEquals("pin_lockout_until_ms", LockPrefKeys.LOCKOUT_UNTIL)
        assertEquals("dbk_pin_iv", LockPrefKeys.DBK_PIN_IV)
        assertEquals("dbk_pin_ct", LockPrefKeys.DBK_PIN_CT)
        assertEquals("dbk_pin_salt", LockPrefKeys.DBK_PIN_SALT)
        assertEquals("dbk_pin_iter", LockPrefKeys.DBK_PIN_ITER)
        assertEquals("dbk_nopin", LockPrefKeys.DBK_NOPIN)
        assertEquals("dbk_bio", LockPrefKeys.LEGACY_DBK_BIO)
        assertEquals("dbk_bio_v2", LockPrefKeys.DBK_BIO_V2)
        assertEquals("bio_offer", LockPrefKeys.BIO_OFFER)
    }

    @Test
    fun authoritative_keys_cover_the_pin_hash_the_full_pin_wrap_and_the_no_pin_key() {
        assertEquals(
            setOf(
                "pin_hash", "pin_salt", "pin_iterations",
                "dbk_pin_iv", "dbk_pin_ct", "dbk_pin_salt", "dbk_pin_iter",
                "dbk_nopin",
            ),
            LockPrefKeys.AUTHORITATIVE_KEYS.toSet(),
        )
    }
}
