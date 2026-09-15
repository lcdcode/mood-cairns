package com.lcdcode.moodcairns.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.entity.TagCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditScreen(
    onBack: () -> Unit,
    viewModel: TagEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "New tag" else "Edit tag") },
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
            // A dropdown rather than a segmented row: there are more categories
            // than fit across a phone screen.
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = state.category.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    TagCategory.entries.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName) },
                            onClick = {
                                viewModel.setCategory(category)
                                categoryExpanded = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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

            if (state.id != 0L) {
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
                    Text(if (state.deleting) "Deleting…" else "Delete tag")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete tag?") },
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

private fun deleteWarning(name: String, affectedEntryCount: Int?): String {
    val label = name.ifBlank { "this tag" }
    val base = "Entries keep their data; the tag is just removed from them."
    return when {
        affectedEntryCount == null -> "\"$label\" will be deleted. $base"
        affectedEntryCount == 0 -> "No entries use \"$label\" yet."
        affectedEntryCount == 1 -> "1 entry is tagged \"$label\". $base"
        else -> "$affectedEntryCount entries are tagged \"$label\". $base"
    }
}
