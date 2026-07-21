package com.lcdcode.moodcairns.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
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
        val filteredEntries = remember(
            state.entries, state.dateFilter, state.slotFilter, state.searchQuery, state.tagFilter,
        ) {
            state.entries.filter { e ->
                val dateOk = state.dateFilter == null ||
                    e.entry.recordedAt.atZone(ZoneId.systemDefault()).toLocalDate() == state.dateFilter
                val slotOk = state.slotFilter == null || e.entry.slot == state.slotFilter
                val searchOk = state.searchQuery.isBlank() ||
                    e.entry.note?.contains(state.searchQuery, ignoreCase = true) == true
                val tagOk = state.tagFilter.isEmpty() ||
                    e.tags.any { it.id in state.tagFilter }
                dateOk && slotOk && searchOk && tagOk
            }
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            FilterBar(
                searchQuery = state.searchQuery,
                onSearchChange = { viewModel.setSearchQuery(it) },
                selectedSlot = state.slotFilter,
                onSlotSelect = { viewModel.setSlotFilter(it) },
                selectedDate = state.dateFilter,
                onDateSelect = { viewModel.setDateFilter(it) },
                tags = state.tags,
                selectedTagIds = state.tagFilter,
                onTagToggle = { viewModel.toggleTagFilter(it) },
                onClearAll = { viewModel.clearAllFilters() },
            )

            if (filteredEntries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val message = if (state.entries.isEmpty()) {
                        "No entries yet."
                    } else {
                        "No entries match the current filters."
                    }
                    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Scaffold
            }

            val grouped = filteredEntries.groupBy {
                it.entry.recordedAt.atZone(ZoneId.systemDefault()).toLocalDate()
            }.toSortedMap(compareByDescending { it })

            val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")
            val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                grouped.forEach { (day, items) ->
                    item(key = "day-$day") {
                        DayHeader(
                            day = day,
                            fmt = dayFmt,
                            onClick = { viewModel.setDateFilter(day) },
                        )
                    }
                    items(items, key = { it.entry.id }) { e ->
                        EntryCard(
                            entry = e,
                            scales = state.scalesById,
                            windows = state.windowsById,
                            timeFmt = timeFmt,
                            onLongPress = { sheetTarget = e },
                        )
                    }
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
private fun DayHeader(
    day: LocalDate,
    fmt: DateTimeFormatter,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text = fmt.format(day),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (onClick != null) {
            Modifier.clickable(onClick = onClick).padding(top = 4.dp)
        } else {
            Modifier.padding(top = 4.dp)
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: EntryWithValues,
    scales: Map<Long, Scale>,
    windows: Map<Long, PromptWindow>,
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
                SlotChip(slotLabel(entry.entry.slot, entry.entry.promptWindowId, windows))
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
            if (entry.tags.isNotEmpty()) {
                Text(
                    entry.tags.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SlotChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label) })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedSlot: PromptSlot?,
    onSlotSelect: (PromptSlot?) -> Unit,
    selectedDate: LocalDate?,
    onDateSelect: (LocalDate?) -> Unit,
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    onTagToggle: (Long) -> Unit,
    onClearAll: () -> Unit,
) {
    val dateFmt = DateTimeFormatter.ofPattern("MMM d")
    var showDatePicker by remember { mutableStateOf(false) }
    val hasActiveFilter = selectedDate != null || selectedSlot != null ||
        searchQuery.isNotBlank() || selectedTagIds.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search notes...") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
            )
            if (hasActiveFilter) {
                TextButton(onClick = onClearAll) {
                    Text("Clear", maxLines = 1)
                }
            }
        }

        val scrollState = rememberScrollState()
        val surface = MaterialTheme.colorScheme.surface

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isSelected = selectedSlot == null
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onSlotSelect(null)
                        onDateSelect(null)
                    },
                    label = { Text("All") },
                )
                val isDateSelected = selectedDate != null
                FilterChip(
                    selected = isDateSelected,
                    onClick = { showDatePicker = true },
                    label = {
                        Text(
                            if (selectedDate != null) dateFmt.format(selectedDate)
                            else "Pick date"
                        )
                    },
                )
                PromptSlot.entries.forEach { slot ->
                    val isSelected = selectedSlot == slot
                    val label = slot.name.lowercase().replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            onSlotSelect(if (isSelected) null else slot)
                        },
                        label = { Text(label) },
                    )
                }
            }
            if (scrollState.canScrollBackward) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to surface,
                                    0.08f to Color.Transparent,
                                    1f to Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
            if (scrollState.canScrollForward) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.92f to Color.Transparent,
                                    1f to surface,
                                ),
                            ),
                        ),
                )
            }
        }

        if (tags.isNotEmpty()) {
            val tagScrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.horizontalScroll(tagScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        FilterChip(
                            selected = tag.id in selectedTagIds,
                            onClick = { onTagToggle(tag.id) },
                            label = { Text(tag.name) },
                        )
                    }
                }
                if (tagScrollState.canScrollBackward) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to surface,
                                        0.08f to Color.Transparent,
                                        1f to Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }
                if (tagScrollState.canScrollForward) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to Color.Transparent,
                                        0.92f to Color.Transparent,
                                        1f to surface,
                                    ),
                                ),
                            ),
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                ?.atStartOfDay(ZoneOffset.UTC)
                ?.toInstant()
                ?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onDateSelect(picked)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDateSelect(null)
                    showDatePicker = false
                }) { Text("Clear") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Prefer the configured window's label so renamed or extra windows read
 * correctly. Falls back to the slot enum name for Manual/Custom entries and for
 * entries whose window was since deleted.
 */
private fun slotLabel(
    slot: PromptSlot,
    promptWindowId: Long?,
    windows: Map<Long, PromptWindow>,
): String =
    promptWindowId?.let { windows[it]?.label }
        ?: slot.name.lowercase().replaceFirstChar { it.uppercase() }

private fun formatHistoryValue(v: Float): String {
    val rounded = kotlin.math.round(v)
    return if (kotlin.math.abs(v - rounded) < 1e-3f) rounded.toInt().toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.')
}
