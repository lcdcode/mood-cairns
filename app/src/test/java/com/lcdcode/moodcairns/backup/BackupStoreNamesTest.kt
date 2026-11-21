package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BackupStoreNamesTest {

    private val day = LocalDate.of(2026, 11, 12)

    @Test
    fun fileNames_distinguishEachKindOfExport() {
        assertEquals("mood-cairns-backup-2026-11-12.json", BackupStore.suggestName(day))
        assertEquals("mood-cairns-config-only-2026-11-12.json", BackupStore.suggestConfigName(day))
        assertEquals("mood-cairns-journal-2026-11-12.json", BackupStore.suggestJournalName(day))
        assertEquals("mood-cairns-export-2026-11-12.csv", BackupStore.suggestCsvName(day))
    }

    @Test
    fun classifyBackupName_recognizesEachKind() {
        assertEquals(BackupKind.Mood, BackupStore.classifyBackupName(BackupStore.suggestName(day)))
        assertEquals(
            BackupKind.Config,
            BackupStore.classifyBackupName(BackupStore.suggestConfigName(day)),
        )
        assertEquals(
            BackupKind.Journal,
            BackupStore.classifyBackupName(BackupStore.suggestJournalName(day)),
        )
    }

    @Test
    fun classifyBackupName_survivesMediaStoreDuplicateSuffix() {
        // MediaStore appends " (1)" to a second export on the same day.
        assertEquals(
            BackupKind.Config,
            BackupStore.classifyBackupName("mood-cairns-config-only-2026-11-12 (1).json"),
        )
        assertEquals(
            BackupKind.Journal,
            BackupStore.classifyBackupName("mood-cairns-journal-2026-11-12 (1).json"),
        )
    }
}
