package com.lcdcode.moodcairns.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.ui.common.UiText
import com.lcdcode.moodcairns.ui.common.asString
import com.lcdcode.moodcairns.ui.common.displayNameRes

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
                title = { Text(stringResource(R.string.settings_title)) },
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
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.common_loading))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_prompt_windows)) }
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
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.settings_add_window))
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.settings_section_security)) }
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
                        Text(
                            stringResource(R.string.settings_biometric_unlock),
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = state.biometricEnabled,
                            onCheckedChange = viewModel::setBiometricEnabled,
                        )
                    }
                }
                item {
                    OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_change_pin))
                    }
                }
                item {
                    OutlinedButton(onClick = viewModel::lockNow, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_lock_now))
                    }
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.settings_no_pin_explanation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    OutlinedButton(onClick = onChangePin, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_set_pin))
                    }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.settings_section_backup)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_allow_unsafe_exports),
                        modifier = Modifier.weight(1f),
                    )
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
                    stringResource(R.string.settings_allow_unsafe_exports_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item { SectionHeader(stringResource(R.string.settings_section_diagnostics)) }
            item {
                OutlinedButton(
                    onClick = viewModel::fireTestNotification,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        pluralStringResource(
                            R.plurals.settings_test_notification,
                            SettingsViewModel.TEST_NOTIFICATION_DELAY_SECONDS,
                            SettingsViewModel.TEST_NOTIFICATION_DELAY_SECONDS,
                        ),
                    )
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
        title = { Text(stringResource(R.string.settings_unsafe_exports_dialog_title)) },
        text = { Text(stringResource(R.string.settings_unsafe_exports_dialog_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.common_enable)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
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
                    stringResource(
                        R.string.settings_window_summary,
                        window.startTime.toString(),
                        window.endTime.toString(),
                        stringResource(window.slot.displayNameRes()),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = window.enabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LockTimeoutSection(selectedMs: Long, onSelect: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.settings_auto_lock_label),
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LOCK_TIMEOUT_OPTIONS_MS.forEach { ms ->
                FilterChip(
                    selected = ms == selectedMs,
                    onClick = { onSelect(ms) },
                    label = { Text(lockTimeoutLabel(ms).asString()) },
                )
            }
        }
    }
}

private val LOCK_TIMEOUT_OPTIONS_MS = listOf(0L, 30_000L, 60_000L, 300_000L, 900_000L)
private const val MS_PER_SECOND = 1_000L
private const val MS_PER_MINUTE = 60_000L

/** Chip label for an auto-lock timeout: "Immediate", whole minutes, or seconds. */
internal fun lockTimeoutLabel(timeoutMs: Long): UiText = when {
    timeoutMs == 0L -> UiText.Res(R.string.settings_auto_lock_immediate)
    timeoutMs % MS_PER_MINUTE == 0L ->
        UiText.Plural(R.plurals.common_duration_minutes_short, (timeoutMs / MS_PER_MINUTE).toInt())
    else ->
        UiText.Plural(R.plurals.common_duration_seconds_short, (timeoutMs / MS_PER_SECOND).toInt())
}
