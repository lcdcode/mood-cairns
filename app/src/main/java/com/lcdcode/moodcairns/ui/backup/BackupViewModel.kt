package com.lcdcode.moodcairns.ui.backup

import android.database.SQLException
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.backup.BackupFileInfo
import com.lcdcode.moodcairns.backup.BackupSerializer
import com.lcdcode.moodcairns.backup.BackupStore
import com.lcdcode.moodcairns.backup.BackupTooLargeException
import com.lcdcode.moodcairns.backup.ConfigImportResult
import com.lcdcode.moodcairns.backup.ConfigImportService
import com.lcdcode.moodcairns.backup.CsvExporter
import com.lcdcode.moodcairns.backup.ImportResult
import com.lcdcode.moodcairns.backup.ImportService
import com.lcdcode.moodcairns.backup.JournalBackupExporter
import com.lcdcode.moodcairns.backup.JournalBackupFormat
import com.lcdcode.moodcairns.backup.JournalImportResult
import com.lcdcode.moodcairns.backup.JournalImportService
import com.lcdcode.moodcairns.backup.JournalMarkdown
import com.lcdcode.moodcairns.backup.JournalMarkdownExporter
import com.lcdcode.moodcairns.security.LockManager
import com.lcdcode.moodcairns.security.LockRepository
import com.lcdcode.moodcairns.settings.BackupReminder
import com.lcdcode.moodcairns.settings.JournalSettings
import com.lcdcode.moodcairns.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

data class BackupUiState(
    val files: List<BackupFileInfo> = emptyList(),
    val busy: Boolean = false,
    val message: UiText? = null,
    val pinPrompt: PinPrompt? = null,
    val allowUnsafeExports: Boolean = false,
    /** Shown as a dialog: the reasons need more room than a snackbar. */
    val configIssue: ConfigIssue? = null,
    val journalEnabled: Boolean = JournalSettings.DEFAULT_ENABLED,
    /** Days offered by the "export one day as Markdown" picker; null while it is closed. */
    val journalDayPicker: List<LocalDate>? = null,
)

/** What a passphrase dialog exports or imports. */
enum class BackupPayload { Mood, Config, Journal }

enum class PinPromptMode(val isExport: Boolean, val payload: BackupPayload) {
    Export(isExport = true, payload = BackupPayload.Mood),
    Import(isExport = false, payload = BackupPayload.Mood),
    ExportConfig(isExport = true, payload = BackupPayload.Config),
    ImportConfig(isExport = false, payload = BackupPayload.Config),
    ExportJournal(isExport = true, payload = BackupPayload.Journal),
    ImportJournal(isExport = false, payload = BackupPayload.Journal),
}

data class PinPrompt(val mode: PinPromptMode, val importUri: Uri? = null)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val serializer: BackupSerializer,
    private val csvExporter: CsvExporter,
    private val store: BackupStore,
    private val importer: ImportService,
    private val configImporter: ConfigImportService,
    private val journalExporter: JournalBackupExporter,
    private val journalImporter: JournalImportService,
    private val markdownExporter: JournalMarkdownExporter,
    private val lockManager: LockManager,
    private val lockRepo: LockRepository,
    private val backupReminder: BackupReminder,
    journalSettings: JournalSettings,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        BackupUiState(allowUnsafeExports = lockRepo.allowUnsafeExports),
    )
    val ui: StateFlow<BackupUiState> = _ui.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            journalSettings.enabled.collect { enabled ->
                _ui.update { it.copy(journalEnabled = enabled) }
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        // MediaStore query is disk I/O; keep it off the UI thread.
        val files = withContext(Dispatchers.IO) { store.list() }
        // Re-read the toggle in case it changed in Settings since this screen opened.
        _ui.update { it.copy(files = files, allowUnsafeExports = lockRepo.allowUnsafeExports) }
    }

    fun requestExport() {
        _ui.update { it.copy(pinPrompt = PinPrompt(PinPromptMode.Export)) }
    }

    fun requestConfigExport() {
        _ui.update { it.copy(pinPrompt = PinPrompt(PinPromptMode.ExportConfig)) }
    }

    fun requestJournalExport() {
        _ui.update { it.copy(pinPrompt = PinPrompt(PinPromptMode.ExportJournal)) }
    }

    /**
     * Export the dataset as plaintext CSV. Only reachable when the user has
     * enabled unsafe exports; re-checked here so a stale UI state can't bypass
     * the gate. No passphrase is involved - the file is deliberately unencrypted.
     */
    fun requestCsvExport() {
        if (!lockRepo.allowUnsafeExports) {
            _ui.update {
                it.copy(message = UiText.Res(R.string.backup_error_unsafe_exports_disabled))
            }
            return
        }
        _ui.update { it.copy(busy = true, message = null) }
        runCsvExport()
    }

    /** Plaintext zip of every journal page, one YYYY-MM-DD.md per day. */
    fun requestJournalMarkdownZip() {
        if (!unsafeExportsAllowed()) return
        _ui.update { it.copy(busy = true, message = null) }
        runPlaintextExport {
            if (markdownExporter.days().isEmpty()) return@runPlaintextExport null
            val name = BackupStore.suggestJournalMarkdownName()
            store.writeStream(name, JournalMarkdown.MIME_ZIP) { out ->
                markdownExporter.exportAll(out)
            }
            name
        }
    }

    /** Opens the day picker, offering only days that have a page. */
    fun requestJournalMarkdownDayPicker() {
        if (!unsafeExportsAllowed()) return
        viewModelScope.launch {
            val days = withContext(Dispatchers.Default) { markdownExporter.days() }
            _ui.update {
                if (days.isEmpty()) {
                    it.copy(message = UiText.Res(R.string.backup_journal_markdown_empty))
                } else {
                    it.copy(journalDayPicker = days)
                }
            }
        }
    }

    fun dismissJournalDayPicker() = _ui.update { it.copy(journalDayPicker = null) }

    /** Plaintext YYYY-MM-DD.md of one day's page. */
    fun exportJournalMarkdownDay(day: LocalDate) {
        _ui.update { it.copy(journalDayPicker = null) }
        if (!unsafeExportsAllowed()) return
        _ui.update { it.copy(busy = true, message = null) }
        runPlaintextExport {
            val content = markdownExporter.pageMarkdown(day) ?: return@runPlaintextExport null
            val name = JournalMarkdown.fileName(day)
            store.writeBackup(name, content, JournalMarkdown.MIME_MARKDOWN)
            name
        }
    }

    /**
     * Re-checks the Settings gate for plaintext exports, so a stale screen cannot bypass it.
     * Shows why when the gate is closed.
     */
    private fun unsafeExportsAllowed(): Boolean {
        if (lockRepo.allowUnsafeExports) return true
        _ui.update {
            it.copy(message = UiText.Res(R.string.backup_error_unsafe_exports_disabled))
        }
        return false
    }

    /** Runs [export] off the UI thread; it returns the written file name, or null if empty. */
    private fun runPlaintextExport(export: suspend () -> String?) = viewModelScope.launch {
        val text = try {
            val name = withContext(Dispatchers.Default) { export() }
            if (name == null) {
                UiText.Res(R.string.backup_journal_markdown_empty)
            } else {
                UiText.Res(R.string.backup_export_csv_success, listOf(name))
            }
        } catch (e: IOException) {
            plaintextExportFailed(e)
        } catch (e: IllegalStateException) {
            plaintextExportFailed(e) // MediaStore refused the file, or the app locked.
        } catch (e: SQLException) {
            plaintextExportFailed(e)
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    private fun plaintextExportFailed(cause: Exception): UiText {
        Log.w(TAG, "Markdown export failed", cause)
        return UiText.withDetail(R.string.backup_export_failed, cause)
    }

    /**
     * Call right before launching the import file picker. The picker is an
     * external full-screen activity, so without a grace window an "Immediate"
     * auto-lock would fire mid-import and drop the picked file.
     */
    fun noteFilePickerOpening() = lockManager.suppressLockForFilePicker()

    fun requestImport(uri: Uri) {
        _ui.update { it.copy(pinPrompt = PinPrompt(PinPromptMode.Import, importUri = uri)) }
    }

    fun requestConfigImport(uri: Uri) {
        _ui.update { it.copy(pinPrompt = PinPrompt(PinPromptMode.ImportConfig, importUri = uri)) }
    }

    fun requestJournalImport(uri: Uri) {
        _ui.update { it.copy(pinPrompt = PinPrompt(PinPromptMode.ImportJournal, importUri = uri)) }
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
        if (prompt.mode.isExport) {
            submitExportPassphrase(prompt.mode, secret)
        } else {
            submitImportSecret(prompt, secret)
        }
    }

    private fun submitExportPassphrase(mode: PinPromptMode, secret: CharArray) {
        // Defence in depth: the dialog already blocks short passphrases,
        // but never trust the UI to be the only gate.
        if (secret.size < MIN_PASSPHRASE_LEN) {
            secret.fill('\u0000')
            _ui.update {
                it.copy(
                    pinPrompt = null,
                    message = UiText.Plural(
                        R.plurals.backup_error_passphrase_too_short,
                        MIN_PASSPHRASE_LEN,
                    ),
                )
            }
            return
        }
        _ui.update { it.copy(pinPrompt = null, busy = true, message = null) }
        runExport(secret, mode.payload)
    }

    private fun submitImportSecret(prompt: PinPrompt, secret: CharArray) {
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
            when (prompt.mode.payload) {
                BackupPayload.Mood -> runImport(uri, secret)
                BackupPayload.Config -> runConfigImport(uri, secret)
                BackupPayload.Journal -> runJournalImport(uri, secret)
            }
        }
    }

    private fun runExport(passphrase: CharArray, payload: BackupPayload) = viewModelScope.launch {
        val text = try {
            // PBKDF2 (600k rounds) + AES + file write; off the UI thread so a
            // large export can't freeze or ANR-crash the app on slow devices.
            withContext(Dispatchers.Default) {
                val (name, success) = when (payload) {
                    BackupPayload.Mood -> {
                        val name = BackupStore.suggestName()
                        store.writeBackup(name, serializer.exportJson(passphrase))
                        name to R.string.backup_export_success
                    }
                    BackupPayload.Config -> {
                        val name = BackupStore.suggestConfigName()
                        store.writeBackup(name, serializer.exportConfigJson(passphrase))
                        name to R.string.backup_config_export_success
                    }
                    BackupPayload.Journal -> {
                        // Streamed straight to the file: no size limit, constant memory.
                        val name = BackupStore.suggestJournalName()
                        store.writeStream(name, JournalBackupFormat.MIME_TYPE) { out ->
                            journalExporter.export(passphrase, out)
                        }
                        name to R.string.backup_journal_export_success
                    }
                }
                backupReminder.postpone()
                UiText.Res(success, listOf(name))
            }
        } catch (e: BackupTooLargeException) {
            Log.w(TAG, "Export refused: over the import size limit", e)
            UiText.Res(R.string.backup_error_export_too_large, listOf(e.maxMegabytes))
        } catch (t: Throwable) {
            Log.w(TAG, "Export failed", t)
            UiText.withDetail(R.string.backup_export_failed, t)
        } finally {
            passphrase.fill('\u0000')
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    private fun runCsvExport() = viewModelScope.launch {
        val text = try {
            // DB read + CSV build + file write; off the UI thread to avoid ANRs.
            withContext(Dispatchers.Default) {
                val csv = csvExporter.exportCsv()
                val name = BackupStore.suggestCsvName()
                store.writeBackup(name, csv, BackupStore.MIME_CSV)
                UiText.Res(R.string.backup_export_csv_success, listOf(name))
            }
        } catch (t: Throwable) {
            Log.w(TAG, "CSV export failed", t)
            UiText.withDetail(R.string.backup_export_failed, t)
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    private fun runImport(uri: Uri, secret: CharArray) = viewModelScope.launch {
        val text = try {
            // PBKDF2 + decrypt + bulk DB writes; keep it off the UI thread.
            val result = withContext(Dispatchers.Default) { importer.importReplace(uri, secret) }
            when (result) {
                is ImportResult.Success -> UiText.Res(
                    R.string.backup_import_success,
                    listOf(result.entries, result.scales, result.tags, result.windows),
                )
                is ImportResult.Failure -> {
                    Log.w(TAG, "Import failed: ${result.error}")
                    UiText.Res(R.string.backup_import_failed, listOf(result.error.toUiText()))
                }
            }
        } finally {
            secret.fill('\u0000')
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    private fun runJournalImport(uri: Uri, secret: CharArray) = viewModelScope.launch {
        val result = try {
            // PBKDF2 + decrypt + validation + DB writes; keep it off the UI thread.
            withContext(Dispatchers.Default) { journalImporter.importReplace(uri, secret) }
        } finally {
            secret.fill('\u0000')
        }
        val text = when (result) {
            is JournalImportResult.Success ->
                UiText.Plural(R.plurals.backup_journal_import_success, result.days)
            is JournalImportResult.Failure -> {
                Log.w(TAG, "Journal import failed: ${result.error}")
                UiText.Res(R.string.backup_journal_import_failed, listOf(result.error.toUiText()))
            }
        }
        _ui.update { it.copy(busy = false, message = text) }
        refresh()
    }

    private fun runConfigImport(uri: Uri, secret: CharArray) = viewModelScope.launch {
        val result = try {
            // PBKDF2 + decrypt + DB writes; keep it off the UI thread.
            withContext(Dispatchers.Default) { configImporter.importConfig(uri, secret) }
        } finally {
            secret.fill('\u0000')
        }
        logConfigImportOutcome(result)
        val text = result.toMessage()
        val issue = result.toConfigIssue()
        _ui.update { it.copy(busy = false, message = text, configIssue = issue) }
        refresh()
    }

    private fun logConfigImportOutcome(result: ConfigImportResult) = when (result) {
        is ConfigImportResult.Success -> Unit
        is ConfigImportResult.Incompatible ->
            Log.w(TAG, "Config import refused: ${result.conflicts.size} conflicts")
        is ConfigImportResult.UnsupportedVersion ->
            Log.w(TAG, "Config import refused: format version ${result.found}")
        is ConfigImportResult.WindowsNotUpdated ->
            Log.w(TAG, "Config imported without prompt windows: ${result.detail}")
        is ConfigImportResult.Failure -> Log.w(TAG, "Config import failed: ${result.error}")
    }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    fun dismissConfigIssue() = _ui.update { it.copy(configIssue = null) }

    companion object {
        private const val TAG = "BackupViewModel"

        /** Snackbar text for [ConfigImportResult]s that do not open the issue dialog. */
        private fun ConfigImportResult.toMessage(): UiText? = when (this) {
            is ConfigImportResult.Success -> UiText.Res(
                R.string.backup_config_import_success,
                listOf(scales, tags, windows),
            )
            is ConfigImportResult.WindowsNotUpdated -> UiText.Res(
                R.string.backup_config_import_partial,
                listOf(scales, tags, detail),
            )
            is ConfigImportResult.Failure ->
                UiText.Res(R.string.backup_config_import_failed, listOf(error.toUiText()))
            is ConfigImportResult.Incompatible, is ConfigImportResult.UnsupportedVersion -> null
        }

        private fun ConfigImportResult.toConfigIssue(): ConfigIssue? = when (this) {
            is ConfigImportResult.Incompatible -> ConfigIssue.WouldLoseData(conflicts)
            is ConfigImportResult.UnsupportedVersion ->
                ConfigIssue.UnsupportedVersion(found, fromConfigFile)
            is ConfigImportResult.Success,
            is ConfigImportResult.WindowsNotUpdated,
            is ConfigImportResult.Failure,
            -> null
        }

        /** Minimum length for a new backup passphrase. Not applied on import. */
        const val MIN_PASSPHRASE_LEN = 8
    }
}
