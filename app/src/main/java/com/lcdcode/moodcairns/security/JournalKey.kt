package com.lcdcode.moodcairns.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Derives the journal database key from the mood database key, so the journal inherits
 * every existing unlock path (PIN wrap, biometric, no-PIN) and PIN changes without any new
 * key storage. Uses HKDF-Expand (RFC 5869, section 2.3) with the DB key as the PRK; the
 * Extract step is skipped because the DB key is already 32 uniformly random bytes.
 *
 * NEVER change [INFO] or the algorithm: a different output cannot open existing journals.
 */
internal object JournalKey {

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val INFO = "mood-cairns-journal-v1"
    private const val FIRST_BLOCK_COUNTER: Byte = 0x01

    const val KEY_BYTES = 32

    fun derive(dbKey: ByteArray): ByteArray =
        hkdfExpandFirstBlock(dbKey, INFO.toByteArray(Charsets.US_ASCII))

    /** HKDF-Expand output T(1) = HMAC(PRK, info || 0x01): exactly one 32-byte SHA-256 block. */
    internal fun hkdfExpandFirstBlock(prk: ByteArray, info: ByteArray): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(prk, HMAC_ALGORITHM))
        mac.update(info)
        mac.update(FIRST_BLOCK_COUNTER)
        return mac.doFinal()
    }
}
