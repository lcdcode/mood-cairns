package com.lcdcode.moodcairns.security

import android.content.SharedPreferences
import androidx.core.content.edit
import com.lcdcode.moodcairns.di.LockPrefs
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Why the app should offer to turn biometric unlock on after the next unlock. */
enum class BiometricOffer {
    /** The raw legacy key was removed on upgrade; biometric unlock must be re-enrolled. */
    UPGRADE,

    /** A PIN was just set up. */
    SETUP,

    /** The device's biometrics changed, which invalidated the Keystore key. */
    INVALIDATED,
}

/** Outcome of [BiometricSlots.retireLegacyAfterVerifiedUnlock]. */
enum class LegacyCleanup {
    /** No legacy raw key on disk. */
    NONE,

    /** The legacy raw key was removed; an independent copy of the DB key exists. */
    REMOVED,

    /** No complete independent copy of the DB key is stored, so the legacy key is kept. */
    KEPT,
}

/** Result of reading the enrolled blob. A corrupt blob is distinct from an absent one. */
sealed interface BlobRead {
    data object Absent : BlobRead
    data object Corrupt : BlobRead
    data class Present(val blob: BiometricBlob) : BlobRead
}

/** State of the PIN-wrapped DB key after a correct PIN was entered. */
enum class PinWrapState {
    /** The wrap opened with the PIN-derived KEK. */
    OPENED,

    /** The wrap is stored but would not open (corrupt or partially written). */
    UNREADABLE,

    /** No wrap is stored: an upgrade from a build that predates DB encryption. */
    ABSENT,
}

/** Where the DB key comes from after a correct PIN. */
enum class PinKeySource {
    /** Use the key unwrapped from the PIN wrap (the normal case). */
    UNWRAPPED,

    /** Use the legacy raw key: it is the only remaining copy of the DB key. */
    ADOPT_LEGACY,

    /** Create a new DB key (pre-encryption upgrade, no data is encrypted yet). */
    FRESH,

    /** Refuse the unlock: there is no usable key and creating one would hide existing data. */
    REJECT,
}

/**
 * Classifies the PIN wrap. Any stored part counts as a wrap: a partial one is UNREADABLE,
 * never ABSENT, because ABSENT allows creating a new DB key.
 */
internal fun pinWrapState(hasAnyWrapPart: Boolean, unwrapped: Boolean): PinWrapState = when {
    unwrapped -> PinWrapState.OPENED
    hasAnyWrapPart -> PinWrapState.UNREADABLE
    else -> PinWrapState.ABSENT
}

/**
 * Chooses the DB key source after a correct PIN. Never FRESH while a legacy key exists or a
 * wrap is stored, because a new key over existing encrypted data makes it unreadable.
 */
internal fun pinUnlockKeySource(wrap: PinWrapState, hasLegacyBioKey: Boolean): PinKeySource =
    when (wrap) {
        PinWrapState.OPENED -> PinKeySource.UNWRAPPED
        PinWrapState.UNREADABLE ->
            if (hasLegacyBioKey) PinKeySource.ADOPT_LEGACY else PinKeySource.REJECT
        PinWrapState.ABSENT ->
            if (hasLegacyBioKey) PinKeySource.ADOPT_LEGACY else PinKeySource.FRESH
    }

/**
 * Biometric unlock state in the @LockPrefs file: the enrolled [BiometricBlob], the pending
 * [BiometricOffer], and removal of the legacy raw key from audit #4.
 *
 * Invariant: nothing here writes or removes [LockPrefKeys.AUTHORITATIVE_KEYS]. The biometric
 * copy is disposable, and losing it only means falling back to the PIN. Writes that matter for
 * safety use commit so they are durable before the caller continues.
 */
@Singleton
class BiometricSlots @Inject constructor(
    @LockPrefs private val prefs: SharedPreferences,
) {
    /**
     * Removes the legacy raw DB key. Call only after the databases opened with a key from the
     * PIN wrap or the no-PIN slot, which proves that copy works; mere presence of a wrap is not
     * enough, since a corrupt wrap would leave the legacy key as the only way back in. Also
     * requires a complete independent copy on disk. Idempotent. Turns biometric unlock off, and
     * offers re-enrollment when a PIN is set (in no-PIN mode biometric unlock gates nothing).
     */
    fun retireLegacyAfterVerifiedUnlock(): LegacyCleanup {
        if (!prefs.contains(LockPrefKeys.LEGACY_DBK_BIO)) return LegacyCleanup.NONE
        if (!hasIndependentDbKeyCopy()) return LegacyCleanup.KEPT
        val pinSet = prefs.contains(LockPrefKeys.PIN_HASH)
        prefs.edit(commit = true) {
            remove(LockPrefKeys.LEGACY_DBK_BIO)
            putBoolean(LockPrefKeys.BIOMETRIC_ENABLED, false)
            if (pinSet) putString(LockPrefKeys.BIO_OFFER, BiometricOffer.UPGRADE.name)
        }
        return LegacyCleanup.REMOVED
    }

    fun hasLegacyKey(): Boolean = prefs.contains(LockPrefKeys.LEGACY_DBK_BIO)

    /** The legacy raw DB key, only for adoption by a correct PIN. Null if absent or unreadable. */
    fun loadLegacyKeyForAdoption(): ByteArray? {
        val encoded = prefs.getString(LockPrefKeys.LEGACY_DBK_BIO, null) ?: return null
        val key = try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return key.takeIf { it.size == DbKeyCrypto.DB_KEY_BYTES }
    }

    /** Stores a new enrollment and turns biometric unlock on, in one durable write. */
    fun saveEnrollment(blob: BiometricBlob): Boolean = prefs.edit().run {
        putString(LockPrefKeys.DBK_BIO_V2, blob.encode())
        putBoolean(LockPrefKeys.BIOMETRIC_ENABLED, true)
        remove(LockPrefKeys.BIO_OFFER)
        commit()
    }

    fun loadBlob(): BlobRead {
        val encoded = prefs.getString(LockPrefKeys.DBK_BIO_V2, null) ?: return BlobRead.Absent
        return BiometricBlob.decode(encoded)?.let(BlobRead::Present) ?: BlobRead.Corrupt
    }

    fun isEnrolled(): Boolean =
        prefs.getBoolean(LockPrefKeys.BIOMETRIC_ENABLED, false) &&
            prefs.contains(LockPrefKeys.DBK_BIO_V2)

    /** Removes the enrollment and turns biometric unlock off. The Keystore key is the caller's. */
    fun clearEnrollment() {
        prefs.edit(commit = true) {
            remove(LockPrefKeys.DBK_BIO_V2)
            putBoolean(LockPrefKeys.BIOMETRIC_ENABLED, false)
        }
    }

    /** The pending offer, or null. An unrecognised stored value reads as no offer. */
    fun offer(): BiometricOffer? {
        val name = prefs.getString(LockPrefKeys.BIO_OFFER, null) ?: return null
        return BiometricOffer.entries.firstOrNull { it.name == name }
    }

    fun setOffer(offer: BiometricOffer) {
        prefs.edit { putString(LockPrefKeys.BIO_OFFER, offer.name) }
    }

    fun clearOffer() {
        prefs.edit { remove(LockPrefKeys.BIO_OFFER) }
    }

    private fun hasIndependentDbKeyCopy(): Boolean =
        LockPrefKeys.PIN_WRAP_KEYS.all(prefs::contains) || prefs.contains(LockPrefKeys.DBK_NOPIN)
}
