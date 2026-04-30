package com.lcdcode.moodcairns.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.EntryRepository
import com.lcdcode.moodcairns.data.repo.ScaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class DayPoint(val dayIndex: Int, val value: Float)

data class ScaleSeries(
    val scale: Scale,
    val rolling: List<DayPoint>,
    val daily: List<DayPoint>,
)

enum class ChartMode { Raw, RollingAvg }

data class ChartsUiState(
    val startDate: LocalDate = LocalDate.now().minusDays(29),
    val endDate: LocalDate = LocalDate.now(),
    val slotFilter: Set<PromptSlot> = PromptSlot.values().toSet(),
    val selectedScaleIds: Set<Long> = emptySet(),
    val scales: List<Scale> = emptyList(),
    val series: List<ScaleSeries> = emptyList(),
    val entryCount: Int = 0,
    val chartMode: ChartMode = ChartMode.Raw,
    val loaded: Boolean = false,
    val earliestDate: LocalDate? = null,
) {
    val days: Int get() = (endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1
}

private data class Filters(
    val start: LocalDate,
    val end: LocalDate,
    val slots: Set<PromptSlot>,
    val selected: Set<Long>,
    val mode: ChartMode,
    val initialized: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val entries: EntryRepository,
    scales: ScaleRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(
        Filters(
            start = LocalDate.now().minusDays(29),
            end = LocalDate.now(),
            slots = PromptSlot.values().toSet(),
            selected = emptySet(),
            mode = ChartMode.Raw,
            initialized = false,
        ),
    )

    private val scalesFlow = scales.observeAll()
    private val earliestFlow = entries.observeEarliestRecordedAt()

    val state: StateFlow<ChartsUiState> = filters
        .flatMapLatest { f ->
            val zone = ZoneId.systemDefault()
            val from = f.start.atStartOfDay(zone).toInstant()
            val to = f.end.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1)
            combine(
                entries.observeRange(from, to),
                scalesFlow,
                earliestFlow,
            ) { rows, scaleList, earliest ->
                buildState(f, rows, scaleList, earliest)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    fun setRange(start: LocalDate, end: LocalDate) = filters.update {
        val (s, e) = if (start <= end) start to end else end to start
        it.copy(start = s, end = e)
    }

    fun toggleSlot(slot: PromptSlot) = filters.update {
        val next = if (slot in it.slots) it.slots - slot else it.slots + slot
        it.copy(slots = if (next.isEmpty()) it.slots else next)
    }

    fun toggleScale(id: Long) = filters.update {
        val next = if (id in it.selected) it.selected - id else it.selected + id
        it.copy(selected = next)
    }

    fun setChartMode(mode: ChartMode) = filters.update { it.copy(mode = mode) }

    private fun buildState(
        f: Filters,
        rows: List<EntryWithValues>,
        scaleList: List<Scale>,
        earliest: java.time.Instant?,
    ): ChartsUiState {
        val zone = ZoneId.systemDefault()
        val earliestLocal = earliest?.atZone(zone)?.toLocalDate()

        // On first init, clamp the default start (today − 29) up to the earliest entry
        // date so the chart opens on a window that actually contains data.
        val effectiveStart = if (!f.initialized && earliestLocal != null && f.start < earliestLocal) {
            earliestLocal.coerceAtMost(f.end)
        } else {
            f.start
        }

        val startDay = effectiveStart.toEpochDay()
        val days = (f.end.toEpochDay() - startDay).toInt() + 1

        val selected = if (!f.initialized || f.selected.isEmpty()) {
            scaleList.filter { !it.archived }.map { it.id }.toSet()
        } else f.selected

        val filteredRows = rows.filter { it.entry.slot in f.slots }

        val series = scaleList.map { scale ->
            val perDaySum = FloatArray(days)
            val perDayCount = IntArray(days)
            for (row in filteredRows) {
                val v = row.values.firstOrNull { it.scaleId == scale.id } ?: continue
                val dayIdx = (row.entry.recordedAt.atZone(zone).toLocalDate().toEpochDay() - startDay).toInt()
                if (dayIdx in 0 until days) {
                    perDaySum[dayIdx] += v.value.toFloat()
                    perDayCount[dayIdx] += 1
                }
            }
            val daily = (0 until days).mapNotNull { i ->
                if (perDayCount[i] == 0) null else DayPoint(i, perDaySum[i] / perDayCount[i])
            }
            val rolling = rollingAverage(perDaySum, perDayCount, window = 7)
            ScaleSeries(scale = scale, rolling = rolling, daily = daily)
        }

        return ChartsUiState(
            startDate = effectiveStart,
            endDate = f.end,
            slotFilter = f.slots,
            selectedScaleIds = selected,
            scales = scaleList,
            series = series,
            entryCount = filteredRows.size,
            chartMode = f.mode,
            loaded = true,
            earliestDate = earliestLocal,
        ).also {
            if (!f.initialized) {
                filters.value = f.copy(start = effectiveStart, selected = selected, initialized = true)
            }
        }
    }

    private fun rollingAverage(
        sums: FloatArray,
        counts: IntArray,
        window: Int,
    ): List<DayPoint> {
        val out = mutableListOf<DayPoint>()
        var rollSum = 0f
        var rollCount = 0
        for (i in sums.indices) {
            rollSum += sums[i]
            rollCount += counts[i]
            val drop = i - window
            if (drop >= 0) {
                rollSum -= sums[drop]
                rollCount -= counts[drop]
            }
            if (rollCount > 0) out += DayPoint(i, rollSum / rollCount)
        }
        return out
    }
}
