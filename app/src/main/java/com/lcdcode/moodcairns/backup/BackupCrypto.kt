package com.lcdcode.moodcairns.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM envelope encryption keyed by a PBKDF2-derived key. Used to encrypt the
 * JSON backup before writing it to user-visible storage.
 */
object BackupCrypto {
    const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    const val CIPHER = "AES/GCM/NoPadding"
    const val KEY_BITS = 256
    const val TAG_BITS = 128
    const val IV_BYTES = 12
    const val SALT_BYTES = 16
    const val PBKDF2_ITERATIONS = 200_000

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
    fun newIv(): ByteArray = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }

    fun deriveKey(
        pin: CharArray,
        salt: ByteArray,
        iterations: Int = PBKDF2_ITERATIONS,
    ): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray, iv: ByteArray = newIv()): Encrypted {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        return Encrypted(iv = iv, ciphertext = ct)
    }

    fun decrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    data class Encrypted(val iv: ByteArray, val ciphertext: ByteArray)
}
