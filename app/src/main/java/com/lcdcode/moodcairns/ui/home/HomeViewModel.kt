package com.lcdcode.moodcairns.ui.home

import androidx.lifecycle.ViewModel
import com.lcdcode.moodcairns.settings.BackupReminder
import com.lcdcode.moodcairns.settings.JournalSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    journalSettings: JournalSettings,
    private val backupReminder: BackupReminder,
) : ViewModel() {
    val journalEnabled: StateFlow<Boolean> = journalSettings.enabled
    val backupReminderDue: StateFlow<Boolean> = backupReminder.due

    fun refreshBackupReminder() = backupReminder.refresh()

    fun dismissBackupReminder() = backupReminder.postpone()
}
