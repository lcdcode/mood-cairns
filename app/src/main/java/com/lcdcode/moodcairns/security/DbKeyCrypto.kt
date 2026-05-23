package com.lcdcode.moodcairns.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wrap/unwrap a random 32-byte database-encryption key with a PIN-derived KEK.
 *
 * Design rationale: SQLCipher needs a key. Deriving it directly from the PIN
 * would force a full database rewrite on every PIN change. Instead, the DB key
 * is a random secret that never changes; only the KEK that wraps it rotates
 * when the PIN does. That keeps PIN changes O(1) regardless of database size.
 *
 * PBKDF2 parameters mirror PinHasher so a successful PIN-hash verify implies a
 * successful KEK derivation (we only call this after the PIN is known good).
 */
object DbKeyCrypto {
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val KEK_BITS = 256
    private const val TAG_BITS = 128
    private const val IV_BYTES = 12
    const val SALT_BYTES = 16
    const val DB_KEY_BYTES = 32
    const val DEFAULT_ITERATIONS = 600_000

    private val rng = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(rng::nextBytes)
    fun newIv(): ByteArray = ByteArray(IV_BYTES).also(rng::nextBytes)
    fun newDbKey(): ByteArray = ByteArray(DB_KEY_BYTES).also(rng::nextBytes)

    /** Derive the KEK from [pin] + [salt]. Caller is responsible for zeroing [pin]. */
    fun deriveKek(pin: CharArray, salt: ByteArray, iterations: Int = DEFAULT_ITERATIONS): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEK_BITS)
        try {
            return SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** AES-GCM-wrap [dbKey] under [kek]. Returns IV + ciphertext+tag. */
    fun wrap(kek: ByteArray, dbKey: ByteArray, iv: ByteArray = newIv()): Wrapped {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return Wrapped(iv = iv, ciphertext = cipher.doFinal(dbKey))
    }

    /** Returns the unwrapped DB key, or null on GCM tag failure (wrong KEK / corruption). */
    fun unwrap(kek: ByteArray, wrapped: Wrapped): ByteArray? {
        return try {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(TAG_BITS, wrapped.iv))
            cipher.doFinal(wrapped.ciphertext)
        } catch (_: Throwable) {
            null
        }
    }

    data class Wrapped(val iv: ByteArray, val ciphertext: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Wrapped) return false
            return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
        }
        override fun hashCode(): Int = iv.contentHashCode() * 31 + ciphertext.contentHashCode()
    }
}
