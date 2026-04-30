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
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun toggleWindowEnabled(window: PromptWindow) {
        viewModelScope.launch {
            windowsRepo.upsert(window.copy(enabled = !window.enabled))
            promptScheduler.scheduleNow()
        }
    }

    fun setLockTimeout(ms: Long) {
        lockRepo.timeoutMs = ms
        lockState.update { it.copy(first = ms, third = it.third + 1) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        lockRepo.biometricEnabled = enabled
        lockState.update { it.copy(second = enabled, third = it.third + 1) }
    }

    fun lockNow() = lockManager.lockNow()

    fun fireTestNotification() {
        promptScheduler.scheduleTestIn(seconds = 15)
    }
}
