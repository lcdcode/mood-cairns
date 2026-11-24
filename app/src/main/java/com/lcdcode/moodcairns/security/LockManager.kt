package com.lcdcode.moodcairns.security

import android.util.Log
import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.data.db.JournalDatabaseHolder
import com.lcdcode.moodcairns.data.db.LegacyMigrator
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.db.ScheduleDatabase
import com.lcdcode.moodcairns.data.db.Seed
import com.lcdcode.moodcairns.data.db.SeedTagCategories
import com.lcdcode.moodcairns.data.db.SeedTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LockState {
    data object NeedsSetup : LockState
    data object Locked : LockState
    /** No-PIN install: the keystore-held DB key is being loaded and the DB opened. */
    data object Booting : LockState
    /** PIN/biometric just verified; the legacy plaintext DB is being split + encrypted. */
    data object Migrating : LockState
    data object Unlocked : LockState
    /** Migration failed; the app cannot proceed without manual intervention. */
    data class MigrationFailed(val message: String) : LockState
}

sealed interface PinUnlockResult {
    data object Success : PinUnlockResult
    data object WrongPin : PinUnlockResult
    /** PIN entry is throttled. [retryAfterMs] is the remaining wait in milliseconds. */
    data class RateLimited(val retryAfterMs: Long) : PinUnlockResult
}

sealed interface ChangePinResult {
    data object Success : ChangePinResult
    data object WrongPin : ChangePinResult
    /** Current-PIN entry is throttled. [retryAfterMs] is the remaining wait in milliseconds. */
    data class RateLimited(val retryAfterMs: Long) : ChangePinResult
    /** No in-memory DB key, i.e. the app is locked. Should be unreachable from Settings. */
    data object Locked : ChangePinResult
}

/**
 * Outcome of a standalone PIN check (PIN change, backup export) that shares the
 * lock screen's failed-attempt budget so it can't be abused as an unthrottled
 * brute-force oracle while the app is unlocked.
 */
sealed interface PinVerifyResult {
    data object Success : PinVerifyResult
    data object WrongPin : PinVerifyResult
    data class RateLimited(val retryAfterMs: Long) : PinVerifyResult
}

@Singleton
class LockManager @Inject constructor(
    private val repo: LockRepository,
    private val scheduleDb: ScheduleDatabase,
    private val moodHolder: MoodDatabaseHolder,
    private val journalHolder: JournalDatabaseHolder,
    private val migrator: LegacyMigrator,
    private val screenCaptureGate: ScreenCaptureGate,
    private val bioSlots: BiometricSlots,
    private val bioKeystore: BiometricKeystore,
) {
    private val _state = MutableStateFlow<LockState>(initialState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val _biometricOffer = MutableStateFlow(bioSlots.offer())

    /** A pending "turn biometric unlock on?" offer, shown once the app is unlocked. */
    val biometricOffer: StateFlow<BiometricOffer?> = _biometricOffer.asStateFlow()

    private val autoLock = AutoLockPolicy()

    /** In-memory copy of the SQLCipher DB encryption key while unlocked. */
    @Volatile private var dbKey: ByteArray? = null

    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // A no-PIN install has no lock screen to drive the unlock; load the
        // keystore-held DB key and open the DB off the main thread before the
        // UI reaches any state that touches it.
        if (_state.value is LockState.Booting) bootstrapNoPin()
    }

    fun isPinSet(): Boolean = repo.isPinSet()

    private fun initialState(): LockState =
        resolveInitialState(pinSet = repo.isPinSet(), hasNoPinDbKey = repo.hasNoPinDbKey())

    private fun bootstrapNoPin() {
        migrationScope.launch {
            val key = repo.loadNoPinDbKey()
            if (key == null) {
                // Flag said no-PIN but the key is gone (tampering / partial wipe);
                // fall back to first-run setup rather than opening nothing.
                _state.value = LockState.NeedsSetup
                return@launch
            }
            finishUnlock(key, fromAuthoritativeCopy = true)
        }
    }

    /**
     * Call when the app stops being visible (Activity onStop, excluding
     * configuration changes). Do not call from onPause: see [AutoLockPolicy]
     * for why pause-driven locking misfires under transient overlays.
     */
    fun onAppBackgrounded() {
        if (_state.value is LockState.Unlocked) autoLock.noteBackgrounded()
    }

    /** Call when the app becomes visible again (Activity onStart). */
    fun onAppForegrounded() {
        if (autoLock.shouldLockOnForeground(repo.timeoutMs)) lockNow()
    }

    /**
     * Call just before launching the system file picker so the handoff
     * doesn't trip an "Immediate" auto-lock. Suppression is one-shot and
     * capped at [FILE_PICKER_GRACE_MS]; lingering in the picker longer than
     * that locks the app as usual.
     */
    fun suppressLockForFilePicker() {
        autoLock.armGrace(FILE_PICKER_GRACE_MS)
    }

    /**
     * Whether a hardware-bound biometric enrollment exists. Biometric unlock is a disposable
     * shortcut: every failure path clears it and falls back to the PIN, which stays the only
     * authoritative way to the DB key.
     */
    fun canBiometricUnlock(): Boolean = repo.isPinSet() && bioSlots.isEnrolled()

    /**
     * Step 1 of biometric unlock: a decrypt cipher for the BiometricPrompt CryptoObject. A dead
     * key (biometrics changed, screen lock removed) or a missing or corrupt blob clears the
     * enrollment; a dead key also queues an [BiometricOffer.INVALIDATED] notice for after the
     * PIN unlock. Runs Keystore calls, so call it off the main thread.
     */
    fun prepareBiometricUnlock(): BioCipher {
        val blob = when (val read = bioSlots.loadBlob()) {
            BlobRead.Absent -> return BioCipher.Missing
            BlobRead.Corrupt -> {
                disableBiometric()
                return BioCipher.Missing
            }
            is BlobRead.Present -> read.blob
        }
        val result = bioKeystore.decryptCipher(blob.iv)
        when (result) {
            BioCipher.Invalidated -> {
                disableBiometric()
                offerBiometric(BiometricOffer.INVALIDATED)
            }
            BioCipher.Missing -> disableBiometric()
            is BioCipher.Failed, is BioCipher.Ready -> Unit
        }
        return result
    }

    /**
     * Step 2 of biometric unlock, after the prompt succeeded: decrypts the DB key with the
     * authenticated [cipher] and unlocks. Returns false to fall back to the PIN. A failed GCM
     * tag means the blob can never open, so the enrollment is cleared; other Keystore errors
     * may be transient and leave it in place.
     */
    fun unlockWithBiometric(cipher: Cipher): Boolean {
        val blob = (bioSlots.loadBlob() as? BlobRead.Present)?.blob ?: return false
        val key = try {
            cipher.updateAAD(BiometricBlob.AAD)
            cipher.doFinal(blob.ciphertext)
        } catch (e: AEADBadTagException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "biometric blob failed authentication", e)
            disableBiometric()
            return false
        } catch (e: GeneralSecurityException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "biometric decrypt failed", e)
            return false
        }
        if (key.size != DbKeyCrypto.DB_KEY_BYTES) {
            key.fill(0)
            disableBiometric()
            return false
        }
        finishUnlock(key, fromAuthoritativeCopy = false)
        return true
    }

    /**
     * Step 1 of enrollment while unlocked with a PIN: replaces the Keystore key and any old
     * enrollment, and returns an encrypt cipher for the BiometricPrompt CryptoObject.
     */
    fun prepareBiometricEnrollment(): BioCipher {
        if (dbKey == null || !repo.isPinSet()) return BioCipher.Missing
        disableBiometric()
        return bioKeystore.newEncryptCipher()
    }

    /**
     * Step 2 of enrollment, after the prompt succeeded: encrypts the in-memory DB key under
     * the authenticated [cipher] and stores it durably. Returns whether biometric unlock is on.
     */
    fun completeBiometricEnrollment(cipher: Cipher): Boolean {
        val live = dbKey ?: return false
        if (!repo.isPinSet()) return false
        val key = live.copyOf()
        try {
            val blob = try {
                val iv = cipher.iv
                cipher.updateAAD(BiometricBlob.AAD)
                BiometricBlob(iv, cipher.doFinal(key))
            } catch (e: GeneralSecurityException) {
                if (BuildConfig.DEBUG) Log.w(TAG, "biometric enrollment failed", e)
                return false
            } catch (e: IllegalArgumentException) {
                if (BuildConfig.DEBUG) Log.w(TAG, "biometric enrollment gave a malformed blob", e)
                return false
            }
            // A concurrent lockNow zeroes the live key; never enroll a copy taken mid-zeroing.
            if (dbKey !== live || !live.contentEquals(key)) return false
            val saved = bioSlots.saveEnrollment(blob)
            _biometricOffer.value = bioSlots.offer()
            return saved
        } finally {
            key.fill(0)
        }
    }

    /** Turns biometric unlock off and deletes its Keystore key. Never touches the PIN wrap. */
    fun disableBiometric() {
        bioSlots.clearEnrollment()
        bioKeystore.deleteKey()
    }

    /** The user answered the offer (either way), so it is not shown again. */
    fun dismissBiometricOffer() {
        bioSlots.clearOffer()
        _biometricOffer.value = null
    }

    private fun offerBiometric(offer: BiometricOffer) {
        bioSlots.setOffer(offer)
        _biometricOffer.value = offer
    }

    fun lockNow() {
        if (!repo.isPinSet()) return
        // A screenshot opt-out must not outlive the session it was granted in.
        screenCaptureGate.revoke()
        dbKey?.fill(0)
        dbKey = null
        moodHolder.close()
        journalHolder.close()
        _state.value = LockState.Locked
    }

    /**
     * Fresh-install path: sets the PIN, generates a random DB key, wraps it
     * under a PIN-derived KEK, opens the encrypted DB, and seeds default
     * scales + prompt windows. Caller is responsible for the [pin] array
     * lifetime; we zero our reference at the end.
     */
    fun completeSetup(pin: CharArray) {
        repo.setPin(pin)
        val key = DbKeyCrypto.newDbKey()
        savePinWrap(pin, key)
        pin.fill('\u0000')
        repo.clearLockoutState()
        dbKey = key
        openDatabases(key)
        offerBiometric(BiometricOffer.SETUP)
        seedNewDatabases()
        _state.value = LockState.Unlocked
    }

    /**
     * Fresh-install path when the user declines a PIN. Generates a random DB key
     * and stores it raw under the keystore-backed prefs (no KEK, no PIN hash),
     * then opens + seeds the DB. The data is still encrypted at rest; what's
     * waived is the PIN gate and the user-secret layer over the keystore. The UI
     * surfaces the risk and requires explicit acceptance before calling this.
     */
    fun completeSetupWithoutPin() {
        val key = DbKeyCrypto.newDbKey()
        repo.saveNoPinDbKey(key)
        repo.clearLockoutState()
        dbKey = key
        openDatabases(key)
        seedNewDatabases()
        _state.value = LockState.Unlocked
    }

    /**
     * Remove the PIN while unlocked (PIN -> no-PIN). Verifies the current PIN
     * for authorization, then re-homes the in-memory DB key to the raw keystore
     * slot and tears down all PIN-derived material (hash, wrap, biometric key,
     * lockout counters). The DB key itself is unchanged, so no data is rewritten.
     * Requires the unlocked state. [currentPin] is zeroed before returning.
     */
    fun removePin(currentPin: CharArray): ChangePinResult {
        val key = dbKey
        try {
            if (key == null) return ChangePinResult.Locked
            when (val verify = verifyPinThrottled(currentPin)) {
                is PinVerifyResult.RateLimited -> return ChangePinResult.RateLimited(verify.retryAfterMs)
                PinVerifyResult.WrongPin -> return ChangePinResult.WrongPin
                PinVerifyResult.Success -> Unit
            }
            repo.saveNoPinDbKey(key)
            repo.clearPin()
            repo.clearPinDbKeyWrap()
            disableBiometric()
            dismissBiometricOffer()
            repo.clearLockoutState()
            return ChangePinResult.Success
        } finally {
            currentPin.fill('\u0000')
        }
    }

    /**
     * Set a PIN from no-PIN mode while unlocked (no-PIN -> PIN). Wraps the
     * in-memory DB key under a KEK derived from [newPin] + a fresh salt, persists
     * the PIN hash and wrap together, then drops the raw keystore key. The DB key
     * is unchanged. There is no current PIN to verify. [newPin] is zeroed before
     * returning.
     */
    fun setPinFromNoPin(newPin: CharArray): ChangePinResult {
        val key = dbKey
        try {
            if (key == null) return ChangePinResult.Locked
            val salt = DbKeyCrypto.newSalt()
            val kek = DbKeyCrypto.deriveKek(newPin, salt)
            val wrapped = DbKeyCrypto.wrap(kek, key)
            kek.fill(0)
            repo.setPinAndDbKeyWrap(
                newPin,
                LockRepository.PinDbKeyWrap(
                    iv = wrapped.iv,
                    ciphertext = wrapped.ciphertext,
                    salt = salt,
                    iterations = DbKeyCrypto.DEFAULT_ITERATIONS,
                ),
            )
            repo.clearNoPinDbKey()
            repo.clearLockoutState()
            offerBiometric(BiometricOffer.SETUP)
            return ChangePinResult.Success
        } finally {
            newPin.fill('\u0000')
        }
    }

    /**
     * Change the PIN while unlocked. Re-wraps the existing in-memory DB key
     * under a KEK derived from [newPin] + a fresh salt, then persists the new
     * PIN hash and the new wrap together. The random DB key itself is never
     * rotated, so all encrypted data stays readable and the biometric key blob
     * (which stores that same DB key) remains valid without being touched.
     *
     * Requires the unlocked state: [dbKey] must be in memory to re-wrap it.
     * Both [currentPin] and [newPin] are zeroed before returning.
     */
    fun changePin(currentPin: CharArray, newPin: CharArray): ChangePinResult {
        val key = dbKey
        try {
            if (key == null) return ChangePinResult.Locked
            when (val verify = verifyPinThrottled(currentPin)) {
                is PinVerifyResult.RateLimited -> return ChangePinResult.RateLimited(verify.retryAfterMs)
                PinVerifyResult.WrongPin -> return ChangePinResult.WrongPin
                PinVerifyResult.Success -> Unit  // proceed with re-wrap
            }

            val salt = DbKeyCrypto.newSalt()
            val kek = DbKeyCrypto.deriveKek(newPin, salt)
            val wrapped = DbKeyCrypto.wrap(kek, key)
            kek.fill(0)
            repo.setPinAndDbKeyWrap(
                newPin,
                LockRepository.PinDbKeyWrap(
                    iv = wrapped.iv,
                    ciphertext = wrapped.ciphertext,
                    salt = salt,
                    iterations = DbKeyCrypto.DEFAULT_ITERATIONS,
                ),
            )
            return ChangePinResult.Success
        } finally {
            currentPin.fill('\u0000')
            newPin.fill('\u0000')
        }
    }

    /**
     * Current PIN-attempt lockout in milliseconds, or 0 if attempts are allowed.
     * Lets the lock UI render any pending countdown without burning a PBKDF2
     * verification round.
     */
    fun pinLockoutRemainingMs(): Long {
        val until = repo.lockoutUntilMs
        val now = System.currentTimeMillis()
        return (until - now).coerceAtLeast(0L)
    }

    /**
     * Verify [pin] against the stored hash under the same failed-attempt lockout
     * the lock screen uses. Any sensitive action gated by a PIN re-entry (PIN
     * change, backup export) must go through this rather than calling
     * [LockRepository.verifyPin] directly, so it can't be turned into an
     * unthrottled brute-force oracle while the app is unlocked. A correct PIN
     * clears the lockout counters; a wrong one advances them and may start a
     * backoff. Caller owns zeroing [pin].
     */
    fun verifyPinThrottled(pin: CharArray): PinVerifyResult {
        val remaining = pinLockoutRemainingMs()
        if (remaining > 0L) return PinVerifyResult.RateLimited(remaining)
        if (!repo.verifyPin(pin)) {
            val penalty = registerFailedAttempt()
            return if (penalty > 0L) PinVerifyResult.RateLimited(penalty) else PinVerifyResult.WrongPin
        }
        repo.clearLockoutState()
        return PinVerifyResult.Success
    }

    /** Record one failed PIN attempt and return the backoff it triggers, in ms (0 if none yet). */
    private fun registerFailedAttempt(): Long {
        val attempts = repo.failedAttempts + 1
        repo.failedAttempts = attempts
        val penalty = penaltyForAttempts(attempts)
        if (penalty > 0L) repo.lockoutUntilMs = System.currentTimeMillis() + penalty
        return penalty
    }

    fun tryUnlockWithPin(pin: CharArray): PinUnlockResult {
        try {
            val remaining = pinLockoutRemainingMs()
            if (remaining > 0L) return PinUnlockResult.RateLimited(remaining)
            if (!repo.verifyPin(pin)) {
                val penalty = registerFailedAttempt()
                return if (penalty > 0L) {
                    PinUnlockResult.RateLimited(penalty)
                } else {
                    PinUnlockResult.WrongPin
                }
            }
            // A correct PIN without a usable DB key reads as a wrong PIN, as before: nothing
            // has been written, and the user can retry.
            val key = dbKeyAfterCorrectPin(pin) ?: return PinUnlockResult.WrongPin
            finishUnlock(key, fromAuthoritativeCopy = true)
            return PinUnlockResult.Success
        } finally {
            pin.fill('\u0000')
        }
    }

    /**
     * The DB key after a correct PIN, or null when no key can be used without risking data.
     * See [pinUnlockKeySource]. An adopted or new key is checked against the existing database
     * before its PIN wrap is saved, so a key that cannot open the data never becomes
     * authoritative.
     */
    private fun dbKeyAfterCorrectPin(pin: CharArray): ByteArray? {
        val unwrapped = repo.loadPinDbKeyWrap()?.let { unwrapPinWrap(pin, it) }
        val wrapState = pinWrapState(repo.hasAnyPinDbKeyWrapPart(), unwrapped != null)
        val legacyKey = bioSlots.loadLegacyKeyForAdoption()
        val source = pinUnlockKeySource(wrapState, legacyKey != null)
        if (source != PinKeySource.ADOPT_LEGACY) legacyKey?.fill(0)
        return when (source) {
            PinKeySource.UNWRAPPED -> unwrapped
            PinKeySource.ADOPT_LEGACY -> wrapIfItOpensDatabase(pin, checkNotNull(legacyKey))
            // Upgrade from a pre-encryption build: no encrypted database exists yet.
            PinKeySource.FRESH -> wrapIfItOpensDatabase(pin, DbKeyCrypto.newDbKey())
            PinKeySource.REJECT -> null
        }
    }

    private fun unwrapPinWrap(pin: CharArray, wrap: LockRepository.PinDbKeyWrap): ByteArray? {
        val kek = DbKeyCrypto.deriveKek(pin, wrap.salt, wrap.iterations)
        return try {
            DbKeyCrypto.unwrap(kek, DbKeyCrypto.Wrapped(wrap.iv, wrap.ciphertext))
        } finally {
            kek.fill(0)
        }
    }

    /**
     * Saves [key] wrapped under [pin], but only if it opens the existing database or there is
     * none yet. A new key therefore never replaces the key of a database that already exists.
     */
    private fun wrapIfItOpensDatabase(pin: CharArray, key: ByteArray): ByteArray? {
        if (!moodHolder.keyOpensExistingDatabase(key)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "refused a DB key that does not open the database")
            key.fill(0)
            return null
        }
        savePinWrap(pin, key)
        return key
    }

    private fun savePinWrap(pin: CharArray, key: ByteArray) {
        val salt = DbKeyCrypto.newSalt()
        val kek = DbKeyCrypto.deriveKek(pin, salt)
        val wrapped = DbKeyCrypto.wrap(kek, key)
        kek.fill(0)
        repo.savePinDbKeyWrap(
            LockRepository.PinDbKeyWrap(
                iv = wrapped.iv,
                ciphertext = wrapped.ciphertext,
                salt = salt,
                iterations = DbKeyCrypto.DEFAULT_ITERATIONS,
            ),
        )
    }

    /**
     * [fromAuthoritativeCopy] is true when [key] came from the PIN wrap or the no-PIN slot,
     * which may retire the legacy raw key; a biometric unlock never does.
     */
    private fun finishUnlock(key: ByteArray, fromAuthoritativeCopy: Boolean) {
        dbKey = key
        openDatabases(key)
        if (fromAuthoritativeCopy) retireLegacyBiometricKey(key)
        repo.clearLockoutState()
        if (migrator.needed()) {
            _state.value = LockState.Migrating
            migrationScope.launch {
                try {
                    migrator.migrateIfNeeded()
                    _state.value = LockState.Unlocked
                } catch (t: Throwable) {
                    if (BuildConfig.DEBUG) Log.e(TAG, "migration failed", t)
                    _state.value = LockState.MigrationFailed(
                        t.message ?: "Unknown migration error",
                    )
                }
            }
        } else {
            _state.value = LockState.Unlocked
        }
    }

    /**
     * Removes the raw biometric key of audit #4 once [key] is proven to open the database. If
     * the check fails the legacy key stays, since it may be the only working copy.
     */
    private fun retireLegacyBiometricKey(key: ByteArray) {
        if (!bioSlots.hasLegacyKey()) return
        if (!moodHolder.keyOpensExistingDatabase(key)) {
            if (BuildConfig.DEBUG) Log.w(TAG, "kept the legacy bio key: unlock key unverified")
            return
        }
        if (bioSlots.retireLegacyAfterVerifiedUnlock() == LegacyCleanup.REMOVED) {
            _biometricOffer.value = bioSlots.offer()
        }
    }

    /**
     * Opens every encrypted database for the session. The journal key is derived from the DB
     * key each time, never stored, so the journal follows the mood DB through every unlock
     * path. Both open regardless of the journal setting, which only hides the UI.
     */
    private fun openDatabases(key: ByteArray) {
        moodHolder.open(key)
        journalHolder.open(JournalKey.derive(key))
    }

    /**
     * Insert default scales + prompt windows into the just-created DBs. Only
     * called on first-time setup; migration paths skip seeding because they
     * preserve legacy rows verbatim.
     */
    private fun seedNewDatabases() {
        migrationScope.launch {
            scheduleDb.promptWindowDao().insertAllIgnore(Seed.windows)
            moodHolder.scaleDao().insertAllIgnore(Seed.scales)
            moodHolder.tagCategoryDao().insertAllIgnore(SeedTagCategories.all)
            moodHolder.tagDao().insertAllIgnore(SeedTags.tags)
        }
    }

    private fun penaltyForAttempts(attempts: Int): Long = when {
        attempts < 5 -> 0L                  // first 4 mistakes are free
        attempts < 10 -> 30_000L            // attempts 5–9: 30 s
        attempts < 20 -> 5L * 60_000L       // attempts 10–19: 5 min
        else -> 30L * 60_000L               // 20+: 30 min
    }

    companion object {
        private const val TAG = "LockManager"

        /** Auto-lock grace while the user is in the system file picker. */
        private const val FILE_PICKER_GRACE_MS = 2L * 60_000L
    }
}

/**
 * Decide the launch state from persisted credential markers. A set PIN means
 * the lock screen gates entry; otherwise a stored no-PIN DB key means the user
 * opted out of a PIN and we auto-open; with neither, it's a first run.
 *
 * Pure so the truth table can be unit-tested without Android dependencies — a
 * wrong mapping here would either re-prompt setup over real data or auto-open
 * when a PIN should be required.
 */
internal fun resolveInitialState(pinSet: Boolean, hasNoPinDbKey: Boolean): LockState = when {
    pinSet -> LockState.Locked
    hasNoPinDbKey -> LockState.Booting
    else -> LockState.NeedsSetup
}
