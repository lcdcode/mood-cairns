package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupStoreNamesTest {

    private val day = LocalDate.of(2026, 11, 12)

    @Test
    fun fileNames_distinguishFullBackupsFromConfigOnlyExports() {
        assertEquals("mood-cairns-backup-2026-11-12.json", BackupStore.suggestName(day))
        assertEquals("mood-cairns-config-only-2026-11-12.json", BackupStore.suggestConfigName(day))
        assertEquals("mood-cairns-export-2026-11-12.csv", BackupStore.suggestCsvName(day))
    }

    @Test
    fun isConfigOnlyName_recognizesOnlyConfigExports() {
        assertTrue(BackupStore.isConfigOnlyName(BackupStore.suggestConfigName(day)))
        // MediaStore appends " (1)" to a second export on the same day.
        assertTrue(BackupStore.isConfigOnlyName("mood-cairns-config-only-2026-11-12 (1).json"))
        assertFalse(BackupStore.isConfigOnlyName(BackupStore.suggestName(day)))
    }
}
