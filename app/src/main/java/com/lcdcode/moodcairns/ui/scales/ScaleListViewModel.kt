package com.lcdcode.moodcairns.ui.scales

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.repo.ScaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScaleListUiState(
    val active: List<Scale> = emptyList(),
    val archived: List<Scale> = emptyList(),
)

@HiltViewModel
class ScaleListViewModel @Inject constructor(
    private val repo: ScaleRepository,
) : ViewModel() {

    val state: StateFlow<ScaleListUiState> = repo.observeAll()
        .map { all ->
            ScaleListUiState(
                active = all.filterNot { it.archived },
                archived = all.filter { it.archived },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScaleListUiState())

    fun setArchived(id: Long, archived: Boolean) = viewModelScope.launch {
        repo.setArchived(id, archived)
    }
}
