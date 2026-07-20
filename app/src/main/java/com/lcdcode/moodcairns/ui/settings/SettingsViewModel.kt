package com.lcdcode.moodcairns.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.repo.PromptWindowRepository
import com.lcdcode.moodcairns.security.LockManager
import com.lcdcode.moodcairns.security.LockRepository
import com.lcdcode.moodcairns.work.PromptScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val windows: List<PromptWindow> = emptyList(),
    val lockTimeoutMs: Long = LockRepository.DEFAULT_TIMEOUT_MS,
    val biometricEnabled: Boolean = true,
    val pinSet: Boolean = true,
    val allowUnsafeExports: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val windowsRepo: PromptWindowRepository,
    private val lockRepo: LockRepository,
    private val lockManager: LockManager,
    private val promptScheduler: PromptScheduler,
) : ViewModel() {

    private val lockState = MutableStateFlow(
        Triple(lockRepo.timeoutMs, lockRepo.biometricEnabled, 0),
    )

    val state: StateFlow<SettingsUiState> = combine(
        windowsRepo.observeAll(),
        lockState,
    ) { windows, lock ->
        SettingsUiState(
            windows = windows,
            lockTimeoutMs = lock.first,
            biometricEnabled = lock.second,
            // Re-read on each emission so returning from the Change/Set PIN
            // screen (which bumps the counter via refresh) reflects the new mode.
            pinSet = lockRepo.isPinSet(),
            allowUnsafeExports = lockRepo.allowUnsafeExports,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Re-evaluate lock-derived state, e.g. after returning from Change/Set PIN. */
    fun refresh() = lockState.update { it.copy(third = it.third + 1) }

    fun toggleWindowEnabled(window: PromptWindow) {
        viewModelScope.launch {
            val nowEnabled = !window.enabled
            windowsRepo.upsert(window.copy(enabled = nowEnabled))
            // Enabling arms the alarms; disabling must drop the already-armed
            // ones, since scheduleNow only adds and would leave them to fire.
            if (nowEnabled) promptScheduler.scheduleNow()
            else promptScheduler.cancelForWindow(window.id)
        }
    }

    fun setLockTimeout(ms: Long) {
        lockRepo.timeoutMs = ms
        lockState.update { it.copy(first = ms, third = it.third + 1) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        lockRepo.biometricEnabled = enabled
        // Mirror the change into the key blob so future biometric unlocks have
        // (or no longer have) a DB key to use. Populating relies on
        // [LockManager.onBiometricEnabledChanged] holding the in-memory DB key
        // — only callable while unlocked, which is the only state from which
        // Settings is reachable.
        lockManager.onBiometricEnabledChanged(enabled)
        lockState.update { it.copy(second = enabled, third = it.third + 1) }
    }

    fun setAllowUnsafeExports(enabled: Boolean) {
        lockRepo.allowUnsafeExports = enabled
        lockState.update { it.copy(third = it.third + 1) }
    }

    fun lockNow() = lockManager.lockNow()

    fun fireTestNotification() {
        promptScheduler.scheduleTestIn(seconds = 15)
    }
}
