package com.moodcairns.ui.backup

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moodcairns.backup.BackupFileInfo
import com.moodcairns.backup.BackupSerializer
import com.moodcairns.backup.BackupStore
import com.moodcairns.backup.ImportResult
import com.moodcairns.backup.ImportService
import com.moodcairns.security.LockRepository
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
    private val lockRepo: LockRepository,
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
        if (!lockRepo.verifyPin(pin)) {
            pin.fill('0')
            _ui.update { it.copy(pinPrompt = null, message = "Incorrect PIN") }
            return
        }
        _ui.update { it.copy(pinPrompt = null, busy = true, message = null) }
        when (prompt.mode) {
            PinPromptMode.Export -> runExport(pin)
            PinPromptMode.Import -> {
                val uri = prompt.importUri
                if (uri == null) {
                    pin.fill('0')
                    _ui.update { it.copy(busy = false) }
                } else {
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
    }
}
