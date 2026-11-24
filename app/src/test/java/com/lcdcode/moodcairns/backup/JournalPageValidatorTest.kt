package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.JournalSql
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class JournalPageValidatorTest {

    private val validator = JournalPageValidator(TODAY)

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

    private fun refused(reason: MalformedReason, block: () -> Unit) {
        val error = assertThrows(BackupImportException::class.java, block).error
        assertEquals(ImportError.Malformed(reason), error)
    }

    @Test
    fun a_valid_page_becomes_a_document_with_an_empty_preview() {
        val doc = validator.toDoc(page())
        assertEquals(LocalDate.of(2026, 11, 1), doc.day)
        assertEquals(Instant.parse("2026-11-01T08:00:00Z"), doc.createdAt)
        assertEquals(Instant.parse("2026-11-01T09:30:00Z"), doc.updatedAt)
        assertEquals("# Hello", doc.body)
        assertEquals("", doc.preview) // the repository recomputes it
    }

    @Test
    fun a_day_listed_twice_is_refused() {
        validator.toDoc(page())
        refused(MalformedReason.DAYS_OUT_OF_ORDER) { validator.toDoc(page(body = "again")) }
    }

    @Test
    fun a_day_earlier_than_the_previous_one_is_refused() {
        validator.toDoc(page(day = "2026-11-05"))
        refused(MalformedReason.DAYS_OUT_OF_ORDER) { validator.toDoc(page(day = "2026-11-04")) }
    }

    @Test
    fun ascending_days_with_gaps_are_accepted() {
        val days = listOf("1900-01-01", "1999-12-31", "2026-11-01", "2026-11-24")
        assertEquals(days, days.map { validator.toDoc(page(day = it)).day.toString() })
    }

    @Test
    fun the_earliest_allowed_day_is_accepted_the_day_before_is_not() {
        assertEquals(
            JournalBackupFormat.EARLIEST_DAY,
            JournalPageValidator(TODAY).toDoc(page(day = "1900-01-01")).day,
        )
        refused(MalformedReason.DAY_OUT_OF_RANGE) {
            JournalPageValidator(TODAY).toDoc(page(day = "1899-12-31"))
        }
    }

    @Test
    fun days_up_to_the_future_grace_are_accepted_later_ones_are_not() {
        val lastAllowed = TODAY.plusDays(JournalBackupFormat.FUTURE_GRACE_DAYS)
        assertEquals(
            lastAllowed,
            JournalPageValidator(TODAY).toDoc(page(day = lastAllowed.toString())).day,
        )
        refused(MalformedReason.DAY_OUT_OF_RANGE) {
            JournalPageValidator(TODAY).toDoc(page(day = lastAllowed.plusDays(1).toString()))
        }
    }

    @Test
    fun extreme_years_that_still_parse_are_out_of_range() {
        for (day in listOf("+999999999-12-31", "-999999999-01-01", "0000-01-01")) {
            refused(MalformedReason.DAY_OUT_OF_RANGE) {
                JournalPageValidator(TODAY).toDoc(page(day = day))
            }
        }
    }

    @Test
    fun invalid_dates_and_timestamps_are_refused() {
        malformed { validator.toDoc(page(day = "2026-13-01")) }
        malformed { validator.toDoc(page(day = "2026-11-02").copy(updatedAt = "yesterday")) }
    }

    @Test
    fun pages_longer_than_the_editor_allows_are_refused_not_cut() {
        refused(MalformedReason.BODY_TOO_LONG) {
            validator.toDoc(page(body = "a".repeat(JournalSql.MAX_BODY_CHARS + 1)))
        }
        val atLimit = page(day = "2026-11-03", body = "a".repeat(JournalSql.MAX_BODY_CHARS))
        assertEquals(JournalSql.MAX_BODY_CHARS, validator.toDoc(atLimit).body.length)
    }

    @Test
    fun a_rejected_value_is_reported_by_field_never_echoed() {
        val hostile = "2026-11-01\n[FAKE] \u202E".repeat(4_000)
        val cases = mapOf(
            page(day = hostile) to MalformedReason.INVALID_DAY,
            page().copy(createdAt = hostile) to MalformedReason.INVALID_CREATED_AT,
            page().copy(updatedAt = hostile) to MalformedReason.INVALID_UPDATED_AT,
        )
        for ((dto, reason) in cases) {
            val thrown = assertThrows(BackupImportException::class.java) {
                JournalPageValidator(TODAY).toDoc(dto)
            }
            assertEquals(ImportError.Malformed(reason), thrown.error)
            assertFalse(thrown.message.orEmpty().contains("FAKE"))
        }
    }

    @Test
    fun export_dtos_round_trip_through_the_validator() {
        val doc = validator.toDoc(page(day = "2026-11-04"))
        assertEquals(page(day = "2026-11-04"), doc.toBackupDto())
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 24)
    }
}
