package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSizeLimitTest {

    @Test
    fun `the limit is fifty megabytes`() {
        assertEquals(50L, BackupSizeLimit.MAX_MEGABYTES)
        assertEquals(50L * 1024 * 1024, BackupSizeLimit.MAX_BYTES)
    }

    @Test
    fun `a file exactly at the limit is importable, one byte more is not`() {
        assertFalse(BackupSizeLimit.exceeds(BackupSizeLimit.MAX_BYTES))
        assertTrue(BackupSizeLimit.exceeds(BackupSizeLimit.MAX_BYTES + 1))
    }

    @Test
    fun `small envelopes pass the export check`() {
        BackupSizeLimit.requireImportable("{\"envelope\":1}") // must not throw
    }
}
