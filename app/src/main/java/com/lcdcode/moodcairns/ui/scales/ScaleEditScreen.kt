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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

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
        }
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
