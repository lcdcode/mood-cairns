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
    fun isPinSet(): Boolean = prefs.contains(LockPrefKeys.PIN_HASH)

    fun setPin(pin: CharArray) {
        val hashed = PinHasher.hash(pin)
        prefs.edit {
            putString(LockPrefKeys.PIN_HASH, encode(hashed.hash))
            putString(LockPrefKeys.PIN_SALT, encode(hashed.salt))
            putInt(LockPrefKeys.PIN_ITERATIONS, hashed.iterations)
        }
    }

    fun verifyPin(pin: CharArray): Boolean {
        val hash = prefs.getString(LockPrefKeys.PIN_HASH, null)?.let(::decode) ?: return false
        val salt = prefs.getString(LockPrefKeys.PIN_SALT, null)?.let(::decode) ?: return false
        val iter = prefs.getInt(LockPrefKeys.PIN_ITERATIONS, 0).takeIf { it > 0 } ?: return false
        return PinHasher.verify(pin, PinHasher.Hashed(hash, salt, iter))
    }

    fun clearPin() = prefs.edit {
        remove(LockPrefKeys.PIN_HASH)
        remove(LockPrefKeys.PIN_SALT)
        remove(LockPrefKeys.PIN_ITERATIONS)
    }

    var timeoutMs: Long
        get() = prefs.getLong(LockPrefKeys.TIMEOUT, DEFAULT_TIMEOUT_MS)
        set(value) = prefs.edit { putLong(LockPrefKeys.TIMEOUT, value) }

    /**
     * Whether biometric unlock is turned on. Written only by [BiometricSlots], together with
     * the enrolled blob. Defaults to off: unlock also requires an enrollment, which installs
     * from before the hardware-bound key never have.
     */
    val biometricEnabled: Boolean
        get() = prefs.getBoolean(LockPrefKeys.BIOMETRIC_ENABLED, false)

    /**
     * Opt-in gate for plaintext exports (e.g. unencrypted CSV). Off by default:
     * a plaintext export lands in shared storage where cloud-backup agents and
     * any app with storage access can read it, so the user must knowingly enable
     * it before that option appears.
     */
    var allowUnsafeExports: Boolean
        get() = prefs.getBoolean(LockPrefKeys.ALLOW_UNSAFE_EXPORTS, false)
        set(value) = prefs.edit { putBoolean(LockPrefKeys.ALLOW_UNSAFE_EXPORTS, value) }

    /**
     * Number of consecutive failed PIN attempts since the last successful unlock.
     * Persisted so a process restart can't reset the counter.
     */
    var failedAttempts: Int
        get() = prefs.getInt(LockPrefKeys.FAILED_ATTEMPTS, 0)
        set(value) = prefs.edit { putInt(LockPrefKeys.FAILED_ATTEMPTS, value) }

    /**
     * Wall-clock instant before which PIN attempts are rejected without
     * invoking PBKDF2. Wall-clock (not elapsedRealtime) so the lockout persists
     * across reboots; the only way to skip it is to advance the system clock,
     * which on a locked device requires authenticating to OS Settings first.
     */
    var lockoutUntilMs: Long
        get() = prefs.getLong(LockPrefKeys.LOCKOUT_UNTIL, 0L)
        set(value) = prefs.edit { putLong(LockPrefKeys.LOCKOUT_UNTIL, value) }

    fun clearLockoutState() = prefs.edit {
        remove(LockPrefKeys.FAILED_ATTEMPTS)
        remove(LockPrefKeys.LOCKOUT_UNTIL)
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

    /** Committed, so the wrap is durable before the caller relies on it. */
    fun savePinDbKeyWrap(wrap: PinDbKeyWrap) = prefs.edit(commit = true) {
        putString(LockPrefKeys.DBK_PIN_IV, encode(wrap.iv))
        putString(LockPrefKeys.DBK_PIN_CT, encode(wrap.ciphertext))
        putString(LockPrefKeys.DBK_PIN_SALT, encode(wrap.salt))
        putInt(LockPrefKeys.DBK_PIN_ITER, wrap.iterations)
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
            putString(LockPrefKeys.PIN_HASH, encode(hashed.hash))
            putString(LockPrefKeys.PIN_SALT, encode(hashed.salt))
            putInt(LockPrefKeys.PIN_ITERATIONS, hashed.iterations)
            putString(LockPrefKeys.DBK_PIN_IV, encode(wrap.iv))
            putString(LockPrefKeys.DBK_PIN_CT, encode(wrap.ciphertext))
            putString(LockPrefKeys.DBK_PIN_SALT, encode(wrap.salt))
            putInt(LockPrefKeys.DBK_PIN_ITER, wrap.iterations)
        }
    }

    fun loadPinDbKeyWrap(): PinDbKeyWrap? {
        val iv = prefs.getString(LockPrefKeys.DBK_PIN_IV, null)?.let(::decode) ?: return null
        val ct = prefs.getString(LockPrefKeys.DBK_PIN_CT, null)?.let(::decode) ?: return null
        val salt = prefs.getString(LockPrefKeys.DBK_PIN_SALT, null)?.let(::decode) ?: return null
        val iter = prefs.getInt(LockPrefKeys.DBK_PIN_ITER, 0).takeIf { it > 0 } ?: return null
        return PinDbKeyWrap(iv, ct, salt, iter)
    }

    fun hasPinDbKeyWrap(): Boolean = prefs.contains(LockPrefKeys.DBK_PIN_IV)

    /**
     * True if any part of the PIN wrap is stored. A partial wrap means a DB key existed, so it
     * must never be treated like "no wrap" (which would create a new key over existing data).
     */
    fun hasAnyPinDbKeyWrapPart(): Boolean = LockPrefKeys.PIN_WRAP_KEYS.any(prefs::contains)

    fun clearPinDbKeyWrap() = prefs.edit {
        remove(LockPrefKeys.DBK_PIN_IV)
        remove(LockPrefKeys.DBK_PIN_CT)
        remove(LockPrefKeys.DBK_PIN_SALT)
        remove(LockPrefKeys.DBK_PIN_ITER)
    }

    /**
     * Database key stored for the no-PIN mode. The user has opted out of a PIN,
     * so there is no KEK to wrap it under; we keep the raw DB key here, at rest
     * behind EncryptedSharedPreferences' Keystore master key, so an attacker with
     * code execution in this process can read it. The trade-off is
     * the explicit, warned-about cost of running without a PIN: there is no user
     * secret protecting an exfiltrated DB + LockPrefs blob.
     */
    fun saveNoPinDbKey(dbKey: ByteArray) =
        prefs.edit { putString(LockPrefKeys.DBK_NOPIN, encode(dbKey)) }

    fun loadNoPinDbKey(): ByteArray? =
        prefs.getString(LockPrefKeys.DBK_NOPIN, null)?.let(::decode)

    fun hasNoPinDbKey(): Boolean = prefs.contains(LockPrefKeys.DBK_NOPIN)

    fun clearNoPinDbKey() = prefs.edit { remove(LockPrefKeys.DBK_NOPIN) }

    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 60_000L
    }
}
