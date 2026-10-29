package com.lcdcode.moodcairns.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.data.repo.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagListUiState(
    val byCategory: Map<TagCategory, List<Tag>> = emptyMap(),
    val categoryLabels: Map<TagCategory, String> = emptyMap(),
)

@HiltViewModel
class TagListViewModel @Inject constructor(
    private val repo: TagRepository,
) : ViewModel() {

    val state: StateFlow<TagListUiState> =
        combine(repo.observeAll(), repo.observeCategoryLabels()) { all, labels ->
            TagListUiState(byCategory = all.groupBy { it.category }, categoryLabels = labels)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagListUiState())

    fun onReorder(orderedIds: List<Long>) = viewModelScope.launch {
        repo.reorder(orderedIds)
    }

    fun renameCategory(category: TagCategory, raw: String, defaultName: String) =
        viewModelScope.launch {
            repo.setCategoryLabel(category, categoryLabelToStore(raw, defaultName))
        }
}
