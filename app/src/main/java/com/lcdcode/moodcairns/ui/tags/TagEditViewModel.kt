package com.lcdcode.moodcairns.ui.tags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.data.repo.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagEditUiState(
    val id: Long = 0,
    val name: String = "",
    val category: TagCategory = TagCategory.PLACE,
    val sortOrder: Int = 0,
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val deleting: Boolean = false,
    val deleted: Boolean = false,
    val affectedEntryCount: Int? = null,
    val error: String? = null,
)

@HiltViewModel
class TagEditViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TagEditUiState())
    val state: StateFlow<TagEditUiState> = _state.asStateFlow()

    init {
        val id = savedState.get<Long>(ARG_TAG_ID)?.takeIf { it > 0L }
        if (id == null) {
            val category = savedState.get<String>(ARG_CATEGORY)
                ?.let { name -> TagCategory.entries.find { it.name == name } }
                ?: TagCategory.PLACE
            _state.update { it.copy(category = category, loaded = true) }
        } else {
            viewModelScope.launch {
                val existing = repo.byId(id)
                if (existing != null) {
                    _state.update {
                        it.copy(
                            id = existing.id,
                            name = existing.name,
                            category = existing.category,
                            sortOrder = existing.sortOrder,
                            loaded = true,
                        )
                    }
                } else {
                    _state.update { it.copy(loaded = true, error = "Tag not found") }
                }
            }
        }
    }

    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setCategory(c: TagCategory) = _state.update { it.copy(category = c, error = null) }

    fun save() {
        val cur = _state.value
        val name = cur.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(error = "Name required") }
            return
        }

        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val sortOrder = if (cur.id == 0L) repo.nextSortOrder(cur.category) else cur.sortOrder
            val tag = Tag(
                id = cur.id,
                name = name,
                category = cur.category,
                sortOrder = sortOrder,
            )
            try {
                repo.upsert(tag)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (t: Throwable) {
                val message = if (t.message?.contains("UNIQUE", ignoreCase = true) == true) {
                    "A tag named \"$name\" already exists in ${cur.category.displayName}"
                } else {
                    t.message ?: "Save failed"
                }
                _state.update { it.copy(saving = false, error = message) }
            }
        }
    }

    /** Load how many entries carry this tag, for the delete confirmation. */
    fun loadAffectedEntryCount() {
        val id = _state.value.id
        if (id == 0L) return
        viewModelScope.launch {
            val count = repo.countEntriesUsing(id)
            _state.update { it.copy(affectedEntryCount = count) }
        }
    }

    fun delete() {
        val id = _state.value.id
        if (id == 0L || _state.value.deleting) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            try {
                repo.delete(id)
                _state.update { it.copy(deleting = false, deleted = true) }
            } catch (t: Throwable) {
                _state.update { it.copy(deleting = false, error = t.message ?: "Delete failed") }
            }
        }
    }

    companion object {
        const val ARG_TAG_ID = "tagId"
        const val ARG_CATEGORY = "category"
    }
}
