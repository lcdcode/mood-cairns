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
    fun no_legacy_key_means_nothing_to_do_and_nothing_written() {
        givenPinWithFullWrap()
        val before = prefs.values.toMap()
        assertEquals(LegacyCleanup.NONE, slots.retireLegacyAfterVerifiedUnlock())
        assertEquals(before, prefs.values)
    }

    @Test
    fun legacy_key_is_removed_durably_in_pin_mode_and_re_enrollment_is_offered() {
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
    fun legacy_key_is_removed_in_no_pin_mode_without_an_offer() {
        prefs.values[LockPrefKeys.DBK_NOPIN] = encode(dbKey)
        givenLegacyKey()
        assertEquals(LegacyCleanup.REMOVED, slots.retireLegacyAfterVerifiedUnlock())
        assertFalse(LockPrefKeys.LEGACY_DBK_BIO in prefs.values)
        assertNull(slots.offer())
    }

    @Test
    fun legacy_key_is_kept_when_it_is_the_only_copy_of_the_db_key() {
        prefs.values[LockPrefKeys.PIN_HASH] = "hash"
        givenLegacyKey()
        val before = prefs.values.toMap()
        assertEquals(LegacyCleanup.KEPT, slots.retireLegacyAfterVerifiedUnlock())
        assertEquals(before, prefs.values)
    }

    @Test
    fun legacy_key_is_kept_when_the_pin_wrap_is_incomplete() {
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
    fun retirement_is_idempotent() {
        givenPinWithFullWrap()
        givenLegacyKey()
        slots.retireLegacyAfterVerifiedUnlock()
        val afterFirst = prefs.values.toMap()
        assertEquals(LegacyCleanup.NONE, slots.retireLegacyAfterVerifiedUnlock())
        assertEquals(afterFirst, prefs.values)
    }

    @Test
    fun legacy_key_loads_for_adoption_only_when_it_is_a_well_formed_db_key() {
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
    fun enrollment_is_stored_durably_turns_biometric_on_and_clears_the_offer() {
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
    fun missing_and_corrupt_blobs_are_told_apart() {
        assertEquals(BlobRead.Absent, slots.loadBlob())
        prefs.values[LockPrefKeys.DBK_BIO_V2] = "garbage"
        assertEquals(BlobRead.Corrupt, slots.loadBlob())
    }

    @Test
    fun clearing_the_enrollment_turns_biometric_off_durably() {
        slots.saveEnrollment(blob)
        slots.clearEnrollment()
        assertFalse(slots.isEnrolled())
        assertEquals(BlobRead.Absent, slots.loadBlob())
        assertEquals(false, prefs.values[LockPrefKeys.BIOMETRIC_ENABLED])
        assertEquals(2, prefs.commits)
    }

    @Test
    fun enrolled_needs_both_the_flag_and_the_blob() {
        prefs.values[LockPrefKeys.BIOMETRIC_ENABLED] = true
        assertFalse("flag without blob, as on every pre-fix install", slots.isEnrolled())
        prefs.values[LockPrefKeys.DBK_BIO_V2] = blob.encode()
        prefs.values[LockPrefKeys.BIOMETRIC_ENABLED] = false
        assertFalse("blob without flag", slots.isEnrolled())
    }

    @Test
    fun a_legacy_raw_key_never_counts_as_an_enrollment() {
        givenLegacyKey()
        assertFalse(slots.isEnrolled())
        assertEquals(BlobRead.Absent, slots.loadBlob())
    }

    // --- offer ---------------------------------------------------------------------------------

    @Test
    fun offer_round_trips_and_clears() {
        BiometricOffer.entries.forEach {
            slots.setOffer(it)
            assertEquals(it, slots.offer())
        }
        slots.clearOffer()
        assertNull(slots.offer())
    }

    @Test
    fun unknown_stored_offer_reads_as_none() {
        prefs.values[LockPrefKeys.BIO_OFFER] = "SOMETHING_ELSE"
        assertNull(slots.offer())
    }

    // --- data-safety invariant -----------------------------------------------------------------

    @Test
    fun no_biometric_operation_touches_the_authoritative_keys() {
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

    // --- PIN wrap state -------------------------------------------------------------------------

    @Test
    fun pin_wrap_state_truth_table() {
        assertEquals(PinWrapState.OPENED, pinWrapState(hasAnyWrapPart = true, unwrapped = true))
        assertEquals(
            PinWrapState.UNREADABLE,
            pinWrapState(hasAnyWrapPart = true, unwrapped = false),
        )
        assertEquals(PinWrapState.ABSENT, pinWrapState(hasAnyWrapPart = false, unwrapped = false))
    }

    @Test
    fun a_partial_pin_wrap_never_allows_a_new_db_key() {
        val state = pinWrapState(hasAnyWrapPart = true, unwrapped = false)
        assertTrue(pinUnlockKeySource(state, hasLegacyBioKey = false) != PinKeySource.FRESH)
    }

    // --- PIN key source ------------------------------------------------------------------------

    @Test
    fun pin_key_source_truth_table() {
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
    fun a_new_db_key_is_never_created_while_a_legacy_key_or_a_stored_wrap_exists() {
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
