package com.lcdcode.moodcairns.ui.charts

import androidx.annotation.StringRes
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.ui.common.SkeletonDateFormat
import com.lcdcode.moodcairns.ui.common.SlotChip
import com.lcdcode.moodcairns.ui.common.asString
import com.lcdcode.moodcairns.ui.common.currentLocale
import com.lcdcode.moodcairns.ui.common.displayLabel
import com.lcdcode.moodcairns.ui.common.displayName
import com.lcdcode.moodcairns.ui.common.displayNameRes
import com.lcdcode.moodcairns.ui.common.formatScaleValue
import com.lcdcode.moodcairns.ui.common.rangeLabel
import com.lcdcode.moodcairns.ui.common.rememberSkeletonDateFormat
import com.lcdcode.moodcairns.ui.common.slotLabel
import com.lcdcode.moodcairns.ui.tags.TagFilterPanel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.cartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.Zoom
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerVisibilityListener
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

/** Raw swaps daily means for individual entries once this many days or fewer are on screen. */
private const val ENTRY_DETAIL_MAX_VISIBLE_DAYS = 5f

/** Deepest zoom, as the x span left on screen: 6 hours on either axis. */
private const val MIN_VISIBLE_HOURS = 6
private const val MIN_VISIBLE_MINUTES = MIN_VISIBLE_HOURS * MINUTES_PER_HOUR

/** Entries the Hourly tapped-point card lists at first, and adds per "+N more" tap. */
private const val HOURLY_CARD_PAGE_SIZE = 10

// One major x step per hour on both axes. Kept as stable instances because
// rememberCartesianChart keys on them.
private val MINUTE_AXIS_X_STEP: (CartesianChartModel) -> Double = { MINUTES_PER_HOUR.toDouble() }
private val HOUR_AXIS_X_STEP: (CartesianChartModel) -> Double = { 1.0 }

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
                title = { Text(stringResource(R.string.charts_title)) },
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

            TagFilterPanel(
                categories = state.categories,
                tags = state.tags,
                selectedTagIds = state.selectedTagIds,
                onToggle = viewModel::toggleTagFilter,
                onClear = viewModel::clearTagFilter,
            )

            val summary = when (state.chartMode) {
                ChartMode.Raw -> pluralStringResource(
                    R.plurals.charts_summary_raw,
                    state.entryCount,
                    state.entryCount,
                )
                ChartMode.RollingAvg -> pluralStringResource(
                    R.plurals.charts_summary_rolling,
                    state.entryCount,
                    state.entryCount,
                    ROLLING_AVERAGE_DAYS,
                )
                ChartMode.Hourly -> pluralStringResource(
                    R.plurals.charts_summary_hourly,
                    state.entryCount,
                    state.entryCount,
                )
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            TextButton(
                onClick = { showHelp = true },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    stringResource(R.string.charts_help_button),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            val visibleSeries = state.series.filter { it.scale.id in state.selectedScaleIds }
            val hasData = visibleSeries.any {
                plotPoints(it, state.chartMode, showEntries = false).isNotEmpty()
            }
            if (!hasData) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT_BASE),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(
                            if (!state.loaded) R.string.common_loading else R.string.charts_no_data,
                        ),
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
                    scales = state.scales,
                    windows = state.windows,
                    entriesByDay = state.entriesByDay,
                    entriesByHour = state.entriesByHour,
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
    val fmt = rememberSkeletonDateFormat("yMMMd")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionLabel(R.string.charts_date_range_label)
            Text(stringResource(R.string.common_date_range, fmt.format(start), fmt.format(end)))
        }
        OutlinedButton(onClick = onClick) { Text(stringResource(R.string.charts_change_range)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartModeRow(mode: ChartMode, onSelect: (ChartMode) -> Unit) {
    Column {
        SectionLabel(R.string.charts_series_label)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = ChartMode.values()
            options.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = m == mode,
                    onClick = { onSelect(m) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    // Three modes share the row: a long translation ellipsizes
                    // rather than wrapping into a tall, uneven button.
                    Text(chartModeLabel(m), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YAxisModeRow(absolute: Boolean, onToggle: (Boolean) -> Unit) {
    Column {
        SectionLabel(R.string.charts_y_axis_label)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(false, true)
            options.forEachIndexed { index, isAbs ->
                SegmentedButton(
                    selected = isAbs == absolute,
                    onClick = { onToggle(isAbs) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(yAxisModeLabel(isAbs))
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
        SectionLabel(R.string.charts_prompt_slots_label)
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
                        label = { Text(window.displayLabel()) },
                    )
                }
                FilterChip(
                    selected = SlotKey.Manual !in excluded,
                    onClick = { onToggle(SlotKey.Manual) },
                    label = { Text(stringResource(PromptSlot.MANUAL.displayNameRes())) },
                )
                FilterChip(
                    selected = SlotKey.Custom !in excluded,
                    onClick = { onToggle(SlotKey.Custom) },
                    label = { Text(stringResource(PromptSlot.CUSTOM.displayNameRes())) },
                )
                if (showOther) {
                    FilterChip(
                        selected = SlotKey.Other !in excluded,
                        onClick = { onToggle(SlotKey.Other) },
                        label = { Text(stringResource(R.string.charts_slot_other)) },
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
        SectionLabel(R.string.charts_scales_label)
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
                        label = { Text(scale.displayName()) },
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

@Composable
private fun ChartArea(
    series: List<ScaleSeries>,
    mode: ChartMode,
    totalDays: Int,
    startDate: LocalDate,
    absoluteY: Boolean,
    scales: List<Scale>,
    windows: List<PromptWindow>,
    entriesByDay: Map<Int, List<EntryWithValues>>,
    entriesByHour: Map<Int, List<EntryWithValues>>,
) {
    val isHourly = mode == ChartMode.Hourly
    val maxZoom = remember(isHourly) {
        val minVisibleX = if (isHourly) MIN_VISIBLE_HOURS else MIN_VISIBLE_MINUTES
        Zoom.max(Zoom.x(minVisibleX.toDouble()), Zoom.Content)
    }
    // Keyed on maxZoom, so switching between the date and hour axes resets zoom.
    val zoomState = rememberVicoZoomState(initialZoom = Zoom.Content, maxZoom = maxZoom)
    val showEntries by remember(mode, totalDays, zoomState) {
        derivedStateOf {
            val fraction = visibleFraction(zoomState.valueRange.start, zoomState.value)
            val visibleDays = totalDays * fraction
            mode == ChartMode.Raw && visibleDays <= ENTRY_DETAIL_MAX_VISIBLE_DAYS
        }
    }

    val nonEmpty = series.filter { plotPoints(it, mode, showEntries).isNotEmpty() }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(nonEmpty, mode, showEntries, absoluteY) {
        if (nonEmpty.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                nonEmpty.forEach { s ->
                    val pts = plotPoints(s, mode, showEntries)
                    series(
                        x = pts.map { it.x },
                        y = pts.map { p -> if (absoluteY) normalize(p.value, s.scale) else p.value },
                    )
                }
            }
        }
    }

    val lines = nonEmpty.map { s -> rememberLine(Color(s.scale.colorArgb)) }

    // Pin the x range to the selected window (or the whole 0-23h day) so points draw
    // at their true position rather than getting auto-scaled to span the data
    // extent. In absolute mode, also pin y to [0,1] since each series is
    // normalized against its own scale's min/max.
    val rangeProvider = remember(isHourly, totalDays, absoluteY) {
        val maxX = if (isHourly) {
            (HOURS_PER_DAY - 1).toDouble()
        } else {
            (totalDays * MINUTES_PER_DAY).toDouble()
        }
        if (absoluteY) {
            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX, minY = 0.0, maxY = 1.0)
        } else {
            CartesianLayerRangeProvider.fixed(minX = 0.0, maxX = maxX)
        }
    }

    // A tap remembers the mode it was made in, so switching modes never reads a
    // minute-axis x as an hour (or vice versa) before the card is cleared.
    var tapped by remember { mutableStateOf<Pair<ChartMode, Double>?>(null) }
    val currentMode by rememberUpdatedState(mode)

    val locale = currentLocale()
    val dayLabelFormat = rememberSkeletonDateFormat("MMMd")
    val timeLabelFormat = rememberSkeletonDateFormat("jm")
    val hourLabelFormat = rememberSkeletonDateFormat("j")
    val xLabelFormatter = remember(
        isHourly,
        startDate,
        dayLabelFormat,
        timeLabelFormat,
        hourLabelFormat,
    ) {
        if (isHourly) {
            CartesianValueFormatter { _, x, _ ->
                val hour = x.roundToInt().coerceIn(0, HOURS_PER_DAY - 1)
                hourLabelFormat.format(LocalTime.of(hour, 0))
            }
        } else {
            CartesianValueFormatter { _, x, _ ->
                val pos = minuteAxisPosition(x)
                if (pos.isMidnight) {
                    dayLabelFormat.format(startDate.plusDays(pos.dayIndex.coerceAtLeast(0)))
                } else {
                    timeLabelFormat.format(LocalTime.ofSecondOfDay(pos.minuteOfDay * 60L))
                }
            }
        }
    }
    val xLabelPlacer = remember(isHourly) {
        SteppedLabelPlacer(if (isHourly) HOUR_AXIS_LABEL_SPACINGS else MINUTE_AXIS_LABEL_SPACINGS)
    }

    val valueLabelFormatter = remember(absoluteY, locale) {
        CartesianValueFormatter { _, y, _ -> yAxisLabel(y, absoluteY, locale) }
    }

    // Vico's marker pipeline handles touch in chart-data coordinates, so the
    // reported `x` already accounts for the current zoom/scroll state. A no-op
    // marker (no visible decoration on the chart itself) is enough - the
    // tapped-point card below the chart is the actual UX surface.
    val invisibleMarker = remember { object : CartesianMarker {} }
    val markerListener = remember {
        object : CartesianMarkerVisibilityListener {
            override fun onShown(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                targets.firstOrNull()?.let { tapped = currentMode to it.x }
            }
            override fun onUpdated(marker: CartesianMarker, targets: List<CartesianMarker.Target>) {
                targets.firstOrNull()?.let { tapped = currentMode to it.x }
            }
            // Intentionally don't clear on hide: Vico hides the marker on touch
            // release, but the detail card should persist until the user taps a
            // different point or hits Close.
            override fun onHidden(marker: CartesianMarker) = Unit
        }
    }

    val plotHeight = chartHeight(nonEmpty.map { it.scale }, absoluteY)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(plotHeight)
                // Own render layer: when content above resizes (e.g. the tag panel
                // animating open), the chart is moved, not redrawn, every frame.
                .graphicsLayer(),
        ) {
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(lines),
                        rangeProvider = rangeProvider,
                    ),
                    startAxis = VerticalAxis.rememberStart(valueFormatter = valueLabelFormatter),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        valueFormatter = xLabelFormatter,
                        itemPlacer = xLabelPlacer,
                    ),
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
                    getXStep = if (isHourly) HOUR_AXIS_X_STEP else MINUTE_AXIS_X_STEP,
                ),
                modelProducer = modelProducer,
                modifier = Modifier.fillMaxSize(),
                zoomState = zoomState,
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            yAxisCaption(nonEmpty.map { it.scale }, absoluteY, locale).asString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        val scalesById = remember(scales) { scales.associateBy { it.id } }
        val windowsById = remember(windows) { windows.associateBy { it.id } }
        tapped?.takeIf { (tapMode, _) -> tapMode == mode }?.let { (_, x) ->
            if (isHourly) {
                val hour = x.roundToInt().coerceIn(0, HOURS_PER_DAY - 1)
                HourlyPointCard(
                    hour = hour,
                    entries = entriesByHour[hour].orEmpty(),
                    averages = nonEmpty.mapNotNull { s ->
                        s.hourly.firstOrNull { it.hour == hour }?.let { s.scale to it.value }
                    },
                    scales = scalesById,
                    windows = windowsById,
                    onDismiss = { tapped = null },
                )
            } else {
                val day = minuteAxisPosition(x).dayIndex.toInt()
                TappedPointCard(
                    date = startDate.plusDays(day.toLong()),
                    entries = entriesByDay[day].orEmpty(),
                    scales = scalesById,
                    windows = windowsById,
                    onDismiss = { tapped = null },
                )
            }
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
                        stringResource(
                            if (s.scale.inverted) {
                                R.string.charts_legend_lower_better
                            } else {
                                R.string.charts_legend
                            },
                            s.scale.displayName(),
                            rangeLabel(s.scale.minValue, s.scale.maxValue, locale).asString(),
                        ),
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
    entries: List<EntryWithValues>,
    scales: Map<Long, Scale>,
    windows: Map<Long, PromptWindow>,
    onDismiss: () -> Unit,
) {
    val dateFmt = rememberSkeletonDateFormat("yMMMEd")
    val timeFmt = rememberSkeletonDateFormat("jm")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    dateFmt.format(date),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.charts_no_entries_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) HorizontalDivider()
                    TappedEntry(entry, scales, windows, timeFmt)
                }
            }
        }
    }
}

@Composable
private fun HourlyPointCard(
    hour: Int,
    entries: List<EntryWithValues>,
    averages: List<Pair<Scale, Float>>,
    scales: Map<Long, Scale>,
    windows: Map<Long, PromptWindow>,
    onDismiss: () -> Unit,
) {
    val locale = currentLocale()
    val hourFmt = rememberSkeletonDateFormat("jm")
    val entryTimeFmt = rememberSkeletonDateFormat("yMMMdjm")
    val nextHour = (hour + 1) % HOURS_PER_DAY
    // Keyed on hour so tapping a different hour starts back at one page.
    var shownCount by remember(hour) { mutableIntStateOf(HOURLY_CARD_PAGE_SIZE) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        R.string.charts_hour_range,
                        hourFmt.format(LocalTime.of(hour, 0)),
                        hourFmt.format(LocalTime.of(nextHour, 0)),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
            Text(
                pluralStringResource(R.plurals.charts_hour_entry_count, entries.size, entries.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            averages.forEach { (scale, average) ->
                ScaleValueRow(
                    scale,
                    stringResource(
                        R.string.charts_hour_average,
                        scale.displayName(),
                        formatScaleValue(average, locale),
                    ),
                )
            }
            entries.asReversed().take(shownCount).forEach { entry ->
                HorizontalDivider()
                TappedEntry(entry, scales, windows, entryTimeFmt)
            }
            val hiddenCount = entries.size - shownCount
            if (hiddenCount > 0) {
                HorizontalDivider()
                TextButton(onClick = { shownCount += HOURLY_CARD_PAGE_SIZE }) {
                    Text(
                        pluralStringResource(
                            R.plurals.charts_hour_more_entries,
                            hiddenCount,
                            hiddenCount,
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScaleValueRow(scale: Scale, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = Color(scale.colorArgb),
            modifier = Modifier.size(10.dp),
        ) {}
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One entry in a tapped-point card; [timeFmt] decides whether the date is shown too. */
@Composable
private fun TappedEntry(
    entry: EntryWithValues,
    scales: Map<Long, Scale>,
    windows: Map<Long, PromptWindow>,
    timeFmt: SkeletonDateFormat,
) {
    val locale = currentLocale()
    val recordedAt = entry.entry.recordedAt.atZone(ZoneId.systemDefault()).toLocalDateTime()
    val listSeparator = stringResource(R.string.common_list_separator)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(timeFmt.format(recordedAt), fontWeight = FontWeight.Medium)
            SlotChip(slotLabel(entry.entry.slot, entry.entry.promptWindowId, windows))
        }
        entry.values.forEach { v ->
            val scale = scales[v.scaleId] ?: return@forEach
            ScaleValueRow(
                scale,
                stringResource(
                    R.string.charts_point_value,
                    scale.displayName(),
                    formatScaleValue(v.value, locale),
                ),
            )
        }
        entry.entry.note?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        if (entry.tags.isNotEmpty()) {
            Text(
                entry.tags.map { it.displayName() }.joinToString(listSeparator),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChartsHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.charts_help_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.charts_help_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val raw = chartModeLabel(ChartMode.Raw)
                val rolling = chartModeLabel(ChartMode.RollingAvg)
                val hourly = chartModeLabel(ChartMode.Hourly)
                HelpSection(stringResource(R.string.charts_help_series_title, raw, rolling, hourly))
                HelpEntry(raw, stringResource(R.string.charts_help_raw_body))
                HelpEntry(
                    rolling,
                    pluralStringResource(
                        R.plurals.charts_help_rolling_body,
                        ROLLING_AVERAGE_DAYS,
                        ROLLING_AVERAGE_DAYS,
                    ),
                )
                HelpEntry(hourly, stringResource(R.string.charts_help_hourly_body))

                val autoFit = yAxisModeLabel(absolute = false)
                val absolute = yAxisModeLabel(absolute = true)
                HelpSection(stringResource(R.string.charts_help_axis_title, autoFit, absolute))
                HelpEntry(autoFit, stringResource(R.string.charts_help_autofit_body))
                HelpEntry(absolute, stringResource(R.string.charts_help_absolute_body))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.charts_help_dismiss)) }
        },
    )
}

@Composable
private fun chartModeLabel(mode: ChartMode): String = when (mode) {
    ChartMode.Raw -> stringResource(R.string.charts_mode_raw)
    ChartMode.RollingAvg ->
        pluralStringResource(
            R.plurals.charts_mode_rolling,
            ROLLING_AVERAGE_DAYS,
            ROLLING_AVERAGE_DAYS,
        )
    ChartMode.Hourly -> stringResource(R.string.charts_mode_hourly)
}

@Composable
private fun yAxisModeLabel(absolute: Boolean): String =
    stringResource(if (absolute) R.string.charts_axis_absolute else R.string.charts_axis_autofit)

@Composable
private fun SectionLabel(@StringRes textRes: Int) {
    Text(stringResource(textRes), style = MaterialTheme.typography.labelMedium)
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
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    ) {
        DateRangePicker(
            state = state,
            title = {
                Text(
                    stringResource(R.string.charts_select_range),
                    modifier = Modifier.padding(16.dp),
                )
            },
        )
    }
}
