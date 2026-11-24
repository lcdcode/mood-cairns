package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportErrorTest {

    private val safeCode = Regex("[a-z]+(-[a-z]+)*")

    @Test
    fun reason_codes_are_distinct_short_and_plain() {
        val codes = MalformedReason.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        for (code in codes) {
            assertTrue(code, safeCode.matches(code) && code.length <= MAX_CODE_CHARS)
        }
    }

    @Test
    fun a_release_log_label_never_carries_exception_detail() {
        val detail = Diagnostic.of("/storage/emulated/0/Download/my journal.mcj: EACCES")
        assertEquals("Unreadable", ImportError.Unreadable(detail).logLabel())
        assertEquals("WriteFailed", ImportError.WriteFailed(detail).logLabel())
        assertEquals(
            "Unreadable(${detail.text})",
            ImportError.Unreadable(detail).logLabel(withDetail = true),
        )
    }

    @Test
    fun a_malformed_label_names_only_the_reason() {
        assertEquals(
            "Malformed(invalid-day)",
            ImportError.Malformed(MalformedReason.INVALID_DAY).logLabel(),
        )
    }

    @Test
    fun the_exception_message_is_the_bounded_log_label() {
        val hostile = "x\n".repeat(50_000)
        val error = ImportError.WriteFailed(Diagnostic.of(hostile))
        val message = BackupImportException(error).message.orEmpty()
        assertEquals("Backup import failed: WriteFailed", message)
        assertFalse(message.contains('\n'))
    }

    private companion object {
        const val MAX_CODE_CHARS = 32
    }
}
