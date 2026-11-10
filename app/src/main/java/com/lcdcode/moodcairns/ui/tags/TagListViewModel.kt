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
    val categories: List<TagCategory> = emptyList(),
    /** Tags keyed by category id; see [groupedByCategory]. */
    val byCategory: Map<String, List<Tag>> = emptyMap(),
)

@HiltViewModel
class TagListViewModel @Inject constructor(
    private val repo: TagRepository,
) : ViewModel() {

    val state: StateFlow<TagListUiState> =
        combine(repo.observeAll(), repo.observeCategories()) { all, categories ->
            TagListUiState(categories = categories, byCategory = all.groupedByCategory(categories))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagListUiState())

    fun onReorder(orderedIds: List<Long>) = viewModelScope.launch {
        repo.reorder(orderedIds)
    }

    /**
     * [defaultName] is the built-in's translated name, or null for a user category.
     * The dialogs never offer Other or a blank user name; these guards keep a stray
     * call from reaching the repository's checks, which would crash the app.
     */
    fun renameCategory(categoryId: String, raw: String, defaultName: String?) {
        if (categoryId == TagCategory.OTHER_ID || isMissingCategoryName(raw, defaultName)) return
        val toStore = if (defaultName == null) raw else categoryLabelToStore(raw, defaultName)
        viewModelScope.launch { repo.renameCategory(categoryId, toStore) }
    }

    fun createCategory(raw: String) {
        if (isMissingCategoryName(raw, defaultName = null)) return
        viewModelScope.launch { repo.createCategory(raw) }
    }

    /** Deletes the category; its tags move to Other. */
    fun deleteCategory(categoryId: String) {
        if (categoryId == TagCategory.OTHER_ID) return
        viewModelScope.launch { repo.deleteCategory(categoryId) }
    }
}
