package com.lcdcode.moodcairns.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LockState {
    data object NeedsSetup : LockState
    data object Locked : LockState
    data object Unlocked : LockState
}

@Singleton
class LockManager @Inject constructor(
    private val repo: LockRepository,
) {
    private val _state = MutableStateFlow<LockState>(initialState())
    val state: StateFlow<LockState> = _state.asStateFlow()

    private var backgroundedAt: Long? = null

    private fun initialState(): LockState =
        if (!repo.isPinSet()) LockState.NeedsSetup else LockState.Locked

    fun onAppBackgrounded() {
        if (_state.value is LockState.Unlocked) {
            backgroundedAt = SystemClock.elapsedRealtime()
        }
    }

    fun onAppForegrounded() {
        val bg = backgroundedAt ?: return
        backgroundedAt = null
        val elapsed = SystemClock.elapsedRealtime() - bg
        if (elapsed >= repo.timeoutMs) lockNow()
    }

    fun lockNow() {
        if (repo.isPinSet()) _state.value = LockState.Locked
    }

    fun completeSetup(pin: CharArray) {
        repo.setPin(pin)
        pin.fill('\u0000')
        _state.value = LockState.Unlocked
    }

    fun tryUnlockWithPin(pin: CharArray): Boolean {
        val ok = repo.verifyPin(pin)
        pin.fill('\u0000')
        if (ok) _state.value = LockState.Unlocked
        return ok
    }

    fun unlockViaBiometric() {
        _state.value = LockState.Unlocked
    }
}
