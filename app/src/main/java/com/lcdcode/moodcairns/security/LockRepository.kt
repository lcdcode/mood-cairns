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

    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_ITERATIONS = "pin_iterations"
        private const val KEY_TIMEOUT = "lock_timeout_ms"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        const val DEFAULT_TIMEOUT_MS: Long = 60_000L
    }
}
