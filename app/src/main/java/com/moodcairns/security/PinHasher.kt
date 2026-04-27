package com.moodcairns.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-SHA256 PIN hashing. 600k iterations per OWASP 2023 guidance. The PIN
 * keyspace is small (4–6 digits), so the KDF is the only thing slowing down an
 * offline brute-force if the EncryptedSharedPreferences file is ever leaked.
 */
object PinHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 600_000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_BYTES = 16

    data class Hashed(val hash: ByteArray, val salt: ByteArray, val iterations: Int)

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun hash(pin: CharArray, salt: ByteArray = newSalt(), iterations: Int = ITERATIONS): Hashed {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_LENGTH_BITS)
        try {
            val bytes = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
            return Hashed(bytes, salt, iterations)
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(pin: CharArray, expected: Hashed): Boolean {
        val actual = hash(pin, expected.salt, expected.iterations)
        return MessageDigest.isEqual(actual.hash, expected.hash)
    }
}
