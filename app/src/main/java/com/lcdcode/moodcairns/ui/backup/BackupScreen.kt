package com.lcdcode.moodcairns.ui.backup

import android.text.format.Formatter
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.backup.BackupFileInfo
import com.lcdcode.moodcairns.ui.common.asString
import com.lcdcode.moodcairns.ui.common.rememberSkeletonDateFormat
import java.time.Instant
import java.time.ZoneId

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

    val messageText = state.message?.asString()
    LaunchedEffect(messageText) {
        messageText?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    state.pinPrompt?.let { prompt ->
        when (prompt.mode) {
            PinPromptMode.Export -> SecretDialog(
                title = stringResource(R.string.backup_encrypt_title),
                body = stringResource(R.string.backup_encrypt_body),
                label = stringResource(R.string.backup_passphrase_label),
                confirmLabel = stringResource(R.string.backup_passphrase_confirm_label),
                minLength = BackupViewModel.MIN_PASSPHRASE_LEN,
                onConfirm = viewModel::submitSecret,
                onDismiss = viewModel::cancelPinPrompt,
            )
            PinPromptMode.Import -> SecretDialog(
                title = stringResource(R.string.backup_decrypt_title),
                body = stringResource(R.string.backup_decrypt_body),
                label = stringResource(R.string.backup_passphrase_or_pin_label),
                confirmLabel = null,
                minLength = 0,
                onConfirm = viewModel::submitSecret,
                onDismiss = viewModel::cancelPinPrompt,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
                stringResource(R.string.backup_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = viewModel::requestExport,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.backup_export)) }

            if (state.allowUnsafeExports) {
                OutlinedButton(
                    onClick = viewModel::requestCsvExport,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.backup_export_csv)) }
                Text(
                    stringResource(R.string.backup_export_csv_warning),
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
            ) { Text(stringResource(R.string.backup_import)) }

            Text(
                stringResource(R.string.backup_existing_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (state.files.isEmpty()) {
                Text(
                    stringResource(R.string.backup_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    val fmt = rememberSkeletonDateFormat("yMMMdjm")
    val context = LocalContext.current
    val created = Instant.ofEpochMilli(file.createdAt)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(file.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOf(fmt.format(created), Formatter.formatShortFileSize(context, file.sizeBytes))
                    .joinToString(stringResource(R.string.common_list_separator)),
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
    /** Label of a second "type it again" field, or null to ask only once. */
    confirmLabel: String?,
    minLength: Int,
    onConfirm: (CharArray) -> Unit,
    onDismiss: () -> Unit,
) {
    var secret by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    val tooShort = secret.length < minLength
    val mismatch = confirmLabel != null && confirm != secret
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
                        {
                            Text(
                                pluralStringResource(
                                    R.plurals.backup_passphrase_min_length,
                                    minLength,
                                    minLength,
                                ),
                            )
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirmLabel != null) {
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(confirmLabel) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirm.isNotEmpty() && mismatch,
                        supportingText = if (confirm.isNotEmpty() && mismatch) {
                            { Text(stringResource(R.string.backup_passphrase_mismatch)) }
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
            ) { Text(stringResource(R.string.common_continue)) }
        },
        dismissButton = {
            TextButton(onClick = {
                secret = ""
                confirm = ""
                onDismiss()
            }) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
