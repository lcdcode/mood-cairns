package com.lcdcode.moodcairns.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.ui.common.formatScaleValue
import com.lcdcode.moodcairns.ui.common.formatValueWithRange
import com.lcdcode.moodcairns.ui.tags.displayName
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.savedId) {
        if (state.savedId != null) onSaved()
    }

    LaunchedEffect(state.error) {
        val err = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.editingId != null) "Edit entry" else "How are you?") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Save lives in a fixed bottom bar so it stays reachable however
            // long the form gets; imePadding keeps it above the keyboard.
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving && state.scales.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(if (state.saving) "Saving…" else "Save")
                }
            }
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
                    value = state.values[scale.id] ?: ((scale.minValue + scale.maxValue) / 2f),
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

            Spacer(Modifier.height(8.dp))

            if (state.tags.isNotEmpty()) {
                TagPicker(
                    tags = state.tags,
                    selectedTagIds = state.selectedTagIds,
                    onToggle = viewModel::toggleTag,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (state.showDateTimeControl) {
                DateTimeControl(
                    recordedAt = state.recordedAt,
                    onChange = viewModel::setRecordedAt,
                )
                Spacer(Modifier.height(8.dp))
            }

            PromptSlotRow(
                windows = state.windows,
                extraWindow = state.extraWindow,
                selectedSlot = state.slot,
                selectedWindowId = state.promptWindowId,
                onWindow = viewModel::selectWindow,
                onManual = viewModel::selectManual,
                onCustom = viewModel::selectCustom,
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagPicker(
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    // observeAll() sorts category alphabetically; iterate TagCategory.entries
    // (Place, Person, Activity) instead so the display order is fixed here.
    val byCategory = remember(tags) { tags.groupBy { it.category } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TagCategory.entries.forEach { category ->
            val categoryTags = byCategory[category] ?: return@forEach
            Text(
                category.displayName,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categoryTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selectedTagIds,
                        onClick = { onToggle(tag.id) },
                        label = { Text(tag.name) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptSlotRow(
    windows: List<PromptWindow>,
    extraWindow: PromptWindow?,
    selectedSlot: PromptSlot,
    selectedWindowId: Long?,
    onWindow: (PromptWindow) -> Unit,
    onManual: () -> Unit,
    onCustom: () -> Unit,
) {
    // Append the pinned disabled window (if any) so an edited entry's slot still
    // renders even though it is no longer offered for new entries.
    val options = remember(windows, extraWindow) {
        if (extraWindow != null && windows.none { it.id == extraWindow.id }) windows + extraWindow
        else windows
    }
    val scrollState = rememberScrollState()
    val surface = androidx.compose.material3.MaterialTheme.colorScheme.surface
    Column {
        Text(
            "Prompt slot",
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { window ->
                    FilterChip(
                        selected = selectedWindowId == window.id,
                        onClick = { onWindow(window) },
                        label = { Text(window.label) },
                    )
                }
                FilterChip(
                    selected = selectedWindowId == null && selectedSlot == PromptSlot.MANUAL,
                    onClick = onManual,
                    label = { Text("Manual") },
                )
                FilterChip(
                    selected = selectedWindowId == null && selectedSlot == PromptSlot.CUSTOM,
                    onClick = onCustom,
                    label = { Text("Custom") },
                )
            }
            // Edge fades signal that more chips exist off-screen. These overlays
            // are purely decorative and do not intercept pointer events.
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
    }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeControl(
    recordedAt: Instant,
    onChange: (Instant) -> Unit,
) {
    var userPicked by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }

    val zoned = recordedAt.atZone(ZoneId.systemDefault())
    val label = if (userPicked) dateTimeFmt.format(zoned) else "Change date/time..."

    AssistChip(
        onClick = { showDatePicker = true },
        label = { Text(label) },
    )

    if (showDatePicker) {
        // Entries record something that already happened, so block dates after
        // today. utcTimeMillis is the picker's UTC-midnight for each candidate day.
        val todayUtcMillis = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val noFutureDates = remember(todayUtcMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtcMillis
                override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = zoned.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
            selectableDates = noFutureDates,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        pendingDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        showDatePicker = false
                        showTimePicker = true
                    }
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val time = zoned.toLocalTime()
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = pendingDate ?: zoned.toLocalDate()
                    val picked = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    onChange(combineDateTime(date, picked, ZoneId.systemDefault()))
                    userPicked = true
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}

internal fun combineDateTime(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
    date.atTime(time).atZone(zone).toInstant()

@Composable
private fun ScaleSlider(
    scale: Scale,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val accent = Color(scale.colorArgb)
    val display = formatScaleValue(value)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(scale.name, fontWeight = FontWeight.Medium)
            Text(formatValueWithRange(value, scale))
        }
        // An inverse scale runs high-to-low, so "better" is the same gesture
        // direction as on normal scales. Flipping the layout direction (rather
        // than mapping values) keeps range, steps, and snapping untouched.
        val baseDirection = LocalLayoutDirection.current
        val sliderDirection = if (!scale.inverted) baseDirection else when (baseDirection) {
            LayoutDirection.Ltr -> LayoutDirection.Rtl
            LayoutDirection.Rtl -> LayoutDirection.Ltr
        }
        val spokenRange = if (scale.minValue < 0) {
            "in range ${scale.minValue} to ${scale.maxValue}"
        } else {
            "out of ${scale.maxValue}"
        }
        CompositionLocalProvider(LocalLayoutDirection provides sliderDirection) {
            Slider(
                value = value,
                onValueChange = { onValueChange(snapToStep(it, scale)) },
                valueRange = scale.minValue.toFloat()..scale.maxValue.toFloat(),
                steps = sliderSteps(scale),
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                ),
                modifier = Modifier.semantics {
                    contentDescription = "${scale.name}, value $display $spokenRange" +
                        if (scale.inverted) ", lower is better" else ""
                },
            )
        }
    }
}

/** Intermediate tick count; rounds so inexact float steps (e.g. 0.3) don't drop a tick. */
internal fun sliderSteps(scale: Scale): Int {
    if (scale.step <= 0f) return 0
    val intervals = kotlin.math.round((scale.maxValue - scale.minValue) / scale.step).toInt()
    return (intervals - 1).coerceAtLeast(0)
}

internal fun snapToStep(raw: Float, scale: Scale): Float {
    if (scale.step <= 0f) return raw
    val offset = raw - scale.minValue
    val snapped = scale.minValue + kotlin.math.round(offset / scale.step) * scale.step
    return snapped.coerceIn(scale.minValue.toFloat(), scale.maxValue.toFloat())
}
