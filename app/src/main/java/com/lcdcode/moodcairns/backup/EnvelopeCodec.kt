package com.lcdcode.moodcairns.backup

import kotlinx.serialization.json.Json
import java.util.Base64

/**
 * The encrypted envelope shared by every backup kind: AES-GCM over the plaintext JSON with
 * a key derived from a passphrase and a fresh random salt. Salt and iteration count travel
 * in the envelope, so any install, including a fresh one, can decrypt with the passphrase.
 *
 * Uses java.util.Base64 (standard alphabet, padded, no line breaks), which is exactly what
 * android.util.Base64 NO_WRAP produced for earlier backups, and keeps this class free of
 * Android APIs so JVM tests cover it.
 */
internal class EnvelopeCodec(private val json: Json) {

    fun encrypt(
        plaintext: String,
        passphrase: CharArray,
        iterations: Int = BackupCrypto.PBKDF2_ITERATIONS,
    ): String {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey(passphrase, salt, iterations)
        try {
            val encrypted = BackupCrypto.encrypt(key, plaintext.toByteArray(Charsets.UTF_8))
            val envelope = EncryptedBackup(
                kdf = BackupCrypto.KDF_ALGORITHM,
                iter = iterations,
                salt = encoder.encodeToString(salt),
                iv = encoder.encodeToString(encrypted.iv),
                ciphertext = encoder.encodeToString(encrypted.ciphertext),
            )
            return json.encodeToString(EncryptedBackup.serializer(), envelope)
        } finally {
            key.fill(0)
        }
    }

    /**
     * Decrypts [raw] with [secret]. Throws [BackupImportException] when [raw] is not an
     * envelope, names an unknown KDF, carries an iteration count outside 1..
     * [MAX_KDF_ITERATIONS], has undecodable fields, or fails authentication (wrong secret or
     * tampered data).
     */
    fun decrypt(raw: String, secret: CharArray): ByteArray {
        // Journal backups are a binary format with their own restore; name the mix-up.
        if (raw.startsWith(JournalBackupFormat.MAGIC_TEXT)) fail(ImportError.JournalFile)
        val envelope = runCatching {
            json.decodeFromString(EncryptedBackup.serializer(), raw)
        }.getOrNull() ?: fail(ImportError.NotABackup)

        if (envelope.kdf != BackupCrypto.KDF_ALGORITHM) {
            fail(ImportError.Malformed(MalformedReason.UNSUPPORTED_KDF))
        }
        if (envelope.iter !in 1..MAX_KDF_ITERATIONS) {
            fail(ImportError.Malformed(MalformedReason.KDF_ITERATIONS_OUT_OF_RANGE))
        }

        val salt = decodeField(envelope.salt, MalformedReason.ENVELOPE_SALT)
        val iv = decodeField(envelope.iv, MalformedReason.ENVELOPE_IV)
        val ciphertext = decodeField(envelope.ciphertext, MalformedReason.ENVELOPE_CIPHERTEXT)

        val key = BackupCrypto.deriveKey(secret, salt, envelope.iter)
        return try {
            BackupCrypto.decrypt(key, iv, ciphertext)
        } catch (_: java.security.GeneralSecurityException) {
            fail(ImportError.WrongSecretOrCorrupt)
        } finally {
            key.fill(0)
        }
    }

    private fun decodeField(value: String, invalid: MalformedReason): ByteArray = try {
        decoder.decode(value)
    } catch (_: IllegalArgumentException) {
        fail(ImportError.Malformed(invalid))
    }

    companion object {
        /**
         * Ten times today's count. A file claiming more would make PBKDF2 run for minutes
         * or hours, so it is refused before any key derivation starts.
         */
        const val MAX_KDF_ITERATIONS = 10 * BackupCrypto.PBKDF2_ITERATIONS

        private val encoder: Base64.Encoder = Base64.getEncoder()
        private val decoder: Base64.Decoder = Base64.getDecoder()
    }
}

private fun fail(error: ImportError): Nothing = throw BackupImportException(error)
