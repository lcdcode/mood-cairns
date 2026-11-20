package com.lcdcode.moodcairns.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class RawKeyPassphraseTest {

    @Test
    fun `formats a 32 byte key as x quote 64 lowercase hex quote`() {
        val key = ByteArray(32) { (it * 8).toByte() }
        val expected = "x'" + key.joinToString("") { "%02x".format(it.toInt() and 0xFF) } + "'"
        val passphrase = rawKeyPassphrase(key)
        assertEquals(expected, String(passphrase, Charsets.US_ASCII))
        assertEquals(67, passphrase.size)
    }

    @Test
    fun `high bytes are not sign extended`() {
        assertEquals("x'ff80'", String(rawKeyPassphrase(byteArrayOf(-1, -128)), Charsets.US_ASCII))
    }
}
