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
     * Confirm a secret entered in the export/import dialog. [secret] is zeroed by
     * this function — callers must not reuse it. For export it is the new backup
     * passphrase (the dialog enforces the minimum length and confirmation match);
     * for import it is whatever the backup was encrypted with, including an old
     * device PIN, so no length rule is applied here.
     */
    fun submitSecret(secret: CharArray) {
        val prompt = _ui.value.pinPrompt
        if (prompt == null) {
            secret.fill('\u0000')
            return
        }
        when (prompt.mode) {
            PinPromptMode.Export -> {
                // Defence in depth: the dialog already blocks short passphrases,
                // but never trust the UI to be the only gate.
                if (secret.size < MIN_PASSPHRASE_LEN) {
                    secret.fill('\u0000')
                    _ui.update {
                        it.copy(
                            pinPrompt = null,
                            message = "Passphrase must be at least $MIN_PASSPHRASE_LEN characters",
                        )
                    }
                    return
                }
                _ui.update { it.copy(pinPrompt = null, busy = true, message = null) }
                runExport(secret)
            }
            PinPromptMode.Import -> {
                // The envelope carries its own salt + iteration count, so any
                // install (including a fresh one after device loss) can restore
                // given the correct secret. AES-GCM's tag check is the gate; no
                // minimum length, since legacy backups used short PINs.
                val uri = prompt.importUri
                if (uri == null) {
                    secret.fill('\u0000')
                    _ui.update { it.copy(pinPrompt = null, busy = false) }
                } else {
                    _ui.update { it.copy(pinPrompt = null, busy = true, message = null) }
                    runImport(uri, secret)
                }
            }
        }
    }

    private fun runExport(passphrase: CharArray) = viewModelScope.launch {
        val text = try {
            val json = serializer.exportJson(passphrase)
            val name = store.suggestName()
            store.writeBackup(name, json)
            "Exported $name"
        } catch (t: Throwable) {
            Log.w(TAG, "Export failed", t)
            "Export failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            passphrase.fill('\u0000')
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    private fun runImport(uri: Uri, secret: CharArray) = viewModelScope.launch {
        val text = try {
            val result = importer.importReplace(uri, secret)
            when (result) {
                is ImportResult.Success ->
                    "Imported ${result.entries} entries, ${result.scales} scales, ${result.windows} windows"
                is ImportResult.Failure -> "Import failed: ${result.message}"
            }
        } finally {
            secret.fill('\u0000')
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    companion object {
        private const val TAG = "BackupViewModel"

        /** Minimum length for a new backup passphrase. Not applied on import. */
        const val MIN_PASSPHRASE_LEN = 8
    }
}
