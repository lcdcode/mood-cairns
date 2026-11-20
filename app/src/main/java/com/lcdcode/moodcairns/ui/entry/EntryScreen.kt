package com.lcdcode.moodcairns.ui.entry

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.ui.common.currentLocale
import com.lcdcode.moodcairns.ui.common.displayLabel
import com.lcdcode.moodcairns.ui.common.displayName
import com.lcdcode.moodcairns.ui.common.displayNameRes
import com.lcdcode.moodcairns.ui.common.formatScaleValue
import com.lcdcode.moodcairns.ui.common.formatValueWithRange
import com.lcdcode.moodcairns.ui.common.rememberSkeletonDateFormat
import com.lcdcode.moodcairns.ui.tags.TagChipGroups
import com.lcdcode.moodcairns.ui.common.asString
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onSavedOpenJournal: (LocalDate) -> Unit,
    viewModel: EntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val journalEnabled by viewModel.journalEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.savedId) {
        if (state.savedId == null) return@LaunchedEffect
        if (state.openJournalAfterSave) {
            onSavedOpenJournal(journalDayOf(state.recordedAt, ZoneId.systemDefault()))
        } else {
            onSaved()
        }
    }

    val errorText = state.error?.asString()
    LaunchedEffect(errorText) {
        val err = errorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(err)
        viewModel.dismissError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titleRes = if (state.editingId != null) {
                        R.string.entry_title_edit
                    } else {
                        R.string.entry_title_new
                    }
                    Text(stringResource(titleRes))
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Save lives in a fixed bottom bar so it stays reachable however
            // long the form gets; imePadding keeps it above the keyboard.
            Surface(tonalElevation = 3.dp) {
                SaveButtons(
                    state = state,
                    journalEnabled = journalEnabled,
                    onSave = { viewModel.save() },
                    onSaveAndOpenJournal = { viewModel.save(openJournal = true) },
                )
            }
        },
    ) { padding ->
        if (state.scales.isEmpty() && !state.saving) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.entry_no_scales),
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
            val locale = currentLocale()
            state.askedScales.forEach { scale ->
                ScaleSlider(
                    scale = scale,
                    value = state.values[scale.id] ?: state.startValue(scale),
                    onValueChange = { viewModel.setValue(scale.id, it) },
                    hint = if (state.isReasked(scale)) {
                        stringResource(
                            R.string.entry_once_per_day_reask_hint,
                            formatScaleValue(scale.minValue.toFloat(), locale),
                        )
                    } else {
                        null
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.entry_note_label)) },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            Spacer(Modifier.height(8.dp))

            if (state.tags.isNotEmpty()) {
                TagChipGroups(
                    categories = state.categories,
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
            stringResource(R.string.entry_prompt_slot_label),
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
                        label = { Text(window.displayLabel()) },
                    )
                }
                FilterChip(
                    selected = selectedWindowId == null && selectedSlot == PromptSlot.MANUAL,
                    onClick = onManual,
                    label = { Text(stringResource(PromptSlot.MANUAL.displayNameRes())) },
                )
                FilterChip(
                    selected = selectedWindowId == null && selectedSlot == PromptSlot.CUSTOM,
                    onClick = onCustom,
                    label = { Text(stringResource(PromptSlot.CUSTOM.displayNameRes())) },
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
    val dateTimeFmt = rememberSkeletonDateFormat("MMMdjm")
    val label = if (userPicked) {
        dateTimeFmt.format(zoned.toLocalDateTime())
    } else {
        stringResource(R.string.entry_change_date_time)
    }

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
                }) { Text(stringResource(R.string.common_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
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
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
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
    hint: String? = null,
) {
    val accent = Color(scale.colorArgb)
    val locale = currentLocale()
    val display = formatScaleValue(value, locale)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(scale.displayName(), fontWeight = FontWeight.Medium)
            Text(formatValueWithRange(value, scale, locale).asString())
        }
        // An inverse scale runs high-to-low, so "better" is the same gesture
        // direction as on normal scales. Flipping the layout direction (rather
        // than mapping values) keeps range, steps, and snapping untouched.
        val baseDirection = LocalLayoutDirection.current
        val sliderDirection = if (!scale.inverted) baseDirection else when (baseDirection) {
            LayoutDirection.Ltr -> LayoutDirection.Rtl
            LayoutDirection.Rtl -> LayoutDirection.Ltr
        }
        val scaleName = scale.displayName()
        val baseDescription = if (scale.minValue < 0) {
            stringResource(
                R.string.entry_slider_description_range,
                scaleName,
                display,
                scale.minValue,
                scale.maxValue,
            )
        } else {
            stringResource(
                R.string.entry_slider_description_out_of,
                scaleName,
                display,
                scale.maxValue,
            )
        }
        val sliderDescription = if (scale.inverted) {
            stringResource(R.string.entry_slider_description_lower_better, baseDescription)
        } else {
            baseDescription
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
                    contentDescription = sliderDescription
                },
            )
        }
        hint?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/**
 * Slider start value for a fresh entry: the scale's configured default, or the
 * range midpoint when it has none. Snapping keeps the start on a slider tick,
 * which the raw midpoint is not for odd ranges (1..10 step 1 starts at 5, not
 * 5.5), and clamps a default left out of range by a later range edit.
 */
internal fun Scale.initialSliderValue(): Float =
    snapToStep(defaultValue ?: ((minValue + maxValue) / 2f), this)

internal fun snapToStep(raw: Float, scale: Scale): Float {
    if (scale.step <= 0f) return raw
    val offset = raw - scale.minValue
    val snapped = scale.minValue + kotlin.math.round(offset / scale.step) * scale.step
    return snapped.coerceIn(scale.minValue.toFloat(), scale.maxValue.toFloat())
}

/**
 * Save, plus "Save & open journal" when the journal is enabled. Side by side at equal width
 * and height, so a label that wraps in a longer language does not make the pair uneven. The
 * button that was tapped shows the saving state.
 */
@Composable
private fun SaveButtons(
    state: EntryUiState,
    journalEnabled: Boolean,
    onSave: () -> Unit,
    onSaveAndOpenJournal: () -> Unit,
) {
    val enabled = !state.saving && state.scales.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            val savingHere = state.saving && !state.openJournalAfterSave
            SaveLabel(if (savingHere) R.string.common_saving else R.string.common_save)
        }
        if (journalEnabled) {
            FilledTonalButton(
                onClick = onSaveAndOpenJournal,
                enabled = enabled,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                val savingHere = state.saving && state.openJournalAfterSave
                SaveLabel(
                    if (savingHere) R.string.common_saving else R.string.entry_save_open_journal,
                )
            }
        }
    }
}

@Composable
private fun SaveLabel(@StringRes label: Int) {
    Text(stringResource(label), textAlign = TextAlign.Center)
}
