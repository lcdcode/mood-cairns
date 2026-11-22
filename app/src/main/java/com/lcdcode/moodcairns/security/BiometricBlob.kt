package com.lcdcode.moodcairns.security

import java.util.Base64

/**
 * The DB key encrypted under the auth-bound Keystore key: AES-256-GCM with [AAD] as associated
 * data. Stored as base64 of `version | iv | ciphertext+tag`. Only this disposable copy is ever
 * stored for biometric unlock; the PIN wrap stays authoritative.
 */
class BiometricBlob(val iv: ByteArray, val ciphertext: ByteArray) {

    init {
        require(iv.size == IV_BYTES) { "biometric blob IV must be $IV_BYTES bytes, was ${iv.size}" }
        require(ciphertext.size == CIPHERTEXT_BYTES) {
            "biometric blob ciphertext must be $CIPHERTEXT_BYTES bytes, was ${ciphertext.size}"
        }
    }

    fun encode(): String {
        val out = ByteArray(ENCODED_BYTES)
        out[0] = VERSION
        iv.copyInto(out, destinationOffset = HEADER_BYTES)
        ciphertext.copyInto(out, destinationOffset = HEADER_BYTES + IV_BYTES)
        return Base64.getEncoder().encodeToString(out)
    }

    companion object {
        const val VERSION: Byte = 1
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val HEADER_BYTES = 1
        const val CIPHERTEXT_BYTES = DbKeyCrypto.DB_KEY_BYTES + TAG_BYTES
        private const val ENCODED_BYTES = HEADER_BYTES + IV_BYTES + CIPHERTEXT_BYTES

        /** Binds the ciphertext to this purpose so it can't be confused with any other blob. */
        val AAD: ByteArray = "mood-cairns-bio-v1".toByteArray(Charsets.US_ASCII)

        /** Parses a stored blob, or returns null if it is malformed or of an unknown version. */
        fun decode(encoded: String): BiometricBlob? {
            val raw = try {
                Base64.getDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                return null
            }
            if (raw.size != ENCODED_BYTES || raw[0] != VERSION) return null
            return BiometricBlob(
                iv = raw.copyOfRange(HEADER_BYTES, HEADER_BYTES + IV_BYTES),
                ciphertext = raw.copyOfRange(HEADER_BYTES + IV_BYTES, ENCODED_BYTES),
            )
        }
    }
}
