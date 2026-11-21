package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.entity.JournalDoc
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate

/**
 * Turns decrypted journal backup JSON into pages, refusing anything that is not a journal
 * backup this version can read. Uses no Android APIs, so JVM tests cover it. Every failure
 * throws [BackupImportException]; nothing is written until a whole file has validated.
 */
internal class JournalBackupDecoder(private val json: Json) {

    fun decode(plaintext: String): JournalBackupFile {
        val header = try {
            json.decodeFromString(JournalBackupHeader.serializer(), plaintext)
        } catch (e: SerializationException) {
            fail(ImportError.Malformed(e.message ?: "Backup contents are not a JSON object"))
        } catch (e: IllegalArgumentException) {
            fail(ImportError.Malformed(e.message ?: "Backup contents are not a JSON object"))
        }
        if (header.kind != JournalBackupFile.KIND) fail(ImportError.NotAJournalBackup)
        val version = header.journalVersion
            ?: fail(ImportError.Malformed("Journal backup is missing journalVersion"))
        if (version !in JournalBackupFile.SUPPORTED_VERSIONS) {
            fail(ImportError.UnsupportedVersion(version))
        }
        return try {
            json.decodeFromString(JournalBackupFile.serializer(), plaintext)
        } catch (e: SerializationException) {
            fail(ImportError.Malformed(e.message ?: "Unreadable journal backup"))
        } catch (e: IllegalArgumentException) {
            fail(ImportError.Malformed(e.message ?: "Unreadable journal backup"))
        }
    }

    /**
     * Validated pages ready to insert. Refuses unparseable dates, a day listed twice, and
     * bodies over the editor's limit, rather than silently dropping or cutting text.
     * Previews are left empty: the DAO recomputes them from each body.
     */
    fun toDocs(file: JournalBackupFile): List<JournalDoc> {
        val seen = HashSet<LocalDate>()
        return file.days.map { dto ->
            val day = parse("day", dto.day) { LocalDate.parse(it) }
            if (!seen.add(day)) fail(ImportError.Malformed("Day ${dto.day} appears twice"))
            if (dto.body.length > JournalSql.MAX_BODY_CHARS) {
                fail(ImportError.Malformed("Page for ${dto.day} is longer than the editor allows"))
            }
            JournalDoc(
                day = day,
                createdAt = parse("createdAt", dto.createdAt) { Instant.parse(it) },
                updatedAt = parse("updatedAt", dto.updatedAt) { Instant.parse(it) },
                preview = "",
                body = dto.body,
            )
        }
    }

    private fun <T> parse(field: String, value: String, parser: (String) -> T): T = try {
        parser(value)
    } catch (_: DateTimeException) {
        fail(ImportError.Malformed("Invalid $field \"$value\""))
    }
}

/**
 * True when the decrypted contents are a journal backup. Lets the mood and configuration
 * imports name the problem instead of reporting a missing schemaVersion.
 */
internal fun isJournalFile(contents: JsonObject): Boolean =
    (contents[JOURNAL_KIND_KEY] as? JsonPrimitive)?.content == JournalBackupFile.KIND

private const val JOURNAL_KIND_KEY = "kind"

private fun fail(error: ImportError): Nothing = throw BackupImportException(error)
