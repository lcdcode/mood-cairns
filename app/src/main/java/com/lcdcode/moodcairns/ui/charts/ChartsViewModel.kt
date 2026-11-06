package com.lcdcode.moodcairns.ui.charts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.repo.EntryRepository
import com.lcdcode.moodcairns.data.repo.PromptWindowRepository
import com.lcdcode.moodcairns.data.repo.ScaleRepository
import com.lcdcode.moodcairns.data.repo.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class DayPoint(val dayIndex: Int, val value: Float)

/** One logged value at its own time; [minuteX] is minutes since the start of the range. */
data class EntryPoint(val minuteX: Int, val value: Float)

/** Mean of every entry logged during [hour] (0-23) of any day in the range. */
data class HourPoint(val hour: Int, val value: Float)

data class ScaleSeries(
    val scale: Scale,
    val rolling: List<DayPoint>,
    val daily: List<DayPoint>,
    val entries: List<EntryPoint> = emptyList(),
    val hourly: List<HourPoint> = emptyList(),
)

enum class ChartMode { Raw, RollingAvg, Hourly }

/** Days averaged by [ChartMode.RollingAvg]; also shown in its labels. Must be odd to center. */
const val ROLLING_AVERAGE_DAYS = 7

/** Days on each side of the center day in the rolling average. */
const val ROLLING_AVERAGE_HALF_WINDOW = ROLLING_AVERAGE_DAYS / 2

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
    val tags: List<Tag> = emptyList(),
    // OR semantics: entries carrying any selected tag pass; empty = no tag filter.
    val selectedTagIds: Set<Long> = emptySet(),
    val series: List<ScaleSeries> = emptyList(),
    // Raw entries per day index (relative to startDate), for the tapped-point card.
    val entriesByDay: Map<Int, List<EntryWithValues>> = emptyMap(),
    // Raw entries per local hour of day (0-23), for the Hourly tapped-point card.
    val entriesByHour: Map<Int, List<EntryWithValues>> = emptyMap(),
    val entryCount: Int = 0,
    val chartMode: ChartMode = ChartMode.Raw,
    val absoluteYAxis: Boolean = false,
    val loaded: Boolean = false,
    val earliestDate: LocalDate? = null,
) {
    val days: Int get() = (endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1
}

/** A filtered row with its local time and day index, resolved once per build. */
private data class LocatedRow(
    val row: EntryWithValues,
    val local: LocalDateTime,
    val dayIndex: Int,
)

private data class Filters(
    val start: LocalDate,
    val end: LocalDate,
    val excluded: Set<SlotKey>,
    val selected: Set<Long>,
    val selectedTags: Set<Long>,
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
    tags: TagRepository,
) : ViewModel() {

    private val filters = MutableStateFlow(
        Filters(
            start = LocalDate.now().minusDays(29),
            end = LocalDate.now(),
            excluded = emptySet(),
            selected = emptySet(),
            selectedTags = emptySet(),
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
    private val tagsFlow = tags.observeAll()

    val state: StateFlow<ChartsUiState> = filters
        .flatMapLatest { f ->
            val zone = ZoneId.systemDefault()
            // Half-open interval [from, toExclusive): start-of-startDate up to
            // (but not including) start-of-the-day-after-endDate, so entries
            // recorded at any time on the selected end date are included. Padded
            // by the average's half window so edge days average against real
            // neighbors; buildState keeps padding rows out of everything else.
            val padDays = ROLLING_AVERAGE_HALF_WINDOW.toLong()
            val from = f.start.minusDays(padDays).atStartOfDay(zone).toInstant()
            val toExclusive = f.end.plusDays(1 + padDays).atStartOfDay(zone).toInstant()
            combine(
                entries.observeRange(from, toExclusive),
                scalesFlow,
                earliestFlow,
                windowsFlow,
                tagsFlow,
            ) { rows, scaleList, earliest, windowList, tagList ->
                buildState(f, rows, scaleList, earliest, windowList, tagList)
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

    fun toggleTagFilter(id: Long) = filters.update {
        val next = if (id in it.selectedTags) it.selectedTags - id else it.selectedTags + id
        it.copy(selectedTags = next)
    }

    fun clearTagFilter() = filters.update { it.copy(selectedTags = emptySet()) }

    fun setChartMode(mode: ChartMode) = filters.update { it.copy(mode = mode) }

    fun setAbsoluteYAxis(value: Boolean) = filters.update { it.copy(absoluteY = value) }

    private fun buildState(
        f: Filters,
        rows: List<EntryWithValues>,
        scaleList: List<Scale>,
        earliest: Instant?,
        windowList: List<PromptWindow>,
        tagList: List<Tag>,
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

        fun isInRange(row: EntryWithValues): Boolean {
            val day = row.entry.recordedAt.atZone(zone).toLocalDate().toEpochDay() - startDay
            return day in 0 until days
        }

        val knownWindowIds = windowList.mapTo(HashSet()) { it.id }
        // Only in-range rows count: padding rows exist solely to feed the average.
        val hasOther = rows.any {
            isInRange(it) && slotKeyFor(it.entry, knownWindowIds) == SlotKey.Other
        }
        visibleSlotKeys = buildSet {
            windowList.forEach { add(SlotKey.Window(it.id)) }
            add(SlotKey.Manual)
            add(SlotKey.Custom)
            if (hasOther) add(SlotKey.Other)
        }
        val slotFilteredRows = rows.filter { slotKeyFor(it.entry, knownWindowIds) !in f.excluded }
        val filteredRows = if (f.selectedTags.isEmpty()) {
            slotFilteredRows
        } else {
            slotFilteredRows.filter { row -> row.tags.any { it.id in f.selectedTags } }
        }

        val located = filteredRows.map { row ->
            val local = row.entry.recordedAt.atZone(zone).toLocalDateTime()
            LocatedRow(row, local, (local.toLocalDate().toEpochDay() - startDay).toInt())
        }
        val inRange = located.filter { it.dayIndex in 0 until days }

        // Preserve the raw entries (note + tags) behind each plotted day and hour so
        // the tapped-point cards can show a per-entry breakdown. Ordered by
        // recordedAt ASC from the query, which groupBy preserves.
        val entriesByDay = inRange.groupBy({ it.dayIndex }, { it.row })
        val entriesByHour = inRange.groupBy({ it.local.hour }, { it.row })

        val series = scaleList.map { scale ->
            buildSeries(scale, located, inRange, days, effectiveStart)
        }

        return ChartsUiState(
            startDate = effectiveStart,
            endDate = f.end,
            windows = windowList,
            excludedSlots = f.excluded,
            showOther = hasOther,
            selectedScaleIds = selected,
            scales = scaleList,
            tags = tagList,
            selectedTagIds = f.selectedTags,
            series = series,
            entriesByDay = entriesByDay,
            entriesByHour = entriesByHour,
            entryCount = inRange.size,
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

    private fun buildSeries(
        scale: Scale,
        located: List<LocatedRow>,
        inRange: List<LocatedRow>,
        days: Int,
        startDate: LocalDate,
    ): ScaleSeries {
        fun valueOf(row: LocatedRow) = row.row.values.firstOrNull { it.scaleId == scale.id }?.value

        // Indexed with the average's padding days on both sides of the range.
        val pad = ROLLING_AVERAGE_HALF_WINDOW
        val perDaySum = FloatArray(days + 2 * pad)
        val perDayCount = IntArray(days + 2 * pad)
        for (row in located) {
            val v = valueOf(row) ?: continue
            val idx = row.dayIndex + pad
            if (idx in perDaySum.indices) {
                perDaySum[idx] += v
                perDayCount[idx] += 1
            }
        }
        val daily = (0 until days).mapNotNull { i ->
            val idx = i + pad
            if (perDayCount[idx] == 0) null else DayPoint(i, perDaySum[idx] / perDayCount[idx])
        }
        val rolling = centeredWeightedAverage(perDaySum, perDayCount, pad, padding = pad)
        val entries = inRange.mapNotNull { row ->
            valueOf(row)?.let { EntryPoint(minuteX(row.local, startDate), it) }
        }
        val hourly = hourlyMeans(
            inRange.mapNotNull { row -> valueOf(row)?.let { row.local.hour to it } },
        )
        return ScaleSeries(
            scale = scale,
            rolling = rolling,
            daily = daily,
            entries = entries,
            hourly = hourly,
        )
    }
}
