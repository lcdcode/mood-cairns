package com.lcdcode.moodcairns.ui.scales

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleEditScreen(
    onBack: () -> Unit,
    viewModel: ScaleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "New scale" else "Edit scale") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold

        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
                enabled = !state.isBuiltIn,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.minValue,
                    onValueChange = viewModel::setMin,
                    label = { Text("Min") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.maxValue,
                    onValueChange = viewModel::setMax,
                    label = { Text("Max") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.step,
                    onValueChange = viewModel::setStep,
                    label = { Text("Step") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Lower is better", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Inverse scale: the slider runs high to low, and charts draw it " +
                            "so improvement points up.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.inverted, onCheckedChange = viewModel::setInverted)
            }

            Text("Color", style = MaterialTheme.typography.labelLarge)
            ColorPalette(
                selected = state.colorArgb,
                onSelect = viewModel::setColor,
            )

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }

            if (state.isBuiltIn) {
                Text(
                    "Built-in scales can't be renamed; you can still adjust the range, step, and color.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.id != 0L && !state.isBuiltIn) {
                OutlinedButton(
                    onClick = {
                        viewModel.loadAffectedEntryCount()
                        showDeleteDialog = true
                    },
                    enabled = !state.deleting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.deleting) "Deleting…" else "Delete scale")
                }
            }
        }
    }

    state.invertDataPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissInvertDataPrompt,
            title = { Text("Remap logged values?") },
            text = { Text(remapWarning(state.name, prompt.entryCount)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSave(remapData = true) }) {
                    Text("Remap")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmSave(remapData = false) }) {
                    Text("Keep values")
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete scale?") },
            text = { Text(deleteWarning(state.name, state.affectedEntryCount)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private fun remapWarning(name: String, entryCount: Int): String {
    val label = name.ifBlank { "this scale" }
    val entries = if (entryCount == 1) "1 entry has" else "$entryCount entries have"
    return "$entries data on \"$label\". Remapping replaces each value v with (min + max) - v " +
        "so past entries keep their meaning under the new direction. " +
        "Choose Keep values if you only meant to change how the scale is displayed."
}

private fun deleteWarning(name: String, affectedEntryCount: Int?): String {
    val label = name.ifBlank { "this scale" }
    val base = "\"$label\" and all data logged on it will be permanently deleted. " +
        "This cannot be undone."
    return when {
        affectedEntryCount == null -> base
        affectedEntryCount == 0 -> "No entries use \"$label\" yet. $base"
        affectedEntryCount == 1 -> "1 entry has data on \"$label\". $base"
        else -> "$affectedEntryCount entries have data on \"$label\". $base"
    }
}

private const val PALETTE_COLUMNS = 5

@Composable
private fun ColorPalette(selected: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScaleEditUiState.PALETTE.chunked(PALETTE_COLUMNS).forEach { rowColors ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowColors.forEach { argb ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        ColorSwatch(
                            argb = argb,
                            isSelected = argb == selected,
                            onSelect = onSelect,
                        )
                    }
                }
                repeat(PALETTE_COLUMNS - rowColors.size) {
                    Box(modifier = Modifier.weight(1f)) {}
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(argb: Int, isSelected: Boolean, onSelect: (Int) -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color(argb),
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    shape = CircleShape,
                ) else Modifier,
            )
            .clickable { onSelect(argb) },
    ) {}
}
