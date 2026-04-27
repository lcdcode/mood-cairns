package com.moodcairns.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodcairns.data.entity.Scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.savedId) {
        if (state.savedId != null) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How are you?") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
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
                    value = state.values[scale.id] ?: ((scale.minValue + scale.maxValue) / 2),
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

@Composable
private fun ScaleSlider(
    scale: Scale,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    val accent = Color(scale.colorArgb)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(scale.name, fontWeight = FontWeight.Medium)
            Text("$value / ${scale.maxValue}")
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = scale.minValue.toFloat()..scale.maxValue.toFloat(),
            steps = ((scale.maxValue - scale.minValue) / scale.step - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
            ),
            modifier = Modifier.semantics {
                contentDescription = "${scale.name}, value $value out of ${scale.maxValue}"
            },
        )
    }
}
