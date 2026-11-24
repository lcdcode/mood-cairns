package com.lcdcode.moodcairns.security

/**
 * Key names in the @LockPrefs file. These literals are frozen: renaming one orphans the stored
 * value, and for the PIN hash or the PIN-wrapped DB key that means permanent data loss.
 * LockPrefKeysTest pins every value.
 */
internal object LockPrefKeys {
    const val PIN_HASH = "pin_hash"
    const val PIN_SALT = "pin_salt"
    const val PIN_ITERATIONS = "pin_iterations"
    const val TIMEOUT = "lock_timeout_ms"
    const val BIOMETRIC_ENABLED = "biometric_enabled"
    const val ALLOW_UNSAFE_EXPORTS = "allow_unsafe_exports"
    const val FAILED_ATTEMPTS = "pin_failed_attempts"
    const val LOCKOUT_UNTIL = "pin_lockout_until_ms"
    const val DBK_PIN_IV = "dbk_pin_iv"
    const val DBK_PIN_CT = "dbk_pin_ct"
    const val DBK_PIN_SALT = "dbk_pin_salt"
    const val DBK_PIN_ITER = "dbk_pin_iter"
    const val DBK_NOPIN = "dbk_nopin"

    /** Legacy raw DB key for biometric unlock (audit #4). Only ever read to remove or adopt it. */
    const val LEGACY_DBK_BIO = "dbk_bio"

    /** DB key wrapped under the auth-bound Keystore key, see [BiometricBlob]. */
    const val DBK_BIO_V2 = "dbk_bio_v2"

    /** Pending "turn biometric unlock on?" offer, a [BiometricOffer] name. */
    const val BIO_OFFER = "bio_offer"

    /** The complete PIN-wrapped DB key; all four must be present for the wrap to be usable. */
    val PIN_WRAP_KEYS = listOf(DBK_PIN_IV, DBK_PIN_CT, DBK_PIN_SALT, DBK_PIN_ITER)

    /**
     * The authoritative credentials and DB key copies. Biometric code must never write or
     * remove any of these; BiometricSlotsTest enforces it.
     */
    val AUTHORITATIVE_KEYS = listOf(PIN_HASH, PIN_SALT, PIN_ITERATIONS) + PIN_WRAP_KEYS + DBK_NOPIN
}
