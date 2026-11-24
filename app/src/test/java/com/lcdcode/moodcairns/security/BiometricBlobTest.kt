package com.lcdcode.moodcairns.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiometricBlobTest {

    private val iv = ByteArray(12) { it.toByte() }
    private val ciphertext = ByteArray(48) { (100 + it).toByte() }

    @Test
    fun encoding_matches_an_independently_computed_vector() {
        // Python: base64.b64encode(bytes([1]) + bytes(range(12)) + bytes(range(100, 148)))
        assertEquals(KNOWN_ENCODING, BiometricBlob(iv, ciphertext).encode())
    }

    @Test
    fun decode_reverses_encode() {
        val decoded = BiometricBlob.decode(KNOWN_ENCODING)!!
        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ciphertext, decoded.ciphertext)
    }

    @Test
    fun unknown_version_is_rejected() {
        assertNull(BiometricBlob.decode(VERSION_2_ENCODING))
    }

    @Test
    fun truncated_blob_is_rejected() {
        assertNull(BiometricBlob.decode(ONE_BYTE_SHORT_ENCODING))
    }

    @Test
    fun garbage_and_empty_input_are_rejected() {
        assertNull(BiometricBlob.decode("not base64 !!"))
        assertNull(BiometricBlob.decode(""))
    }

    @Test
    fun a_raw_legacy_db_key_never_decodes_as_a_blob() {
        val rawKeyBase64 = java.util.Base64.getEncoder().encodeToString(ByteArray(32) { 7 })
        assertNull(BiometricBlob.decode(rawKeyBase64))
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrong_iv_length_cannot_be_constructed() {
        BiometricBlob(ByteArray(16), ciphertext)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ciphertext_without_the_gcm_tag_cannot_be_constructed() {
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
