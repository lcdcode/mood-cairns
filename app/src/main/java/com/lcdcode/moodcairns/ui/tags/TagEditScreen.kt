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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.ui.common.currentLocale
import com.lcdcode.moodcairns.ui.common.SeedNames
import com.lcdcode.moodcairns.ui.common.asString
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditScreen(
    onBack: () -> Unit,
    viewModel: TagEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val shownName = state.name.takeIf { !state.nameIsDefault }
        ?: SeedNames.tagRes(state.name)?.let { stringResource(it) }
        ?: state.name

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.id == 0L) {
                                R.string.tag_edit_title_new
                            } else {
                                R.string.tag_edit_title_edit
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
                    value = categories.find { it.id == state.categoryId }?.displayName().orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.tag_edit_category_label)) },
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
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.displayName()) },
                            onClick = {
                                viewModel.setCategory(category.id)
                                categoryExpanded = false
                            },
                        )
                    }
                }
            }

            ColumnPicker(selected = state.columnIndex, onSelect = viewModel::setColumn)

            OutlinedTextField(
                value = shownName,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.common_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        stringResource(
                            if (state.deleting) {
                                R.string.common_deleting
                            } else {
                                R.string.tag_edit_delete
                            },
                        ),
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.tag_edit_delete_title)) },
            text = { Text(tagDeleteWarning(shownName, state.affectedEntryCount).asString()) },
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

/** Picks which of the category's columns the tag sits in, shown as 1..[Tag.MAX_COLUMNS]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnPicker(selected: Int, onSelect: (Int) -> Unit) {
    val locale = currentLocale()
    val numberFormat = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.tag_edit_column_label),
            style = MaterialTheme.typography.labelLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Tag.COLUMN_RANGE.forEach { column ->
                SegmentedButton(
                    selected = column == selected,
                    onClick = { onSelect(column) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = column,
                        count = Tag.MAX_COLUMNS,
                    ),
                ) { Text(numberFormat.format(column + 1)) }
            }
        }
    }
}
