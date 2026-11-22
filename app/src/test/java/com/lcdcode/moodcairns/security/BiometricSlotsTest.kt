package com.lcdcode.moodcairns.security

import com.lcdcode.moodcairns.testing.InMemoryPrefs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BiometricSlotsTest {

    private val prefs = InMemoryPrefs()
    private val slots = BiometricSlots(prefs)

    private val dbKey = ByteArray(DbKeyCrypto.DB_KEY_BYTES) { (it + 1).toByte() }
    private val blob = BiometricBlob(ByteArray(12) { 3 }, ByteArray(48) { 9 })

    private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun givenPinWithFullWrap() {
        prefs.values[LockPrefKeys.PIN_HASH] = "hash"
        prefs.values[LockPrefKeys.PIN_SALT] = "salt"
        prefs.values[LockPrefKeys.PIN_ITERATIONS] = 600_000
        prefs.values[LockPrefKeys.DBK_PIN_IV] = "iv"
        prefs.values[LockPrefKeys.DBK_PIN_CT] = "ct"
        prefs.values[LockPrefKeys.DBK_PIN_SALT] = "wrapsalt"
        prefs.values[LockPrefKeys.DBK_PIN_ITER] = 600_000
    }

    private fun givenLegacyKey(value: String = encode(dbKey)) {
        prefs.values[LockPrefKeys.LEGACY_DBK_BIO] = value
        prefs.values[LockPrefKeys.BIOMETRIC_ENABLED] = true
    }

    // --- legacy retirement -------------------------------------------------------------------

    @Test
    fun `no legacy key means nothing to do and nothing written`() {
        givenPinWithFullWrap()
        val before = prefs.values.toMap()
        assertEquals(LegacyCleanup.NONE, slots.retireLegacyAfterVerifiedUnlock())
        assertEquals(before, prefs.values)
    }

    @Test
    fun `legacy key is removed durably in PIN mode and re-enrollment is offered`() {
        givenPinWithFullWrap()
        givenLegacyKey()
        assertEquals(LegacyCleanup.REMOVED, slots.retireLegacyAfterVerifiedUnlock())
        assertFalse(LockPrefKeys.LEGACY_DBK_BIO in prefs.values)
        assertEquals(false, prefs.values[LockPrefKeys.BIOMETRIC_ENABLED])
        assertEquals(BiometricOffer.UPGRADE, slots.offer())
        assertEquals(1, prefs.commits)
        assertEquals(0, prefs.applies)
    }

    @Test
    fun `legacy key is removed in no-PIN mode without an offer`() {
        prefs.values[LockPrefKeys.DBK_NOPIN] = encode(dbKey)
        givenLegacyKey()
        assertEquals(LegacyCleanup.REMOVED, slots.retireLegacyAfterVerifiedUnlock())
        assertFalse(LockPrefKeys.LEGACY_DBK_BIO in prefs.values)
        assertNull(slots.offer())
    }

    @Test
    fun `legacy key is kept when it is the only copy of the DB key`() {
        prefs.values[LockPrefKeys.PIN_HASH] = "hash"
        givenLegacyKey()
        val before = prefs.values.toMap()
        assertEquals(LegacyCleanup.KEPT, slots.retireLegacyAfterVerifiedUnlock())
        assertEquals(before, prefs.values)
    }

    @Test
    fun `legacy key is kept when the PIN wrap is incomplete`() {
        LockPrefKeys.PIN_WRAP_KEYS.forEach { missing ->
            val prefs = InMemoryPrefs()
            val slots = BiometricSlots(prefs)
            LockPrefKeys.PIN_WRAP_KEYS.filter { it != missing }.forEach { prefs.values[it] = "x" }
            prefs.values[LockPrefKeys.PIN_HASH] = "hash"
            prefs.values[LockPrefKeys.LEGACY_DBK_BIO] = encode(dbKey)
            val result = slots.retireLegacyAfterVerifiedUnlock()
            assertEquals("missing $missing", LegacyCleanup.KEPT, result)
            assertTrue(LockPrefKeys.LEGACY_DBK_BIO in prefs.values)
        }
    }

    @Test
    fun `retirement is idempotent`() {
        givenPinWithFullWrap()
        givenLegacyKey()
        slots.retireLegacyAfterVerifiedUnlock()
        val afterFirst = prefs.values.toMap()
        assertEquals(LegacyCleanup.NONE, slots.retireLegacyAfterVerifiedUnlock())
        assertEquals(afterFirst, prefs.values)
    }

    @Test
    fun `legacy key loads for adoption only when it is a well-formed DB key`() {
        assertNull(slots.loadLegacyKeyForAdoption())
        givenLegacyKey()
        assertArrayEquals(dbKey, slots.loadLegacyKeyForAdoption())
        givenLegacyKey(encode(ByteArray(16)))
        assertNull(slots.loadLegacyKeyForAdoption())
        givenLegacyKey("%%% not base64")
        assertNull(slots.loadLegacyKeyForAdoption())
    }

    // --- enrollment ----------------------------------------------------------------------------

    @Test
    fun `enrollment is stored durably, turns biometric on and clears the offer`() {
        slots.setOffer(BiometricOffer.UPGRADE)
        assertTrue(slots.saveEnrollment(blob))
        assertTrue(slots.isEnrolled())
        assertNull(slots.offer())
        val read = slots.loadBlob() as BlobRead.Present
        assertArrayEquals(blob.iv, read.blob.iv)
        assertArrayEquals(blob.ciphertext, read.blob.ciphertext)
        assertEquals(1, prefs.commits)
    }

    @Test
    fun `missing and corrupt blobs are told apart`() {
        assertEquals(BlobRead.Absent, slots.loadBlob())
        prefs.values[LockPrefKeys.DBK_BIO_V2] = "garbage"
        assertEquals(BlobRead.Corrupt, slots.loadBlob())
    }

    @Test
    fun `clearing the enrollment turns biometric off durably`() {
        slots.saveEnrollment(blob)
        slots.clearEnrollment()
        assertFalse(slots.isEnrolled())
        assertEquals(BlobRead.Absent, slots.loadBlob())
        assertEquals(false, prefs.values[LockPrefKeys.BIOMETRIC_ENABLED])
        assertEquals(2, prefs.commits)
    }

    @Test
    fun `enrolled needs both the flag and the blob`() {
        prefs.values[LockPrefKeys.BIOMETRIC_ENABLED] = true
        assertFalse("flag without blob, as on every pre-fix install", slots.isEnrolled())
        prefs.values[LockPrefKeys.DBK_BIO_V2] = blob.encode()
        prefs.values[LockPrefKeys.BIOMETRIC_ENABLED] = false
        assertFalse("blob without flag", slots.isEnrolled())
    }

    @Test
    fun `a legacy raw key never counts as an enrollment`() {
        givenLegacyKey()
        assertFalse(slots.isEnrolled())
        assertEquals(BlobRead.Absent, slots.loadBlob())
    }

    // --- offer ---------------------------------------------------------------------------------

    @Test
    fun `offer round trips and clears`() {
        BiometricOffer.entries.forEach {
            slots.setOffer(it)
            assertEquals(it, slots.offer())
        }
        slots.clearOffer()
        assertNull(slots.offer())
    }

    @Test
    fun `unknown stored offer reads as none`() {
        prefs.values[LockPrefKeys.BIO_OFFER] = "SOMETHING_ELSE"
        assertNull(slots.offer())
    }

    // --- data-safety invariant -----------------------------------------------------------------

    @Test
    fun `no biometric operation touches the authoritative keys`() {
        givenPinWithFullWrap()
        prefs.values[LockPrefKeys.DBK_NOPIN] = encode(dbKey)
        givenLegacyKey()
        val authoritative = { LockPrefKeys.AUTHORITATIVE_KEYS.associateWith { prefs.values[it] } }
        val before = authoritative()
        assertEquals(LockPrefKeys.AUTHORITATIVE_KEYS.size, before.values.count { it != null })

        val operations: List<Pair<String, () -> Unit>> = listOf(
            "retireLegacy" to { slots.retireLegacyAfterVerifiedUnlock() },
            "loadLegacy" to { slots.loadLegacyKeyForAdoption() },
            "saveEnrollment" to { slots.saveEnrollment(blob) },
            "loadBlob" to { slots.loadBlob() },
            "isEnrolled" to { slots.isEnrolled() },
            "clearEnrollment" to { slots.clearEnrollment() },
            "setOffer" to { slots.setOffer(BiometricOffer.INVALIDATED) },
            "offer" to { slots.offer() },
            "clearOffer" to { slots.clearOffer() },
        )
        operations.forEach { (name, op) ->
            op()
            assertEquals("after $name", before, authoritative())
        }
    }

    // --- PIN key source ------------------------------------------------------------------------

    @Test
    fun `PIN key source truth table`() {
        val expected = mapOf(
            (PinWrapState.OPENED to false) to PinKeySource.UNWRAPPED,
            (PinWrapState.OPENED to true) to PinKeySource.UNWRAPPED,
            (PinWrapState.UNREADABLE to false) to PinKeySource.REJECT,
            (PinWrapState.UNREADABLE to true) to PinKeySource.ADOPT_LEGACY,
            (PinWrapState.ABSENT to false) to PinKeySource.FRESH,
            (PinWrapState.ABSENT to true) to PinKeySource.ADOPT_LEGACY,
        )
        expected.forEach { (input, source) ->
            assertEquals("$input", source, pinUnlockKeySource(input.first, input.second))
        }
        assertEquals(PinWrapState.entries.size * 2, expected.size)
    }

    @Test
    fun `a new DB key is never created while a legacy key or a stored wrap exists`() {
        PinWrapState.entries.forEach { wrap ->
            listOf(false, true).forEach { legacy ->
                val source = pinUnlockKeySource(wrap, legacy)
                if (legacy || wrap != PinWrapState.ABSENT) {
                    assertTrue("$wrap/$legacy", source != PinKeySource.FRESH)
                }
            }
        }
    }
}
