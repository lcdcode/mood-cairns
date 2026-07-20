package com.lcdcode.moodcairns.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.backup.BackupFileInfo
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::requestImport) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    state.pinPrompt?.let { prompt ->
        when (prompt.mode) {
            PinPromptMode.Export -> SecretDialog(
                title = "Encrypt backup",
                body = "Choose a passphrase to encrypt this backup. You'll need this exact " +
                    "passphrase to restore it — even on a fresh install. It is not your app " +
                    "PIN and cannot be recovered if you forget it.",
                label = "Passphrase",
                requireConfirmation = true,
                minLength = BackupViewModel.MIN_PASSPHRASE_LEN,
                onConfirm = viewModel::submitSecret,
                onDismiss = viewModel::cancelPinPrompt,
            )
            PinPromptMode.Import -> SecretDialog(
                title = "Decrypt backup",
                body = "Enter the passphrase used to encrypt this backup. For older backups, " +
                    "this is the PIN that was set when they were created.",
                label = "Passphrase or PIN",
                requireConfirmation = false,
                minLength = 0,
                onConfirm = viewModel::submitSecret,
                onDismiss = viewModel::cancelPinPrompt,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & import") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) }
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Backups are AES-GCM encrypted with a key derived from a passphrase you choose and written to Documents/MoodCairns. Syncthing or any file manager can sync them off-device — the app never uploads anything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = viewModel::requestExport,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export now") }

            if (state.allowUnsafeExports) {
                OutlinedButton(
                    onClick = viewModel::requestCsvExport,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export unencrypted CSV") }
                Text(
                    "The CSV is plain text and NOT encrypted. Anyone or anything that can read " +
                        "your Documents folder - cloud backup, file managers, other apps - can read " +
                        "your entries. Disable unsafe exports in Settings to hide this option.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            OutlinedButton(
                onClick = {
                    viewModel.noteFilePickerOpening()
                    importLauncher.launch(arrayOf("application/json"))
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import from file (replaces all data)") }

            Text(
                "Existing backups",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (state.files.isEmpty()) {
                Text("None yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.files, key = { it.uri }) { file ->
                        BackupRow(file)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupRow(file: BackupFileInfo) {
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${fmt.format(Date(file.createdAt))} · ${file.sizeBytes / 1024} KB",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecretDialog(
    title: String,
    body: String,
    label: String,
    requireConfirmation: Boolean,
    minLength: Int,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val tooShort = secret.length < minLength
    val mismatch = requireConfirmation && confirm != secret
    val canSubmit = secret.isNotEmpty() && !tooShort && !mismatch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(label) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = secret.isNotEmpty() && tooShort,
                    supportingText = if (minLength > 0) {
                        { Text("At least $minLength characters") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text("Confirm $label") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirm.isNotEmpty() && mismatch,
                        supportingText = if (confirm.isNotEmpty() && mismatch) {
                            { Text("Passphrases do not match") }
                        } else {
                            null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val chars = secret.toCharArray()
                    secret = ""
                    confirm = ""
                    onConfirm(chars)
                },
                enabled = canSubmit,
            ) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = {
                secret = ""
                confirm = ""
                onDismiss()
            }) { Text("Cancel") }
        },
    )
}
