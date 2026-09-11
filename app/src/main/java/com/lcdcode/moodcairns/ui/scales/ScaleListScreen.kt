package com.lcdcode.moodcairns.ui.scales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.ui.common.rangeLabel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ScaleListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var activeList by remember { mutableStateOf(state.active) }
    LaunchedEffect(state.active, isDragging) {
        if (!isDragging) activeList = state.active
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        if (!fromKey.startsWith("a-") || !toKey.startsWith("a-")) {
            return@rememberReorderableLazyListState
        }
        val updated = activeList.toMutableList()
        val fromIdx = updated.indexOfFirst { "a-${it.id}" == fromKey }
        val toIdx = updated.indexOfFirst { "a-${it.id}" == toKey }
        if (fromIdx == -1 || toIdx == -1) return@rememberReorderableLazyListState
        updated.add(toIdx, updated.removeAt(fromIdx))
        activeList = updated
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scales") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add scale")
            }
        },
    ) { padding ->
        if (activeList.isEmpty() && state.archived.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scales yet.")
            }
            return@Scaffold
        }

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (activeList.isNotEmpty()) {
                item(key = "hdr-active") {
                    SectionHeader("Active")
                }
                items(activeList, key = { "a-${it.id}" }) { scale ->
                    ReorderableItem(reorderableState, key = "a-${scale.id}") { _ ->
                        val dragModifier = Modifier.draggableHandle(
                            onDragStarted = { _ -> isDragging = true },
                            onDragStopped = {
                                viewModel.onReorder(activeList.map { it.id })
                                isDragging = false
                            },
                        )
                        ScaleRow(
                            scale = scale,
                            onClick = { onEdit(scale.id) },
                            onToggleArchive = { viewModel.setArchived(scale.id, true) },
                            dragHandleModifier = dragModifier,
                        )
                    }
                }
            }
            if (state.archived.isNotEmpty()) {
                item(key = "hdr-archived") {
                    SectionHeader("Archived")
                }
                items(state.archived, key = { "z-${it.id}" }) { scale ->
                    ScaleRow(
                        scale = scale,
                        onClick = { onEdit(scale.id) },
                        onToggleArchive = { viewModel.setArchived(scale.id, false) },
                        archived = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ScaleRow(
    scale: Scale,
    onClick: () -> Unit,
    onToggleArchive: () -> Unit,
    archived: Boolean = false,
    dragHandleModifier: Modifier? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color(scale.colorArgb),
                modifier = Modifier.size(16.dp),
            ) {}
            Column(modifier = Modifier.weight(1f)) {
                Text(scale.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${rangeLabel(scale.minValue, scale.maxValue)} · step ${formatStep(scale.step)}" +
                        (if (scale.inverted) " · lower is better" else "") +
                        if (scale.isBuiltIn) " · built-in" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onToggleArchive) {
                Text(if (archived) "Unarchive" else "Archive")
            }
            if (dragHandleModifier != null) {
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
}
