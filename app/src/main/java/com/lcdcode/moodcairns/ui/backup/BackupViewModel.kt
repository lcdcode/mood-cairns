package com.lcdcode.moodcairns.ui.backup

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.backup.BackupFileInfo
import com.lcdcode.moodcairns.backup.BackupSerializer
import com.lcdcode.moodcairns.backup.BackupStore
import com.lcdcode.moodcairns.backup.ImportResult
import com.lcdcode.moodcairns.backup.ImportService
import com.lcdcode.moodcairns.security.LockManager
import com.lcdcode.moodcairns.security.PinVerifyResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val files: List<BackupFileInfo> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val pinPrompt: PinPrompt? = null,
)

enum class PinPromptMode { Export, Import }

data class PinPrompt(val mode: PinPromptMode, val importUri: Uri? = null)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val serializer: BackupSerializer,
    private val store: BackupStore,
    private val importer: ImportService,
    private val lockManager: LockManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(BackupUiState())
    val ui: StateFlow<BackupUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        val files = store.list()
        _ui.update { it.copy(files = files) }
    }

    fun requestExport() {
        _ui.update { it.copy(pinPrompt = PinPrompt(PinPromptMode.Export)) }
    }

    fun requestImport(uri: Uri) {
        _ui.update { it.copy(pinPrompt = PinPrompt(PinPromptMode.Import, importUri = uri)) }
    }

    fun cancelPinPrompt() {
        _ui.update { it.copy(pinPrompt = null) }
    }

    /**
     * Confirm a PIN entered in the export/import dialog. [pin] is zeroed by this
     * function — callers must not reuse it.
     */
    fun submitPin(pin: CharArray) {
        val prompt = _ui.value.pinPrompt
        if (prompt == null) {
            pin.fill('0')
            return
        }
        when (prompt.mode) {
            PinPromptMode.Export -> {
                // Confirm the user really knows the device PIN before exporting
                // every entry to disk under it. Throttled through LockManager so
                // this dialog can't be used to brute-force the PIN while unlocked.
                when (val verify = lockManager.verifyPinThrottled(pin)) {
                    is PinVerifyResult.RateLimited -> {
                        pin.fill('0')
                        _ui.update {
                            it.copy(
                                pinPrompt = null,
                                message = "Too many attempts. Try again in ${ceilSeconds(verify.retryAfterMs)}s",
                            )
                        }
                    }
                    PinVerifyResult.WrongPin -> {
                        pin.fill('0')
                        _ui.update { it.copy(pinPrompt = null, message = "Incorrect PIN") }
                    }
                    PinVerifyResult.Success -> {
                        _ui.update { it.copy(pinPrompt = null, busy = true, message = null) }
                        runExport(pin)
                    }
                }
            }
            PinPromptMode.Import -> {
                // Do NOT compare against the current device PIN: the backup envelope
                // carries its own salt + iteration count, so any install (including
                // a fresh one after device loss) should be able to restore as long
                // as the correct backup-encryption PIN is provided. AES-GCM's tag
                // check is the authoritative gate.
                val uri = prompt.importUri
                if (uri == null) {
                    pin.fill('0')
                    _ui.update { it.copy(pinPrompt = null, busy = false) }
                } else {
                    _ui.update { it.copy(pinPrompt = null, busy = true, message = null) }
                    runImport(uri, pin)
                }
            }
        }
    }

    private fun runExport(pin: CharArray) = viewModelScope.launch {
        val text = try {
            val json = serializer.exportJson(pin)
            val name = store.suggestName()
            store.writeBackup(name, json)
            "Exported $name"
        } catch (t: Throwable) {
            Log.w(TAG, "Export failed", t)
            "Export failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            pin.fill('0')
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    private fun runImport(uri: Uri, pin: CharArray) = viewModelScope.launch {
        val text = try {
            val result = importer.importReplace(uri, pin)
            when (result) {
                is ImportResult.Success ->
                    "Imported ${result.entries} entries, ${result.scales} scales, ${result.windows} windows"
                is ImportResult.Failure -> "Import failed: ${result.message}"
            }
        } finally {
            pin.fill('0')
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    companion object {
        private const val TAG = "BackupViewModel"

        private fun ceilSeconds(ms: Long): Long = ((ms + 999) / 1000).coerceAtLeast(1)
    }
}
