package com.lcdcode.moodcairns.ui.journal

import android.database.SQLException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.data.dao.JournalDocMeta
import com.lcdcode.moodcairns.data.dao.JournalSearchRow
import com.lcdcode.moodcairns.data.repo.JournalRepository
import com.lcdcode.moodcairns.data.repo.buildFtsQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class JournalHistoryRow(val day: LocalDate, val preview: HighlightedText)

data class JournalHistoryUiState(
    val query: String = "",
    val rows: List<JournalHistoryRow> = emptyList(),
    /** True while [rows] are search results rather than the full list. */
    val searching: Boolean = false,
    val loaded: Boolean = false,
)

/**
 * All journal pages, newest first, or the full-text search results for [setQuery]. Search
 * waits for a pause in typing and re-runs when pages change, so results stay current after
 * editing a page and coming back.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalHistoryViewModel @Inject constructor(
    private val repo: JournalRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val pages: Flow<List<JournalDocMeta>> = repo.observeMeta()
        .catch { e -> emit(emptyListOnLockedOrFailed(e)) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** Null when there is nothing to search for, so the full list shows. */
    private val searchResults: Flow<List<JournalSearchRow>?> = combine(
        query.debounce { if (buildFtsQuery(it).isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
        pages,
    ) { text, _ -> text }
        .mapLatest { text -> if (buildFtsQuery(text).isEmpty()) null else search(text) }

    val state: StateFlow<JournalHistoryUiState> = combine(query, pages, searchResults) {
            text, allPages, results ->
        JournalHistoryUiState(
            query = text,
            rows = results?.map(::rowOf) ?: allPages.map(::rowOf),
            searching = results != null,
            loaded = true,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        JournalHistoryUiState(),
    )

    fun setQuery(text: String) {
        query.value = text
    }

    private suspend fun search(text: String): List<JournalSearchRow> = try {
        repo.search(text)
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Journal search failed: database locked", e)
        emptyList()
    } catch (e: SQLException) {
        Log.w(TAG, "Journal search failed", e)
        emptyList()
    }

    private fun emptyListOnLockedOrFailed(e: Throwable): List<JournalDocMeta> {
        if (e !is IllegalStateException && e !is SQLException) throw e
        Log.w(TAG, "Loading journal pages failed", e)
        return emptyList()
    }

    private fun rowOf(meta: JournalDocMeta) =
        JournalHistoryRow(meta.day, HighlightedText(meta.preview))

    private fun rowOf(result: JournalSearchRow) =
        JournalHistoryRow(result.day, snippetPreview(result.snippet))

    private companion object {
        const val TAG = "JournalHistoryVM"
        const val SEARCH_DEBOUNCE_MS = 300L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
