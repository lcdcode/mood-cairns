package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.JournalSql
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/**
 * Pins the journal backup format: what it accepts, what it refuses, and that journal and
 * mood/configuration files can never be restored as each other, by this or an older app.
 */
class JournalBackupDecoderTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val decoder = JournalBackupDecoder(json)

    private fun page(day: String = "2026-11-01", body: String = "# Hello") = JournalDayDto(
        day = day,
        createdAt = "2026-11-01T08:00:00Z",
        updatedAt = "2026-11-01T09:30:00Z",
        body = body,
    )

    private fun journalFile(vararg pages: JournalDayDto) = JournalBackupFile(
        kind = JournalBackupFile.KIND,
        journalVersion = JournalBackupFile.CURRENT_VERSION,
        exportedAt = "2026-11-20T10:00:00Z",
        appVersion = "test",
        days = pages.toList(),
    )

    private fun text(file: JournalBackupFile) =
        json.encodeToString(JournalBackupFile.serializer(), file)

    private fun importError(block: () -> Unit): ImportError =
        assertThrows(BackupImportException::class.java, block).error

    @Test
    fun `a journal backup decodes into pages`() {
        val docs = decoder.toDocs(decoder.decode(text(journalFile(page()))))
        val doc = docs.single()
        assertEquals(LocalDate.of(2026, 11, 1), doc.day)
        assertEquals(Instant.parse("2026-11-01T08:00:00Z"), doc.createdAt)
        assertEquals(Instant.parse("2026-11-01T09:30:00Z"), doc.updatedAt)
        assertEquals("# Hello", doc.body)
        assertEquals("", doc.preview) // recomputed by the DAO on insert
    }

    @Test
    fun `an empty journal is a valid backup`() {
        assertEquals(emptyList<Any>(), decoder.toDocs(decoder.decode(text(journalFile()))))
    }

    @Test
    fun `unknown extra keys from a newer app are ignored`() {
        val withExtra = text(journalFile(page())).replaceFirst("{", "{\"future\":true,")
        assertEquals(1, decoder.toDocs(decoder.decode(withExtra)).size)
    }

    @Test
    fun `a newer format version is unsupported even if its shape changed`() {
        val newer = "{\"kind\":\"${JournalBackupFile.KIND}\",\"journalVersion\":2,\"days\":\"?\"}"
        assertEquals(ImportError.UnsupportedVersion(2), importError { decoder.decode(newer) })
    }

    @Test
    fun `missing version is malformed`() {
        val raw = "{\"kind\":\"${JournalBackupFile.KIND}\",\"days\":[]}"
        assertTrue(importError { decoder.decode(raw) } is ImportError.Malformed)
    }

    @Test
    fun `contents that are not a JSON object are malformed`() {
        assertTrue(importError { decoder.decode("[1,2]") } is ImportError.Malformed)
        assertTrue(importError { decoder.decode("not json") } is ImportError.Malformed)
    }

    @Test
    fun `a day listed twice is refused`() {
        val file = journalFile(page(), page(body = "again"))
        assertTrue(importError { decoder.toDocs(file) } is ImportError.Malformed)
    }

    @Test
    fun `invalid dates and timestamps are refused`() {
        val badDay = journalFile(page(day = "2026-13-01"))
        assertTrue(importError { decoder.toDocs(badDay) } is ImportError.Malformed)
        val badTime = journalFile(page().copy(updatedAt = "yesterday"))
        assertTrue(importError { decoder.toDocs(badTime) } is ImportError.Malformed)
    }

    @Test
    fun `pages longer than the editor allows are refused, not cut`() {
        val tooLong = page(body = "a".repeat(JournalSql.MAX_BODY_CHARS + 1))
        assertTrue(importError { decoder.toDocs(journalFile(tooLong)) } is ImportError.Malformed)
        val atLimit = page(body = "a".repeat(JournalSql.MAX_BODY_CHARS))
        assertEquals(1, decoder.toDocs(journalFile(atLimit)).size)
    }

    // Cross-kind guards

    @Test
    fun `a mood backup is not a journal backup`() {
        val mood = json.encodeToString(BackupFile.serializer(), moodBackup())
        assertEquals(ImportError.NotAJournalBackup, importError { decoder.decode(mood) })
    }

    @Test
    fun `a configuration file is not a journal backup`() {
        val config = json.encodeToString(ConfigFile.serializer(), configFile())
        assertEquals(ImportError.NotAJournalBackup, importError { decoder.decode(config) })
    }

    @Test
    fun `this app refuses a journal backup in the mood and configuration imports`() {
        val contents = json.parseToJsonElement(text(journalFile(page()))).jsonObject
        val moodDecoder = BackupContentsDecoder(json)
        assertEquals(ImportError.JournalFile, importError { moodDecoder.forFullRestore(contents) })
        assertEquals(ImportError.JournalFile, importError { moodDecoder.forConfigImport(contents) })
    }

    @Test
    fun `older apps cannot decode a journal backup as mood data or configuration`() {
        // Older versions decode the plaintext straight into these classes. Missing required
        // keys (schemaVersion, entries, configVersion) make them fail before writing anything.
        val raw = text(journalFile(page()))
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(BackupFile.serializer(), raw)
        }
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(ConfigFile.serializer(), raw)
        }
    }

    private fun moodBackup() = BackupFile(
        schemaVersion = BackupFile.CURRENT_VERSION,
        exportedAt = "2026-11-20T10:00:00Z",
        appVersion = "test",
        scales = emptyList(),
        promptWindows = emptyList(),
        entries = emptyList(),
    )

    private fun configFile() = ConfigFile(
        kind = ConfigFile.KIND,
        configVersion = ConfigFile.CURRENT_VERSION,
        exportedAt = "2026-11-20T10:00:00Z",
        appVersion = "test",
        scales = emptyList(),
        promptWindows = emptyList(),
        tags = emptyList(),
        tagCategories = emptyList(),
    )
}
