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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.SeedNames
import com.lcdcode.moodcairns.ui.common.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleEditScreen(
    onBack: () -> Unit,
    viewModel: ScaleEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    // Built-in names cannot be edited, so showing the translation is display only.
    val shownName = SeedNames.scaleRes(state.name, state.isBuiltIn)?.let { stringResource(it) }
        ?: state.name
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.id == 0L) {
                                R.string.scale_edit_title_new
                            } else {
                                R.string.scale_edit_title_edit
                            },
                        ),
                    )
                },
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
        if (!state.loaded) return@Scaffold

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = shownName,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.common_name_label)) },
                singleLine = true,
                enabled = !state.isBuiltIn,
                modifier = Modifier.fillMaxWidth(),
            )

            // Min, Max, and Default accept negatives, but KeyboardType.Number
            // and .Decimal map to an unsigned input type, so soft keyboards show
            // no minus key. Phone gives a dialpad that has one; the sanitizers
            // strip the other dialpad characters.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.minValue,
                    onValueChange = viewModel::setMin,
                    label = { Text(stringResource(R.string.scale_edit_min_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.maxValue,
                    onValueChange = viewModel::setMax,
                    label = { Text(stringResource(R.string.scale_edit_max_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.step,
                    onValueChange = viewModel::setStep,
                    label = { Text(stringResource(R.string.scale_edit_step_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.defaultValue,
                onValueChange = viewModel::setDefault,
                label = { Text(stringResource(R.string.scale_edit_default_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = {
                    Text(stringResource(R.string.scale_edit_default_hint))
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.scale_edit_lower_better),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.scale_edit_lower_better_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.inverted, onCheckedChange = viewModel::setInverted)
            }

            Text(
                stringResource(R.string.scale_edit_color_label),
                style = MaterialTheme.typography.labelLarge,
            )
            ColorPalette(
                selected = state.colorArgb,
                onSelect = viewModel::setColor,
            )

            state.error?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.saving) R.string.common_saving else R.string.common_save,
                    ),
                )
            }

            if (state.isBuiltIn) {
                Text(
                    stringResource(R.string.scale_edit_built_in_hint),
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
                    Text(
                        stringResource(
                            if (state.deleting) {
                                R.string.common_deleting
                            } else {
                                R.string.scale_edit_delete
                            },
                        ),
                    )
                }
            }
        }
    }

    state.invertDataPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissInvertDataPrompt,
            title = { Text(stringResource(R.string.scale_edit_remap_title)) },
            text = { Text(remapWarning(shownName, prompt.entryCount).asString()) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSave(remapData = true) }) {
                    Text(stringResource(R.string.scale_edit_remap_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmSave(remapData = false) }) {
                    Text(stringResource(R.string.scale_edit_remap_keep))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.scale_edit_delete_title)) },
            text = { Text(deleteWarning(shownName, state.affectedEntryCount).asString()) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
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
