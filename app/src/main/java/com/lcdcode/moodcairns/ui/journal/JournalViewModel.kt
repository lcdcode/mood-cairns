package com.lcdcode.moodcairns.ui.journal

import android.database.SQLException
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.repo.JournalRepository
import com.lcdcode.moodcairns.markdown.MdTask
import com.lcdcode.moodcairns.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Edits one day's journal page. The day is fixed when the screen opens, so writing past
 * midnight keeps going to the page that is open (its date is in the title).
 *
 * Saving happens every [AUTOSAVE_INTERVAL_MS] while there are changes, and whenever the
 * screen asks for a [flush] (leaving the screen, app going to the background). The app
 * locks at the next foreground, not at background, so the background flush runs while the
 * database is still open. Only an explicit lock can close it first; then saves are skipped.
 */
@HiltViewModel
class JournalViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: JournalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        JournalUiState(day = resolveJournalDay(savedState[ARG_DAY], LocalDate.now())),
    )
    val state: StateFlow<JournalUiState> = _state.asStateFlow()

    private val saveMutex = Mutex()

    // Not tied to viewModelScope, which is already cancelled when onCleared runs its save.
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch {
            while (true) {
                delay(AUTOSAVE_INTERVAL_MS)
                saveIfChanged()
            }
        }
    }

    fun setBody(raw: String) = _state.update { it.copy(body = clampJournalBody(raw)) }

    fun toggleReadMode() = _state.update { it.copy(readMode = !it.readMode) }

    /** Ticks or unticks a checklist box from the read view; saved like any other edit. */
    fun toggleTask(task: MdTask) = _state.update { current ->
        toggleTaskAt(current.body, task.markerOffset)?.let { current.copy(body = it) } ?: current
    }

    fun flush() {
        saveScope.launch { saveIfChanged() }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    override fun onCleared() {
        flush()
    }

    private suspend fun load() {
        val day = _state.value.day
        try {
            val body = repo.load(day)?.body.orEmpty()
            val readMode = opensInReadMode(body)
            _state.update {
                it.copy(body = body, savedBody = body, loaded = true, readMode = readMode)
            }
        } catch (e: IllegalStateException) {
            onLoadFailed(e)
        } catch (e: SQLException) {
            onLoadFailed(e)
        }
    }

    private fun onLoadFailed(cause: Exception) {
        Log.w(TAG, "Loading journal page failed", cause)
        _state.update { it.copy(loadFailed = true) }
    }

    private suspend fun saveIfChanged() = saveMutex.withLock {
        val snapshot = _state.value
        if (!snapshot.hasUnsavedChanges || !repo.isOpen()) return@withLock
        try {
            repo.save(snapshot.day, snapshot.body)
            _state.update { it.copy(savedBody = snapshot.body) }
        } catch (e: IllegalStateException) {
            onSaveFailed(e) // Locked between the isOpen check and the write.
        } catch (e: SQLException) {
            onSaveFailed(e)
        }
    }

    private fun onSaveFailed(cause: Exception) {
        Log.w(TAG, "Saving journal page failed", cause)
        _state.update { it.copy(error = UiText.withDetail(R.string.error_save_failed, cause)) }
    }

    companion object {
        private const val TAG = "JournalViewModel"
        const val ARG_DAY = "day"
    }
}
