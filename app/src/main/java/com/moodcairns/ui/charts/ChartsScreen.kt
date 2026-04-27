package com.moodcairns.ui.charts

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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodcairns.data.entity.PromptSlot
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    onBack: () -> Unit,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showRangePicker by remember { mutableStateOf(false) }

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

            SlotFilterRow(
                selected = state.slotFilter,
                onToggle = viewModel::toggleSlot,
            )

            ScaleToggleRow(
                scales = state.scales,
                selected = state.selectedScaleIds,
                onToggle = viewModel::toggleScale,
            )

            Text(
                "${state.entryCount} entries · 7-day rolling average",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val visibleSeries = state.series.filter { it.scale.id in state.selectedScaleIds }
            if (visibleSeries.isEmpty() || visibleSeries.all { it.rolling.isEmpty() }) {
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
                RollingAverageChart(
                    series = visibleSeries,
                    totalDays = state.days,
                    startDate = state.startDate,
                )
            }
        }

        if (showRangePicker) {
            DateRangeDialog(
                initialStart = state.startDate,
                initialEnd = state.endDate,
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
private fun SlotFilterRow(selected: Set<PromptSlot>, onToggle: (PromptSlot) -> Unit) {
    Column {
        Text("Prompt slots", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PromptSlot.values().forEach { slot ->
                FilterChip(
                    selected = slot in selected,
                    onClick = { onToggle(slot) },
                    label = { Text(slot.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaleToggleRow(
    scales: List<com.moodcairns.data.entity.Scale>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    if (scales.isEmpty()) return
    Column {
        Text("Scales", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
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
    }
}

@Composable
private fun RollingAverageChart(
    series: List<ScaleSeries>,
    totalDays: Int,
    startDate: LocalDate,
) {
    val nonEmpty = series.filter { it.rolling.isNotEmpty() }
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(nonEmpty, totalDays) {
        if (nonEmpty.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineSeries {
                nonEmpty.forEach { s ->
                    series(
                        x = s.rolling.map { it.dayIndex },
                        y = s.rolling.map { it.value },
                    )
                }
            }
        }
    }

    val lines = nonEmpty.map { s ->
        val color = Color(s.scale.colorArgb)
        rememberLine(color)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(lines),
                ),
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        )

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            nonEmpty.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun rememberLine(color: Color): LineCartesianLayer.Line {
    val fill = LineCartesianLayer.LineFill.single(fill(color))
    return remember(color) { LineCartesianLayer.Line(fill = fill) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDialog(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStart.atStartOfDay(zone).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = initialEnd.atStartOfDay(zone).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                onClick = {
                    val s = state.selectedStartDateMillis ?: return@TextButton
                    val e = state.selectedEndDateMillis ?: return@TextButton
                    val ld1 = Instant.ofEpochMilli(s).atZone(zone).toLocalDate()
                    val ld2 = Instant.ofEpochMilli(e).atZone(zone).toLocalDate()
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
