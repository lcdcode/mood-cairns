package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.JournalSql
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class JournalPageValidatorTest {

    private val validator = JournalPageValidator()

    private fun page(day: String = "2026-11-01", body: String = "# Hello") = JournalDayDto(
        day = day,
        createdAt = "2026-11-01T08:00:00Z",
        updatedAt = "2026-11-01T09:30:00Z",
        body = body,
    )

    private fun malformed(block: () -> Unit) {
        val error = assertThrows(BackupImportException::class.java, block).error
        assertTrue(error is ImportError.Malformed)
    }

    @Test
    fun `a valid page becomes a document with an empty preview`() {
        val doc = validator.toDoc(page())
        assertEquals(LocalDate.of(2026, 11, 1), doc.day)
        assertEquals(Instant.parse("2026-11-01T08:00:00Z"), doc.createdAt)
        assertEquals(Instant.parse("2026-11-01T09:30:00Z"), doc.updatedAt)
        assertEquals("# Hello", doc.body)
        assertEquals("", doc.preview) // the repository recomputes it
    }

    @Test
    fun `a day listed twice is refused`() {
        validator.toDoc(page())
        malformed { validator.toDoc(page(body = "again")) }
    }

    @Test
    fun `invalid dates and timestamps are refused`() {
        malformed { validator.toDoc(page(day = "2026-13-01")) }
        malformed { validator.toDoc(page(day = "2026-11-02").copy(updatedAt = "yesterday")) }
    }

    @Test
    fun `pages longer than the editor allows are refused, not cut`() {
        malformed { validator.toDoc(page(body = "a".repeat(JournalSql.MAX_BODY_CHARS + 1))) }
        val atLimit = page(day = "2026-11-03", body = "a".repeat(JournalSql.MAX_BODY_CHARS))
        assertEquals(JournalSql.MAX_BODY_CHARS, validator.toDoc(atLimit).body.length)
    }

    @Test
    fun `export dtos round trip through the validator`() {
        val doc = validator.toDoc(page(day = "2026-11-04"))
        assertEquals(page(day = "2026-11-04"), doc.toBackupDto())
    }
}
