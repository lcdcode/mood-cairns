package com.lcdcode.moodcairns.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class JournalKeyTest {

    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `hkdf expand matches RFC 5869 test case 1`() {
        // RFC 5869 A.1: the first 32 bytes of OKM are exactly T(1).
        val prk = hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        assertArrayEquals(
            hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf"),
            JournalKey.hkdfExpandFirstBlock(prk, info),
        )
    }

    @Test
    fun `derivation is frozen`() {
        // Computed independently (Python hmac). If this fails, existing journals become
        // unreadable: never update the expected value, revert the change instead.
        val dbKey = ByteArray(32) { it.toByte() }
        assertArrayEquals(
            hex("a75e8193f195e2242d5d57f649639d8a5dd39f5cb9aeeda020632877d8654ae6"),
            JournalKey.derive(dbKey),
        )
    }

    @Test
    fun `derived key is 32 bytes and differs from the db key`() {
        val dbKey = ByteArray(32) { 7 }
        val journalKey = JournalKey.derive(dbKey)
        assertEquals(JournalKey.KEY_BYTES, journalKey.size)
        assertFalse(dbKey.contentEquals(journalKey))
    }

    @Test
    fun `different db keys give different journal keys`() {
        val a = JournalKey.derive(ByteArray(32) { 1 })
        val b = JournalKey.derive(ByteArray(32) { 2 })
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `derive leaves the db key untouched`() {
        val dbKey = ByteArray(32) { it.toByte() }
        JournalKey.derive(dbKey)
        assertArrayEquals(ByteArray(32) { it.toByte() }, dbKey)
    }
}
