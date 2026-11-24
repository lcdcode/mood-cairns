package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSizeLimitTest {

    @Test
    fun the_limit_is_fifty_megabytes() {
        assertEquals(50L, BackupSizeLimit.MAX_MEGABYTES)
        assertEquals(50L * 1024 * 1024, BackupSizeLimit.MAX_BYTES)
    }

    @Test
    fun a_file_exactly_at_the_limit_is_importable_one_byte_more_is_not() {
        assertFalse(BackupSizeLimit.exceeds(BackupSizeLimit.MAX_BYTES))
        assertTrue(BackupSizeLimit.exceeds(BackupSizeLimit.MAX_BYTES + 1))
    }

    @Test
    fun small_envelopes_pass_the_export_check() {
        BackupSizeLimit.requireImportable("{\"envelope\":1}") // must not throw
    }
}
