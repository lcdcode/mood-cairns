package com.lcdcode.moodcairns.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.entity.PromptWindow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAddWindow: () -> Unit,
    onEditWindow: (Long) -> Unit,
    onChangePin: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showUnsafeExportWarning by remember { mutableStateOf(false) }

    if (showUnsafeExportWarning) {
        UnsafeExportWarningDialog(
            onConfirm = {
                viewModel.setAllowUnsafeExports(true)
                showUnsafeExportWarning = false
            },
            onDismiss = { showUnsafeExportWarning = false },
        )
    }

    // Setting or removing a PIN happens on another screen; refresh on return so
    // the Security section reflects the current mode.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader("Prompt windows") }
            items(state.windows, key = { "w-${it.id}" }) { w ->
                PromptWindowRow(
                    window = w,
                    onEdit = { onEditWindow(w.id) },
                    onToggle = { viewModel.toggleWindowEnabled(w) },
                )
            }
            item {
                OutlinedButton(onClick = onAddWindow, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Add window")
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Security") }
            if (state.pinSet) {
                item {
                    LockTimeoutSection(
                        selectedMs = state.lockTimeoutMs,
                        onSelect = viewModel::setLockTimeout,
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Biometric unlock", modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.biometricEnabled,
                            onCheckedChange = viewModel::setBiometricEnabled,
                        )
                    }
                }
                item {
                    OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                        Text("Change PIN")
                    }
                }
                item {
                    OutlinedButton(onClick = viewModel::lockNow, modifier = Modifier.fillMaxWidth()) {
                        Text("Lock now")
                    }
                }
            } else {
                item {
                    Text(
                        "No PIN set. Your data is encrypted on this device but protected only " +
                            "by your device's keystore, not by a PIN. Auto-lock, biometric unlock, " +
                            "and lock-now are unavailable without a PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                        Text("Set PIN")
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Backup & export") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Allow unsafe exports", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.allowUnsafeExports,
                        onCheckedChange = { checked ->
                            // Enabling exposes plaintext data, so gate it behind a
                            // warning; disabling is always safe and immediate.
                            if (checked) showUnsafeExportWarning = true
                            else viewModel.setAllowUnsafeExports(false)
                        },
                    )
                }
            }
            item {
                Text(
                    "Enables an unencrypted CSV export under Backup & import. Off by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader("Diagnostics") }
            item {
                OutlinedButton(
                    onClick = viewModel::fireTestNotification,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Test notification (fires in 15 s)")
                }
            }
        }
    }
}

@Composable
private fun UnsafeExportWarningDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow unsafe exports?") },
        text = {
            Text(
                "This adds an option to export your data as an unencrypted CSV file to shared " +
                    "storage. That file is plain text with no passphrase: cloud backup services, " +
                    "file managers, and any other app that can read shared storage will be able " +
                    "to read all of your entries.\n\n" +
                    "Only enable this if you understand and accept that danger.",
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Enable") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun PromptWindowRow(
    window: PromptWindow,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(window.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${window.startTime} – ${window.endTime} · ${window.slot.name.lowercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = window.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockTimeoutSection(selectedMs: Long, onSelect: (Long) -> Unit) {
    val options = listOf(
        0L to "Immediate",
        30_000L to "30 s",
        60_000L to "1 min",
        300_000L to "5 min",
        900_000L to "15 min",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Auto-lock after background", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (ms, label) ->
                FilterChip(
                    selected = ms == selectedMs,
                    onClick = { onSelect(ms) },
                    label = { Text(label) },
                )
            }
        }
    }
}
