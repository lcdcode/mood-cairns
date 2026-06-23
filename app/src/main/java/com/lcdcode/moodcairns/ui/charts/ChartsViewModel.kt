package com.lcdcode.moodcairns.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.EntryRepository
import com.lcdcode.moodcairns.data.repo.PromptWindowRepository
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

/**
 * Identifies one prompt-slot filter chip. Windows are keyed by id (so renamed or
 * multiple same-slot windows stay distinct); Manual/Custom are the slot-only
 * entries; Other catches entries that map to no current chip (legacy slots or a
 * since-deleted window) so they remain filterable rather than silently hidden.
 */
sealed interface SlotKey {
    data class Window(val id: Long) : SlotKey
    object Manual : SlotKey
    object Custom : SlotKey
    object Other : SlotKey
}

/**
 * Maps an entry onto its filter chip. A window id is honored only when the
 * window still exists; otherwise Manual/Custom fall through to their slot, and
 * everything else (legacy slot, deleted window) buckets into Other.
 */
internal fun slotKeyFor(entry: Entry, knownWindowIds: Set<Long>): SlotKey {
    val windowId = entry.promptWindowId
    return when {
        windowId != null && windowId in knownWindowIds -> SlotKey.Window(windowId)
        windowId == null && entry.slot == PromptSlot.MANUAL -> SlotKey.Manual
        windowId == null && entry.slot == PromptSlot.CUSTOM -> SlotKey.Custom
        else -> SlotKey.Other
    }
}

data class ChartsUiState(
    val startDate: LocalDate = LocalDate.now().minusDays(29),
    val endDate: LocalDate = LocalDate.now(),
    val windows: List<PromptWindow> = emptyList(),
    val excludedSlots: Set<SlotKey> = emptySet(),
    val showOther: Boolean = false,
    val selectedScaleIds: Set<Long> = emptySet(),
    val scales: List<Scale> = emptyList(),
    val series: List<ScaleSeries> = emptyList(),
    val entryCount: Int = 0,
    val chartMode: ChartMode = ChartMode.Raw,
    val absoluteYAxis: Boolean = false,
    val loaded: Boolean = false,
    val earliestDate: LocalDate? = null,
) {
    val days: Int get() = (endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1
}

private data class Filters(
    val start: LocalDate,
    val end: LocalDate,
    val excluded: Set<SlotKey>,
    val selected: Set<Long>,
    val mode: ChartMode,
    val absoluteY: Boolean,
    val initialized: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChartsViewModel @Inject constructor(
    private val entries: EntryRepository,
    scales: ScaleRepository,
    windows: PromptWindowRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(
        Filters(
            start = LocalDate.now().minusDays(29),
            end = LocalDate.now(),
            excluded = emptySet(),
            selected = emptySet(),
            mode = ChartMode.Raw,
            absoluteY = false,
            initialized = false,
        ),
    )

    // Slot-filter options visible in the latest render, cached so toggleSlot can
    // refuse a toggle that would hide every option. Best-effort guard only.
    @Volatile
    private var visibleSlotKeys: Set<SlotKey> = emptySet()

    private val scalesFlow = scales.observeAll()
    private val windowsFlow = windows.observeAll()
    private val earliestFlow = entries.observeEarliestRecordedAt()

    val state: StateFlow<ChartsUiState> = filters
        .flatMapLatest { f ->
            val zone = ZoneId.systemDefault()
            // Half-open interval [from, toExclusive): start-of-startDate up to
            // (but not including) start-of-the-day-after-endDate, so entries
            // recorded at any time on the selected end date are included.
            val from = f.start.atStartOfDay(zone).toInstant()
            val toExclusive = f.end.plusDays(1).atStartOfDay(zone).toInstant()
            combine(
                entries.observeRange(from, toExclusive),
                scalesFlow,
                earliestFlow,
                windowsFlow,
            ) { rows, scaleList, earliest, windowList ->
                buildState(f, rows, scaleList, earliest, windowList)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChartsUiState())

    fun setRange(start: LocalDate, end: LocalDate) = filters.update {
        val (s, e) = if (start <= end) start to end else end to start
        it.copy(start = s, end = e)
    }

    fun toggleSlot(key: SlotKey) = filters.update {
        val next = if (key in it.excluded) it.excluded - key else it.excluded + key
        // Keep at least one visible option active, mirroring the old slot filter.
        val hidesEverything = visibleSlotKeys.isNotEmpty() && visibleSlotKeys.all { k -> k in next }
        if (hidesEverything) it else it.copy(excluded = next)
    }

    fun toggleScale(id: Long) = filters.update {
        val next = if (id in it.selected) it.selected - id else it.selected + id
        it.copy(selected = next)
    }

    fun setChartMode(mode: ChartMode) = filters.update { it.copy(mode = mode) }

    fun setAbsoluteYAxis(value: Boolean) = filters.update { it.copy(absoluteY = value) }

    private fun buildState(
        f: Filters,
        rows: List<EntryWithValues>,
        scaleList: List<Scale>,
        earliest: java.time.Instant?,
        windowList: List<PromptWindow>,
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

        val knownWindowIds = windowList.mapTo(HashSet()) { it.id }
        val hasOther = rows.any { slotKeyFor(it.entry, knownWindowIds) == SlotKey.Other }
        visibleSlotKeys = buildSet {
            windowList.forEach { add(SlotKey.Window(it.id)) }
            add(SlotKey.Manual)
            add(SlotKey.Custom)
            if (hasOther) add(SlotKey.Other)
        }
        val filteredRows = rows.filter { slotKeyFor(it.entry, knownWindowIds) !in f.excluded }

        val series = scaleList.map { scale ->
            val perDaySum = FloatArray(days)
            val perDayCount = IntArray(days)
            for (row in filteredRows) {
                val v = row.values.firstOrNull { it.scaleId == scale.id } ?: continue
                val dayIdx = (row.entry.recordedAt.atZone(zone).toLocalDate().toEpochDay() - startDay).toInt()
                if (dayIdx in 0 until days) {
                    perDaySum[dayIdx] += v.value
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
            windows = windowList,
            excludedSlots = f.excluded,
            showOther = hasOther,
            selectedScaleIds = selected,
            scales = scaleList,
            series = series,
            entryCount = filteredRows.size,
            chartMode = f.mode,
            absoluteYAxis = f.absoluteY,
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
