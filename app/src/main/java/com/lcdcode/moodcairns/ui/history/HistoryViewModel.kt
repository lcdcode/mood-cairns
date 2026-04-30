package com.lcdcode.moodcairns.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.EntryRepository
import com.lcdcode.moodcairns.data.repo.ScaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryUiState(
    val entries: List<EntryWithValues> = emptyList(),
    val scalesById: Map<Long, Scale> = emptyMap(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    entries: EntryRepository,
    scales: ScaleRepository,
) : ViewModel() {

    val state: StateFlow<HistoryUiState> =
        combine(entries.observeAll(), scales.observeAll()) { list, scaleList ->
            HistoryUiState(
                entries = list,
                scalesById = scaleList.associateBy { it.id },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())
}
