package com.lcdcode.moodcairns.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-trip checks for the PIN-wrapped DB key. The actual SQLCipher round
 * trip lives behind a native library that the JVM test runtime can't load, so
 * we verify the wrap layer here and verify SQLCipher end-to-end via
 * instrumented tests / manual QA on device.
 */
class DbKeyCryptoTest {

    @Test
    fun wrapAndUnwrap_recoversKey_withCorrectPin() {
        val pin = "1234".toCharArray()
        val salt = DbKeyCrypto.newSalt()
        val dbKey = DbKeyCrypto.newDbKey()
        // Lower iteration count so the test stays sub-second; the production
        // iteration count is exercised by integration testing on device.
        val iter = 1000

        val kek = DbKeyCrypto.deriveKek(pin.copyOf(), salt, iter)
        val wrapped = DbKeyCrypto.wrap(kek, dbKey)
        val kek2 = DbKeyCrypto.deriveKek(pin.copyOf(), salt, iter)
        val unwrapped = DbKeyCrypto.unwrap(kek2, wrapped)

        assertNotNull(unwrapped)
        assertArrayEquals(dbKey, unwrapped)
    }

    @Test
    fun unwrap_returnsNull_onWrongPin() {
        val salt = DbKeyCrypto.newSalt()
        val dbKey = DbKeyCrypto.newDbKey()
        val iter = 1000

        val kek = DbKeyCrypto.deriveKek("1234".toCharArray(), salt, iter)
        val wrapped = DbKeyCrypto.wrap(kek, dbKey)
        val wrongKek = DbKeyCrypto.deriveKek("9999".toCharArray(), salt, iter)
        assertNull(DbKeyCrypto.unwrap(wrongKek, wrapped))
    }

    @Test
    fun wrap_isIvUnique_acrossCalls() {
        // GCM is catastrophically broken if IV is reused under the same key;
        // we generate a fresh IV every wrap call. Two consecutive wraps of the
        // same plaintext must produce different ciphertext.
        val kek = DbKeyCrypto.deriveKek("1234".toCharArray(), DbKeyCrypto.newSalt(), 1000)
        val dbKey = DbKeyCrypto.newDbKey()
        val a = DbKeyCrypto.wrap(kek, dbKey)
        val b = DbKeyCrypto.wrap(kek, dbKey)
        assertNotEquals(a.iv.toList(), b.iv.toList())
        assertNotEquals(a.ciphertext.toList(), b.ciphertext.toList())
    }
}
