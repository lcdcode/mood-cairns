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
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagListScreen(
    onBack: () -> Unit,
    onAdd: (TagCategory) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: TagListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var lists by remember { mutableStateOf(state.byCategory) }
    LaunchedEffect(state.byCategory, isDragging) {
        if (!isDragging) lists = state.byCategory
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        val categoryName = fromKey.substringBefore('/')
        if (categoryName != toKey.substringBefore('/')) return@rememberReorderableLazyListState
        val category = TagCategory.entries.find { it.name == categoryName }
            ?: return@rememberReorderableLazyListState
        val updated = lists[category].orEmpty().toMutableList()
        val fromIdx = updated.indexOfFirst { tagKey(it) == fromKey }
        val toIdx = updated.indexOfFirst { tagKey(it) == toKey }
        if (fromIdx == -1 || toIdx == -1) return@rememberReorderableLazyListState
        updated.add(toIdx, updated.removeAt(fromIdx))
        lists = lists + (category to updated)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            for (category in TagCategory.entries) {
                val tags = lists[category].orEmpty()
                item(key = "hdr/${category.name}") {
                    SectionHeader(
                        text = category.displayName,
                        onAdd = { onAdd(category) },
                    )
                }
                if (tags.isEmpty()) {
                    item(key = "empty/${category.name}") {
                        Text(
                            "No ${category.displayName.lowercase()} yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(tags, key = ::tagKey) { tag ->
                    ReorderableItem(reorderableState, key = tagKey(tag)) { _ ->
                        val dragModifier = Modifier.draggableHandle(
                            onDragStarted = { _ -> isDragging = true },
                            onDragStopped = {
                                viewModel.onReorder(lists[tag.category].orEmpty().map { it.id })
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
}

/** Stable LazyColumn key; '/' cannot appear in a category name so the prefix parses cleanly. */
private fun tagKey(tag: Tag) = "${tag.category.name}/${tag.id}"

@Composable
private fun SectionHeader(text: String, onAdd: () -> Unit) {
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
        TextButton(onClick = onAdd) { Text("Add") }
    }
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
                tag.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(modifier = dragHandleModifier, onClick = {}) {
                Icon(
                    Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
