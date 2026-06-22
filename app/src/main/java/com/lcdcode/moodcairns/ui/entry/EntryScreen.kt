package com.lcdcode.moodcairns.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.savedId) {
        if (state.savedId != null) onSaved()
    }

    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editingId != null) "Edit entry" else "How are you?") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.scales.isEmpty() && !state.saving) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No scales configured. Add one from Manage scales.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.scales.forEach { scale ->
                ScaleSlider(
                    scale = scale,
                    value = state.values[scale.id] ?: ((scale.minValue + scale.maxValue) / 2f),
                    onValueChange = { viewModel.setValue(scale.id, it) },
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Spacer(Modifier.height(8.dp))

            PromptSlotRow(
                windows = state.windows,
                extraWindow = state.extraWindow,
                selectedSlot = state.slot,
                selectedWindowId = state.promptWindowId,
                onWindow = viewModel::selectWindow,
                onManual = viewModel::selectManual,
                onCustom = viewModel::selectCustom,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = viewModel::save,
                enabled = !state.saving && state.scales.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptSlotRow(
    windows: List<PromptWindow>,
    extraWindow: PromptWindow?,
    selectedSlot: PromptSlot,
    selectedWindowId: Long?,
    onWindow: (PromptWindow) -> Unit,
    onManual: () -> Unit,
    onCustom: () -> Unit,
) {
    // Append the pinned disabled window (if any) so an edited entry's slot still
    // renders even though it is no longer offered for new entries.
    val options = remember(windows, extraWindow) {
        if (extraWindow != null && windows.none { it.id == extraWindow.id }) windows + extraWindow
        else windows
    }
    Column {
        Text(
            "Prompt slot",
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { window ->
                FilterChip(
                    selected = selectedWindowId == window.id,
                    onClick = { onWindow(window) },
                    label = { Text(window.label) },
                )
            }
            FilterChip(
                selected = selectedWindowId == null && selectedSlot == PromptSlot.MANUAL,
                onClick = onManual,
                label = { Text("Manual") },
            )
            FilterChip(
                selected = selectedWindowId == null && selectedSlot == PromptSlot.CUSTOM,
                onClick = onCustom,
                label = { Text("Custom") },
            )
        }
    }
}

@Composable
private fun ScaleSlider(
    scale: Scale,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val accent = Color(scale.colorArgb)
    val display = formatEntryValue(value)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(scale.name, fontWeight = FontWeight.Medium)
            Text("$display / ${scale.maxValue}")
        }
        Slider(
            value = value,
            onValueChange = { onValueChange(snapToStep(it, scale)) },
            valueRange = scale.minValue.toFloat()..scale.maxValue.toFloat(),
            steps = (((scale.maxValue - scale.minValue) / scale.step).toInt() - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
            modifier = Modifier.semantics {
                contentDescription = "${scale.name}, value $display out of ${scale.maxValue}"
            },
        )
    }
}

private fun snapToStep(raw: Float, scale: Scale): Float {
    if (scale.step <= 0f) return raw
    val offset = raw - scale.minValue
    val snapped = scale.minValue + kotlin.math.round(offset / scale.step) * scale.step
    return snapped.coerceIn(scale.minValue.toFloat(), scale.maxValue.toFloat())
}

private fun formatEntryValue(v: Float): String {
    val rounded = kotlin.math.round(v)
    return if (kotlin.math.abs(v - rounded) < 1e-3f) rounded.toInt().toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.')
}
