package com.lcdcode.moodcairns.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.ui.common.displayName
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagListScreen(
    onBack: () -> Unit,
    onAdd: (categoryId: String) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: TagListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var renamingId by rememberSaveable { mutableStateOf<String?>(null) }
    var lists by remember { mutableStateOf(state.byCategory) }
    LaunchedEffect(state.byCategory, isDragging) {
        if (!isDragging) lists = state.byCategory
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        val categoryId = fromKey.substringBeforeLast('/')
        if (categoryId != toKey.substringBeforeLast('/')) return@rememberReorderableLazyListState
        val updated = lists[categoryId].orEmpty().toMutableList()
        val fromIdx = updated.indexOfFirst { tagKey(categoryId, it) == fromKey }
        val toIdx = updated.indexOfFirst { tagKey(categoryId, it) == toKey }
        if (fromIdx == -1 || toIdx == -1) return@rememberReorderableLazyListState
        updated.add(toIdx, updated.removeAt(fromIdx))
        lists = lists + (categoryId to updated)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tag_list_title)) },
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
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (category in state.categories) {
                val tags = lists[category.id].orEmpty()
                item(key = "hdr/${category.id}") {
                    SectionHeader(
                        text = category.displayName(),
                        // Other is the catch-all for deleted categories; its name is fixed.
                        onRename = { renamingId = category.id }
                            .takeIf { category.id != TagCategory.OTHER_ID },
                        onAdd = { onAdd(category.id) },
                    )
                }
                if (tags.isEmpty()) {
                    item(key = "empty/${category.id}") {
                        Text(
                            stringResource(R.string.tag_list_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(tags, key = { tagKey(category.id, it) }) { tag ->
                    ReorderableItem(reorderableState, key = tagKey(category.id, tag)) { _ ->
                        val dragModifier = Modifier.draggableHandle(
                            onDragStarted = { _ -> isDragging = true },
                            onDragStopped = {
                                viewModel.onReorder(lists[category.id].orEmpty().map { it.id })
                                isDragging = false
                            },
                        )
                        TagRow(
                            tag = tag,
                            onClick = { onEdit(tag.id) },
                            dragHandleModifier = dragModifier,
                        )
                    }
                }
            }
        }
    }

    state.categories.find { it.id == renamingId }?.let { category ->
        val defaultName = category.defaultName()
        RenameCategoryDialog(
            currentName = category.displayName(),
            defaultName = defaultName,
            onDismiss = { renamingId = null },
            onSave = { raw ->
                viewModel.renameCategory(category.id, raw, defaultName)
                renamingId = null
            },
        )
    }
}

/**
 * Stable LazyColumn key "<section category id>/<tag id>". Tag ids are numbers, so the
 * last '/' always splits it, even if a category id contained one.
 */
private fun tagKey(sectionId: String, tag: Tag) = "$sectionId/${tag.id}"

@Composable
private fun SectionHeader(text: String, onRename: (() -> Unit)?, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(top = 4.dp),
        )
        if (onRename != null) {
            IconButton(onClick = onRename) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onAdd) { Text(stringResource(R.string.common_add)) }
    }
}

/**
 * Renames a category using only shared strings, so no new translations are needed.
 * Saving a blank field, or the default name, restores the translated default.
 */
@Composable
private fun RenameCategoryDialog(
    currentName: String,
    defaultName: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(currentName) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { new ->
                    if (new.codePointCount(0, new.length) <= TagCategory.MAX_LABEL_LENGTH) {
                        draft = new
                    }
                },
                label = { Text(stringResource(R.string.common_name_label)) },
                placeholder = defaultName?.let { { Text(it) } },
                singleLine = true,
                trailingIcon = {
                    if (draft.isNotEmpty()) {
                        IconButton(onClick = { draft = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.common_clear),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun TagRow(
    tag: Tag,
    onClick: () -> Unit,
    dragHandleModifier: Modifier,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tag.displayName(),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(modifier = dragHandleModifier, onClick = {}) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = stringResource(R.string.common_reorder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
