package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Round-trip and backward-compatibility checks for the backup envelope crypto.
 * The full BackupSerializer path uses android.util.Base64, which the JVM test
 * runtime can't load, so we verify the key-derivation + AES-GCM layer here.
 */
class BackupCryptoTest {

    // Keep iteration counts tiny so the test stays sub-second; production counts
    // are carried per-backup in the envelope and exercised on device.
    private val fastIter = 1000

    @Test
    fun encryptDecrypt_roundTrips_withPassphrase() {
        val salt = BackupCrypto.newSalt()
        val key = BackupCrypto.deriveKey("correct horse battery".toCharArray(), salt, fastIter)
        val plaintext = "{\"schemaVersion\":1}".toByteArray()

        val enc = BackupCrypto.encrypt(key, plaintext)
        val dec = BackupCrypto.decrypt(key, enc.iv, enc.ciphertext)

        assertArrayEquals(plaintext, dec)
    }

    @Test
    fun decrypt_failsWithWrongPassphrase() {
        val salt = BackupCrypto.newSalt()
        val enc = BackupCrypto.encrypt(
            BackupCrypto.deriveKey("right-passphrase".toCharArray(), salt, fastIter),
            "secret".toByteArray(),
        )
        val wrongKey = BackupCrypto.deriveKey("wrong-passphrase".toCharArray(), salt, fastIter)

        // GCM tag mismatch surfaces as an AEADBadTagException.
        assertThrows(Throwable::class.java) {
            BackupCrypto.decrypt(wrongKey, enc.iv, enc.ciphertext)
        }
    }

    @Test
    fun legacyBackup_decrypts_whenDerivedWithItsRecordedIterations() {
        // A backup made before the default iteration count was raised must still
        // open: decryption keys off the iteration count stored in the envelope,
        // not whatever the current default happens to be. Here a short numeric
        // "PIN" (like old exports used) at a low iteration count round-trips,
        // while deriving at a different count - i.e. ignoring the envelope - fails.
        val salt = BackupCrypto.newSalt()
        val pin = "1234".toCharArray()
        val legacyIter = 1000
        val newDefaultIter = 4000

        val enc = BackupCrypto.encrypt(
            BackupCrypto.deriveKey(pin.copyOf(), salt, legacyIter),
            "legacy".toByteArray(),
        )

        assertArrayEquals(
            "legacy".toByteArray(),
            BackupCrypto.decrypt(BackupCrypto.deriveKey(pin.copyOf(), salt, legacyIter), enc.iv, enc.ciphertext),
        )
        assertThrows(Throwable::class.java) {
            BackupCrypto.decrypt(BackupCrypto.deriveKey(pin.copyOf(), salt, newDefaultIter), enc.iv, enc.ciphertext)
        }
    }
}
