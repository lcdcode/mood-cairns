package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.data.entity.JournalDoc
import com.lcdcode.moodcairns.data.repo.JournalRepository
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Encrypted journal backups: export every page, and read a backup back for restore. */
@Singleton
class JournalBackupSerializer @Inject constructor(
    private val repo: JournalRepository,
) {
    // Compact: journal text can be large and pretty-printing would only add whitespace.
    private val json = Json { ignoreUnknownKeys = true }
    private val envelopes = EnvelopeCodec(json)
    private val decoder = JournalBackupDecoder(json)

    /**
     * Encrypted JSON of every page. Throws [BackupTooLargeException] instead of producing a
     * file the import could not read back.
     */
    suspend fun exportJson(passphrase: CharArray): String {
        val file = JournalBackupFile(
            kind = JournalBackupFile.KIND,
            journalVersion = JournalBackupFile.CURRENT_VERSION,
            exportedAt = Instant.now().toString(),
            appVersion = BuildConfig.VERSION_NAME,
            days = repo.allDocs().map(::toDto),
        )
        val plaintext = json.encodeToString(JournalBackupFile.serializer(), file)
        return envelopes.encrypt(plaintext, passphrase).also(BackupSizeLimit::requireImportable)
    }

    /**
     * Decrypts [raw] with [secret] and validates it into pages. Throws
     * [BackupImportException] for a wrong secret, damaged data, a newer format, or a file
     * that is not a journal backup.
     */
    fun parse(raw: String, secret: CharArray): List<JournalDoc> {
        val plaintext = String(envelopes.decrypt(raw, secret), Charsets.UTF_8)
        return decoder.toDocs(decoder.decode(plaintext))
    }

    private fun toDto(doc: JournalDoc) = JournalDayDto(
        day = doc.day.toString(),
        createdAt = doc.createdAt.toString(),
        updatedAt = doc.updatedAt.toString(),
        body = doc.body,
    )
}
