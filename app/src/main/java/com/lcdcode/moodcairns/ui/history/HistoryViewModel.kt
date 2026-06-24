package com.lcdcode.moodcairns.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.EntryRepository
import com.lcdcode.moodcairns.data.repo.PromptWindowRepository
import com.lcdcode.moodcairns.data.repo.ScaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val entries: List<EntryWithValues> = emptyList(),
    val scalesById: Map<Long, Scale> = emptyMap(),
    val windowsById: Map<Long, PromptWindow> = emptyMap(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val entries: EntryRepository,
    scales: ScaleRepository,
    windows: PromptWindowRepository,
) : ViewModel() {

    val state: StateFlow<HistoryUiState> =
        combine(
            entries.observeAll(),
            scales.observeAll(),
            windows.observeAll(),
        ) { list, scaleList, windowList ->
            HistoryUiState(
                entries = list,
                scalesById = scaleList.associateBy { it.id },
                windowsById = windowList.associateBy { it.id },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun delete(entryId: Long) {
        viewModelScope.launch { entries.delete(entryId) }
    }
}
