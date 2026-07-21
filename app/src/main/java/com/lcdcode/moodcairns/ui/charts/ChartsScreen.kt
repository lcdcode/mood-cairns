package com.lcdcode.moodcairns.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    onBack: () -> Unit,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Charts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RangeRow(
                start = state.startDate,
                end = state.endDate,
                onClick = { showRangePicker = true },
            )

            ChartModeRow(
                mode = state.chartMode,
                onSelect = viewModel::setChartMode,
            )

            YAxisModeRow(
                absolute = state.absoluteYAxis,
                onToggle = viewModel::setAbsoluteYAxis,
            )

            SlotFilterRow(
                windows = state.windows,
                excluded = state.excludedSlots,
                showOther = state.showOther,
                onToggle = viewModel::toggleSlot,
            )

            ScaleToggleRow(
                scales = state.scales,
                selected = state.selectedScaleIds,
                onToggle = viewModel::toggleScale,
            )

            TagFilterRow(
                tags = state.tags,
                selected = state.selectedTagIds,
                onToggle = viewModel::toggleTagFilter,
                onClear = viewModel::clearTagFilter,
            )

            val modeLabel = when (state.chartMode) {
                ChartMode.Raw -> "raw daily values"
                ChartMode.RollingAvg -> "7-day rolling average"
            }
            Text(
                "${state.entryCount} entries · $modeLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                onClick = { showHelp = true },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Help with charts", color = MaterialTheme.colorScheme.primary)
            }

            val visibleSeries = state.series.filter { it.scale.id in state.selectedScaleIds }
            val hasData = visibleSeries.any {
                when (state.chartMode) {
                    ChartMode.Raw -> it.daily.isNotEmpty()
                    ChartMode.RollingAvg -> it.rolling.isNotEmpty()
                }
            }
            if (!hasData) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (!state.loaded) "Loading…" else "No data in this range.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ChartArea(
                    series = visibleSeries,
                    mode = state.chartMode,
                    totalDays = state.days,
                    startDate = state.startDate,
                    absoluteY = state.absoluteYAxis,
                )
            }
        }

        if (showHelp) {
            ChartsHelpDialog(onDismiss = { showHelp = false })
        }

        if (showRangePicker) {
            DateRangeDialog(
                initialStart = state.startDate,
                initialEnd = state.endDate,
                minDate = state.earliestDate,
                onDismiss = { showRangePicker = false },
                onConfirm = { s, e ->
                    viewModel.setRange(s, e)
                    showRangePicker = false
                },
            )
        }
    }
}

@Composable
private fun RangeRow(start: LocalDate, end: LocalDate, onClick: () -> Unit) {
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Date range", style = MaterialTheme.typography.labelMedium)
            Text("${start.format(fmt)} – ${end.format(fmt)}")
        }
        OutlinedButton(onClick = onClick) { Text("Change") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartModeRow(mode: ChartMode, onSelect: (ChartMode) -> Unit) {
    Column {
        Text("Series", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = ChartMode.values()
            options.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = m == mode,
                    onClick = { onSelect(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(when (m) {
                        ChartMode.Raw -> "Raw"
                        ChartMode.RollingAvg -> "7-day avg"
                    })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YAxisModeRow(absolute: Boolean, onToggle: (Boolean) -> Unit) {
    Column {
        Text("Y axis", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(false, true)
            options.forEachIndexed { index, isAbs ->
                SegmentedButton(
                    selected = isAbs == absolute,
                    onClick = { onToggle(isAbs) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(if (isAbs) "Absolute" else "Auto-fit")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotFilterRow(
    windows: List<PromptWindow>,
    excluded: Set<SlotKey>,
    showOther: Boolean,
    onToggle: (SlotKey) -> Unit,
) {
    val scrollState = rememberScrollState()
    val surface = MaterialTheme.colorScheme.surface
    Column {
        Text("Prompt slots", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                windows.forEach { window ->
                    val key = SlotKey.Window(window.id)
                    FilterChip(
                        selected = key !in excluded,
                        onClick = { onToggle(key) },
                        label = { Text(window.label) },
                    )
                }
                FilterChip(
                    selected = SlotKey.Manual !in excluded,
                    onClick = { onToggle(SlotKey.Manual) },
                    label = { Text("Manual") },
                )
                FilterChip(
                    selected = SlotKey.Custom !in excluded,
                    onClick = { onToggle(SlotKey.Custom) },
                    label = { Text("Custom") },
                )
                if (showOther) {
                    FilterChip(
                        selected = SlotKey.Other !in excluded,
                        onClick = { onToggle(SlotKey.Other) },
                        label = { Text("Other") },
                    )
                }
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
private fun ScaleToggleRow(
    scales: List<com.lcdcode.moodcairns.data.entity.Scale>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    if (scales.isEmpty()) return
    val scrollState = rememberScrollState()
    val surface = MaterialTheme.colorScheme.surface
    Column {
        Text("Scales", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                scales.filter { !it.archived }.forEach { scale ->
                    FilterChip(
                        selected = scale.id in selected,
                        onClick = { onToggle(scale.id) },
                        label = { Text(scale.name) },
                        leadingIcon = {
                            Surface(
                                shape = CircleShape,
                                color = Color(scale.colorArgb),
                                modifier = Modifier.size(12.dp),
                            ) {}
                        },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
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
private fun TagFilterRow(
    tags: List<com.lcdcode.moodcairns.data.entity.Tag>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
) {
    if (tags.isEmpty()) return
    val scrollState = rememberScrollState()
    val surface = MaterialTheme.colorScheme.surface
    Column {
        Text("Tags", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val noTagsSelected = selected.isEmpty()
                FilterChip(
                    selected = noTagsSelected,
                    onClick = onClear,
                    label = { Text(if (noTagsSelected) "All" else "Clear") },
                )
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in selected,
                        onClick = { onToggle(tag.id) },
                        label = { Text(tag.name) },
                    )
                }
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

@Composable
private fun ChartArea(
    series: List<ScaleSeries>,
    mode: ChartMode,
    totalDays: Int,
    startDate: LocalDate,
    absoluteY: Boolean,
) {
    val pointsForMode: (ScaleSeries) -> List<DayPoint> = { s ->
        when (mode) {
            ChartMode.Raw -> s.daily
            ChartMode.RollingAvg -> s.rolling
        }
    }
    val nonEmpty = series.filter { pointsForMode(it).isNotEmpty() }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(nonEmpty, mode, totalDays, absoluteY) {
        if (nonEmpty.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                nonEmpty.forEach { s ->
                    val pts = pointsForMode(s)
                    series(
                        x = pts.map { it.dayIndex },
                        y = pts.map { p -> if (absoluteY) normalize(p.value, s.scale) else p.value },
                    )
                }
            }
        }
    }

    val lines = nonEmpty.map { s -> rememberLine(Color(s.scale.colorArgb)) }

    // Pin the x range to the selected window so points draw at their true day index
    // rather than getting auto-scaled to span the data extent — keeps the chart aligned
    // with the start/mid/end date labels below it. In absolute mode, also pin y to
    // [0,1] since each series is normalized against its own scale's min/max.
    val rangeProvider = remember(totalDays, absoluteY) {
        if (absoluteY) {
            CartesianLayerRangeProvider.fixed(
                minX = 0.0,
                maxX = (totalDays - 1).coerceAtLeast(0).toDouble(),
                minY = 0.0,
                maxY = 1.0,
            )
        } else {
            CartesianLayerRangeProvider.fixed(
                minX = 0.0,
                maxX = (totalDays - 1).coerceAtLeast(0).toDouble(),
            )
        }
    }

    var tappedDay by remember { mutableStateOf<Int?>(null) }

    val dateLabelFormatter = remember(startDate) {
        val fmt = DateTimeFormatter.ofPattern("d MMM")
        CartesianValueFormatter { _, x, _ ->
            startDate.plusDays(x.toLong().coerceAtLeast(0)).format(fmt)
        }
    }

    // Vico's marker pipeline handles touch in chart-data coordinates, so the
    // reported `x` already accounts for the current zoom/scroll state. A no-op
    // marker (no visible decoration on the chart itself) is enough — the
    // TappedPointCard below the chart is the actual UX surface.
    val invisibleMarker = remember { object : CartesianMarker {} }
    val markerListener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                tappedDay = targets.firstOrNull()?.x?.roundToInt()
            }
            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                tappedDay = targets.firstOrNull()?.x?.roundToInt()
            }
            // Intentionally don't clear on hide: Vico hides the marker on touch
            // release, but the detail card should persist until the user taps a
            // different point or hits Close.
            override fun onHidden(marker: CartesianMarker) = Unit
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        ) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(lines),
                        rangeProvider = rangeProvider,
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateLabelFormatter),
                    marker = invisibleMarker,
                    markerVisibilityListener = markerListener,
                    // Inset the data area slightly on both sides so the points
                    // at minX (start date) and maxX (end date) aren't drawn
                    // centered on the chart's clip edge, which would cut their
                    // markers in half.
                    layerPadding = cartesianLayerPadding(
                        unscalableStart = 8.dp,
                        unscalableEnd = 8.dp,
                    ),
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxSize(),
                zoomState = rememberVicoZoomState(initialZoom = Zoom.Content),
            )
        }

        Spacer(Modifier.height(8.dp))

        tappedDay?.let { day ->
            TappedPointCard(
                date = startDate.plusDays(day.toLong()),
                series = series,
                dayIndex = day,
                mode = mode,
                onDismiss = { tappedDay = null },
            )
            Spacer(Modifier.height(8.dp))
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            nonEmpty.forEach { s ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color(s.scale.colorArgb),
                        modifier = Modifier.size(12.dp),
                    ) {}
                    Text(
                        "${s.scale.name}  (${s.scale.minValue}–${s.scale.maxValue})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TappedPointCard(
    date: LocalDate,
    series: List<ScaleSeries>,
    dayIndex: Int,
    mode: ChartMode,
    onDismiss: () -> Unit,
) {
    val fmt = remember { DateTimeFormatter.ofPattern("EEE, d MMM yyyy") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    date.format(fmt),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            val rows = series.mapNotNull { s ->
                val pts = when (mode) {
                    ChartMode.Raw -> s.daily
                    ChartMode.RollingAvg -> s.rolling
                }
                pts.firstOrNull { it.dayIndex == dayIndex }?.let { s to it.value }
            }
            if (rows.isEmpty()) {
                Text(
                    "No entries on this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rows.forEach { (s, v) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(s.scale.colorArgb),
                            modifier = Modifier.size(10.dp),
                        ) {}
                        Text(
                            "${s.scale.name}: ${formatValue(v)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartsHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reading the charts") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Two toggles change how your numbers are drawn. Nothing you pick here " +
                        "changes your data - only how the chart looks.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HelpSection("Series: Raw vs 7-day avg")
                HelpEntry(
                    "Raw",
                    "Plots each day exactly as you logged it. Use it to see precise " +
                        "day-to-day changes - though it can look jumpy.",
                )
                HelpEntry(
                    "7-day avg",
                    "Replaces each day with the average of it and the surrounding week. " +
                        "This smooths out one-off spikes so the overall trend - whether " +
                        "your moods are drifting up or down - is easier to see.",
                )

                HelpSection("Y axis: Auto-fit vs Absolute")
                HelpEntry(
                    "Auto-fit",
                    "Zooms the vertical axis to just the range your data actually covers. " +
                        "Small movements become easy to see because the chart fills the " +
                        "space - but the line isn't measured against the scale's full range.",
                )
                HelpEntry(
                    "Absolute",
                    "Shows each line against its scale's full min-to-max range. Movements " +
                        "look smaller, but different scales line up fairly, so you can " +
                        "honestly compare one against another on the same chart.",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        },
    )
}

@Composable
private fun HelpSection(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun HelpEntry(term: String, explanation: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            term,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(explanation, style = MaterialTheme.typography.bodySmall)
    }
}

private fun normalize(value: Float, scale: com.lcdcode.moodcairns.data.entity.Scale): Float {
    val span = (scale.maxValue - scale.minValue).toFloat()
    if (span <= 0f) return 0.5f
    return ((value - scale.minValue) / span).coerceIn(0f, 1f)
}

private fun formatValue(v: Float): String {
    val rounded = v.roundToInt()
    return if (kotlin.math.abs(v - rounded) < 0.05f) rounded.toString()
    else "%.1f".format(v)
}

@Composable
private fun rememberLine(color: Color): LineCartesianLayer.Line {
    val fill = LineCartesianLayer.LineFill.single(fill(color))
    return remember(color) { LineCartesianLayer.Line(fill = fill) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    minDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val minUtcMillis = minDate?.toEpochDay()?.times(86_400_000L)
    val minYear = minDate?.year ?: 1970
    val nowYear = LocalDate.now().year
    val selectable = remember(minUtcMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                minUtcMillis == null || utcTimeMillis >= minUtcMillis
            override fun isSelectableYear(year: Int): Boolean =
                year in minYear..nowYear
        }
    }
    val clampedStart = if (minDate != null && initialStart < minDate) minDate else initialStart
    val clampedEnd = if (minDate != null && initialEnd < minDate) minDate else initialEnd
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = clampedStart.toEpochDay() * 86_400_000L,
        initialSelectedEndDateMillis = clampedEnd.toEpochDay() * 86_400_000L,
        yearRange = minYear..nowYear,
        selectableDates = selectable,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@TextButton
                    val e = state.selectedEndDateMillis ?: return@TextButton
                    val ld1 = LocalDate.ofEpochDay(s / 86_400_000L)
                    val ld2 = LocalDate.ofEpochDay(e / 86_400_000L)
                    onConfirm(ld1, ld2)
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Select range", modifier = Modifier.padding(16.dp)) },
        )
    }
}
