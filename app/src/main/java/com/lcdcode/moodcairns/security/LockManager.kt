package com.lcdcode.moodcairns.security

import android.util.Log
import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.data.db.LegacyMigrator
import com.lcdcode.moodcairns.data.db.MoodDatabaseHolder
import com.lcdcode.moodcairns.data.db.ScheduleDatabase
import com.lcdcode.moodcairns.data.db.Seed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LockState {
    data object NeedsSetup : LockState
    data object Locked : LockState
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
    private val migrator: LegacyMigrator,
) {
    private val _state = MutableStateFlow<LockState>(initialState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    private val autoLock = AutoLockPolicy()

    /** In-memory copy of the SQLCipher DB encryption key while unlocked. */
    @Volatile private var dbKey: ByteArray? = null

    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun initialState(): LockState =
        if (!repo.isPinSet()) LockState.NeedsSetup else LockState.Locked

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
     * Whether biometric unlock can produce a usable DB key right now. False
     * for installs that just upgraded from a build without encryption; the
     * first PIN unlock seeds the biometric blob, and it becomes true after.
     */
    fun canBiometricUnlock(): Boolean = repo.hasBiometricDbKey()

    /**
     * Mirror the biometric-enabled toggle in Settings into the on-disk DB key
     * blob. Only meaningful while [dbKey] is in memory (i.e. unlocked) — and
     * Settings is only reachable from the unlocked state, so that's the only
     * way this is called.
     */
    fun onBiometricEnabledChanged(enabled: Boolean) {
        if (enabled) {
            val key = dbKey ?: return  // Defensive: nothing to save while locked.
            if (!repo.hasBiometricDbKey()) repo.saveBiometricDbKey(key)
        } else {
            repo.clearBiometricDbKey()
        }
    }

    fun lockNow() {
        if (!repo.isPinSet()) return
        dbKey?.fill(0)
        dbKey = null
        moodHolder.close()
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
        val salt = DbKeyCrypto.newSalt()
        val kek = DbKeyCrypto.deriveKek(pin, salt)
        val wrapped = DbKeyCrypto.wrap(kek, key)
        kek.fill(0)
        pin.fill('\u0000')
        repo.savePinDbKeyWrap(
            LockRepository.PinDbKeyWrap(
                iv = wrapped.iv,
                ciphertext = wrapped.ciphertext,
                salt = salt,
                iterations = DbKeyCrypto.DEFAULT_ITERATIONS,
            ),
        )
        repo.clearLockoutState()
        dbKey = key
        moodHolder.open(key)
        if (repo.biometricEnabled) repo.saveBiometricDbKey(key)
        seedNewDatabases()
        _state.value = LockState.Unlocked
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
        val remaining = pinLockoutRemainingMs()
        if (remaining > 0L) {
            pin.fill('\u0000')
            return PinUnlockResult.RateLimited(remaining)
        }
        val pinOk = repo.verifyPin(pin)
        if (!pinOk) {
            pin.fill('\u0000')
            val penalty = registerFailedAttempt()
            return if (penalty > 0L) PinUnlockResult.RateLimited(penalty) else PinUnlockResult.WrongPin
        }
        // PIN is good. Either unwrap the existing DB key, or — on first unlock
        // after an upgrade from a pre-encryption build — synthesise one now.
        val existing = repo.loadPinDbKeyWrap()
        val key: ByteArray = if (existing != null) {
            val kek = DbKeyCrypto.deriveKek(pin, existing.salt, existing.iterations)
            val unwrapped = DbKeyCrypto.unwrap(
                kek,
                DbKeyCrypto.Wrapped(existing.iv, existing.ciphertext),
            )
            kek.fill(0)
            if (unwrapped == null) {
                // PIN hash matched but wrap can't be opened — file tampering or
                // a partially-written wrap. Treat as wrong-PIN-equivalent
                // failure rather than crashing; user can retry.
                pin.fill('\u0000')
                return PinUnlockResult.WrongPin
            }
            unwrapped
        } else {
            val freshKey = DbKeyCrypto.newDbKey()
            val salt = DbKeyCrypto.newSalt()
            val kek = DbKeyCrypto.deriveKek(pin, salt)
            val wrapped = DbKeyCrypto.wrap(kek, freshKey)
            kek.fill(0)
            repo.savePinDbKeyWrap(
                LockRepository.PinDbKeyWrap(
                    iv = wrapped.iv,
                    ciphertext = wrapped.ciphertext,
                    salt = salt,
                    iterations = DbKeyCrypto.DEFAULT_ITERATIONS,
                ),
            )
            freshKey
        }
        pin.fill('\u0000')

        finishUnlock(key)
        return PinUnlockResult.Success
    }

    /**
     * Biometric quick-unlock. Returns false if no biometric DB key is stored
     * (e.g. immediately after upgrading from a build without encryption);
     * caller should fall back to PIN entry.
     */
    fun unlockViaBiometric(): Boolean {
        val key = repo.loadBiometricDbKey() ?: return false
        finishUnlock(key)
        return true
    }

    private fun finishUnlock(key: ByteArray) {
        dbKey = key
        moodHolder.open(key)
        // Opportunistically populate the biometric key so the next session can
        // use the fast path; safe because we hold the DB key in memory now.
        if (repo.biometricEnabled && !repo.hasBiometricDbKey()) {
            repo.saveBiometricDbKey(key)
        }
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
     * Insert default scales + prompt windows into the just-created DBs. Only
     * called on first-time setup; migration paths skip seeding because they
     * preserve legacy rows verbatim.
     */
    private fun seedNewDatabases() {
        migrationScope.launch {
            scheduleDb.promptWindowDao().insertAllIgnore(Seed.windows)
            moodHolder.scaleDao().insertAllIgnore(Seed.scales)
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
