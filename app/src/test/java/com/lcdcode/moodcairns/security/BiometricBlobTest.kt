package com.lcdcode.moodcairns.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiometricBlobTest {

    private val iv = ByteArray(12) { it.toByte() }
    private val ciphertext = ByteArray(48) { (100 + it).toByte() }

    @Test
    fun `encoding matches an independently computed vector`() {
        // Python: base64.b64encode(bytes([1]) + bytes(range(12)) + bytes(range(100, 148)))
        assertEquals(KNOWN_ENCODING, BiometricBlob(iv, ciphertext).encode())
    }

    @Test
    fun `decode reverses encode`() {
        val decoded = BiometricBlob.decode(KNOWN_ENCODING)!!
        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun `unknown version is rejected`() {
        assertNull(BiometricBlob.decode(VERSION_2_ENCODING))
    }

    @Test
    fun `truncated blob is rejected`() {
        assertNull(BiometricBlob.decode(ONE_BYTE_SHORT_ENCODING))
    }

    @Test
    fun `garbage and empty input are rejected`() {
        assertNull(BiometricBlob.decode("not base64 !!"))
        assertNull(BiometricBlob.decode(""))
    }

    @Test
    fun `a raw legacy DB key never decodes as a blob`() {
        val rawKeyBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        assertNull(BiometricBlob.decode(rawKeyBase64))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `wrong IV length cannot be constructed`() {
        BiometricBlob(ByteArray(16), ciphertext)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ciphertext without the GCM tag cannot be constructed`() {
        BiometricBlob(iv, ByteArray(32))
    }

    private companion object {
        const val KNOWN_ENCODING =
            "AQABAgMEBQYHCAkKC2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6PkJGSkw=="

        /** [KNOWN_ENCODING] with version byte 2. */
        const val VERSION_2_ENCODING =
            "AgABAgMEBQYHCAkKC2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6PkJGSkw=="

        /** [KNOWN_ENCODING] minus the last ciphertext byte. */
        const val ONE_BYTE_SHORT_ENCODING =
            "AQABAgMEBQYHCAkKC2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMjY6PkJGS"
    }
}
