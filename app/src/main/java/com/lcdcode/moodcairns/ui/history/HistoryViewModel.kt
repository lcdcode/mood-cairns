package com.lcdcode.moodcairns.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.EntryRepository
import com.lcdcode.moodcairns.data.repo.PromptWindowRepository
import com.lcdcode.moodcairns.data.repo.ScaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HistoryUiState(
    val entries: List<EntryWithValues> = emptyList(),
    val scalesById: Map<Long, Scale> = emptyMap(),
    val windowsById: Map<Long, PromptWindow> = emptyMap(),
    val dateFilter: LocalDate? = null,
    val slotFilter: PromptSlot? = null,
    val searchQuery: String = "",
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val entries: EntryRepository,
    scales: ScaleRepository,
    windows: PromptWindowRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                entries.observeAll(),
                scales.observeAll(),
                windows.observeAll(),
            ) { list, scaleList, windowList ->
                _state.value.copy(
                    entries = list,
                    scalesById = scaleList.associateBy { it.id },
                    windowsById = windowList.associateBy { it.id },
                )
            }.collect { _state.value = it }
        }
    }

    fun delete(entryId: Long) {
        viewModelScope.launch { entries.delete(entryId) }
    }

    fun setDateFilter(date: LocalDate?) {
        _state.value = _state.value.copy(dateFilter = date)
    }

    fun setSlotFilter(slot: PromptSlot?) {
        _state.value = _state.value.copy(slotFilter = slot)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun clearAllFilters() {
        _state.value = _state.value.copy(
            dateFilter = null,
            slotFilter = null,
            searchQuery = "",
        )
    }
}
