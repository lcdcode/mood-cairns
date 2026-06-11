package com.lcdcode.moodcairns.security

import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import com.lcdcode.moodcairns.di.LockPrefs
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockRepository @Inject constructor(
    @LockPrefs private val prefs: SharedPreferences,
) {
    fun isPinSet(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: CharArray) {
        val hashed = PinHasher.hash(pin)
        prefs.edit {
            putString(KEY_HASH, encode(hashed.hash))
            putString(KEY_SALT, encode(hashed.salt))
            putInt(KEY_ITERATIONS, hashed.iterations)
        }
    }

    fun verifyPin(pin: CharArray): Boolean {
        val hash = prefs.getString(KEY_HASH, null)?.let(::decode) ?: return false
        val salt = prefs.getString(KEY_SALT, null)?.let(::decode) ?: return false
        val iter = prefs.getInt(KEY_ITERATIONS, 0).takeIf { it > 0 } ?: return false
        return PinHasher.verify(pin, PinHasher.Hashed(hash, salt, iter))
    }

    fun clearPin() = prefs.edit {
        remove(KEY_HASH); remove(KEY_SALT); remove(KEY_ITERATIONS)
    }

    var timeoutMs: Long
        get() = prefs.getLong(KEY_TIMEOUT, DEFAULT_TIMEOUT_MS)
        set(value) = prefs.edit { putLong(KEY_TIMEOUT, value) }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, true)
        set(value) = prefs.edit { putBoolean(KEY_BIOMETRIC, value) }

    /**
     * Number of consecutive failed PIN attempts since the last successful unlock.
     * Persisted so a process restart can't reset the counter.
     */
    var failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit { putInt(KEY_FAILED_ATTEMPTS, value) }

    /**
     * Wall-clock instant before which PIN attempts are rejected without
     * invoking PBKDF2. Wall-clock (not elapsedRealtime) so the lockout persists
     * across reboots; the only way to skip it is to advance the system clock,
     * which on a locked device requires authenticating to OS Settings first.
     */
    var lockoutUntilMs: Long
        get() = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit { putLong(KEY_LOCKOUT_UNTIL, value) }

    fun clearLockoutState() = prefs.edit {
        remove(KEY_FAILED_ATTEMPTS); remove(KEY_LOCKOUT_UNTIL)
    }

    /**
     * PIN-wrapped database encryption key. AES-GCM(IV=[iv], CT=[ciphertext])
     * under a KEK derived from PIN + [salt] via PBKDF2-SHA256 with [iterations].
     * Present once the user has set (or upgraded into) a PIN.
     */
    data class PinDbKeyWrap(
        val iv: ByteArray,
        val ciphertext: ByteArray,
        val salt: ByteArray,
        val iterations: Int,
    )

    fun savePinDbKeyWrap(wrap: PinDbKeyWrap) = prefs.edit {
        putString(KEY_DBK_IV, encode(wrap.iv))
        putString(KEY_DBK_CT, encode(wrap.ciphertext))
        putString(KEY_DBK_SALT, encode(wrap.salt))
        putInt(KEY_DBK_ITER, wrap.iterations)
    }

    /**
     * Rotate the PIN hash and the PIN-wrapped DB key together in a single
     * committed write. A PIN change must update both: the hash gates entry and
     * the wrap is what the new PIN-derived KEK can actually open. Writing them
     * in separate transactions risks a crash leaving the hash advanced while the
     * wrap still expects the old PIN, which would lock the user out of their own
     * data. [commit] is used (not apply) so success is only reported once the
     * change is durable. Caller owns zeroing [pin].
     */
    fun setPinAndDbKeyWrap(pin: CharArray, wrap: PinDbKeyWrap) {
        val hashed = PinHasher.hash(pin)
        prefs.edit(commit = true) {
            putString(KEY_HASH, encode(hashed.hash))
            putString(KEY_SALT, encode(hashed.salt))
            putInt(KEY_ITERATIONS, hashed.iterations)
            putString(KEY_DBK_IV, encode(wrap.iv))
            putString(KEY_DBK_CT, encode(wrap.ciphertext))
            putString(KEY_DBK_SALT, encode(wrap.salt))
            putInt(KEY_DBK_ITER, wrap.iterations)
        }
    }

    fun loadPinDbKeyWrap(): PinDbKeyWrap? {
        val iv = prefs.getString(KEY_DBK_IV, null)?.let(::decode) ?: return null
        val ct = prefs.getString(KEY_DBK_CT, null)?.let(::decode) ?: return null
        val salt = prefs.getString(KEY_DBK_SALT, null)?.let(::decode) ?: return null
        val iter = prefs.getInt(KEY_DBK_ITER, 0).takeIf { it > 0 } ?: return null
        return PinDbKeyWrap(iv, ct, salt, iter)
    }

    fun hasPinDbKeyWrap(): Boolean = prefs.contains(KEY_DBK_IV)

    /**
     * Database key stored for the biometric quick-unlock path. EncryptedSharedPrefs
     * already wraps this under the Android Keystore master key, so it is at rest
     * behind device-unlock. We store the raw DB key here (rather than another
     * Keystore-bound wrap) so biometric unlock can short-circuit without
     * re-deriving a KEK; the security trade-off is that an attacker with code
     * execution inside this app's process can read it. The PIN path remains the
     * stronger credential — it's the only thing protecting an exfiltrated DB +
     * LockPrefs blob.
     */
    fun saveBiometricDbKey(dbKey: ByteArray) =
        prefs.edit { putString(KEY_BIO_DBK, encode(dbKey)) }

    fun loadBiometricDbKey(): ByteArray? =
        prefs.getString(KEY_BIO_DBK, null)?.let(::decode)

    fun hasBiometricDbKey(): Boolean = prefs.contains(KEY_BIO_DBK)

    fun clearBiometricDbKey() = prefs.edit { remove(KEY_BIO_DBK) }

    fun clearAllKeyMaterial() = prefs.edit {
        remove(KEY_DBK_IV); remove(KEY_DBK_CT); remove(KEY_DBK_SALT); remove(KEY_DBK_ITER)
        remove(KEY_BIO_DBK)
    }

    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_ITERATIONS = "pin_iterations"
        private const val KEY_TIMEOUT = "lock_timeout_ms"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_FAILED_ATTEMPTS = "pin_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL = "pin_lockout_until_ms"
        private const val KEY_DBK_IV = "dbk_pin_iv"
        private const val KEY_DBK_CT = "dbk_pin_ct"
        private const val KEY_DBK_SALT = "dbk_pin_salt"
        private const val KEY_DBK_ITER = "dbk_pin_iter"
        private const val KEY_BIO_DBK = "dbk_bio"
        const val DEFAULT_TIMEOUT_MS: Long = 60_000L
    }
}
