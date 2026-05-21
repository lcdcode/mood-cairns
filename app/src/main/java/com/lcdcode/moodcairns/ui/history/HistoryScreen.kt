package com.lcdcode.moodcairns.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.Scale
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onAddPast: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetTarget by remember { mutableStateOf<EntryWithValues?>(null) }
    var deleteTarget by remember { mutableStateOf<EntryWithValues?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPast) {
                Icon(Icons.Default.Add, contentDescription = "Add past entry")
            }
        },
    ) { padding ->
        if (state.entries.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No entries yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val grouped = state.entries.groupBy {
            it.entry.recordedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        }.toSortedMap(compareByDescending { it })

        val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")
        val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            grouped.forEach { (day, items) ->
                item(key = "day-$day") { DayHeader(day, dayFmt) }
                items(items, key = { it.entry.id }) { e ->
                    EntryCard(
                        entry = e,
                        scales = state.scalesById,
                        timeFmt = timeFmt,
                        onLongPress = { sheetTarget = e },
                    )
                }
            }
        }
    }

    sheetTarget?.let { target ->
        EntryActionSheet(
            onDismiss = { sheetTarget = null },
            onEdit = {
                sheetTarget = null
                onEdit(target.entry.id)
            },
            onDelete = {
                sheetTarget = null
                deleteTarget = target
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete entry?") },
            text = { Text("This entry will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.entry.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryActionSheet(
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            ListItem(
                headlineContent = { Text("Edit") },
                leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                modifier = Modifier.clickable(onClick = onEdit),
            )
            ListItem(
                headlineContent = { Text("Delete") },
                leadingContent = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable(onClick = onDelete),
            )
        }
    }
}

@Composable
private fun DayHeader(day: LocalDate, fmt: DateTimeFormatter) {
    Text(
        text = fmt.format(day),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: EntryWithValues,
    scales: Map<Long, Scale>,
    timeFmt: DateTimeFormatter,
    onLongPress: () -> Unit,
) {
    val time = entry.entry.recordedAt.atZone(ZoneId.systemDefault()).toLocalTime()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(timeFmt.format(time), fontWeight = FontWeight.Medium)
                SlotChip(entry.entry.slot)
            }
            HorizontalDivider()
            entry.values.forEach { v ->
                val scale = scales[v.scaleId] ?: return@forEach
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(scale.colorArgb),
                        modifier = Modifier.size(10.dp),
                    ) {}
                    Text(
                        "  ${scale.name}",
                        modifier = Modifier.weight(1f),
                    )
                    Text("${formatHistoryValue(v.value)} / ${scale.maxValue}")
                }
            }
            entry.entry.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SlotChip(slot: PromptSlot) {
    AssistChip(onClick = {}, label = { Text(slot.name.lowercase().replaceFirstChar { it.uppercase() }) })
}

private fun formatHistoryValue(v: Float): String {
    val rounded = kotlin.math.round(v)
    return if (kotlin.math.abs(v - rounded) < 1e-3f) rounded.toInt().toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.')
}
