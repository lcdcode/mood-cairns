package com.lcdcode.moodcairns.backup

import kotlinx.serialization.Serializable

/**
 * Journal backup file layout. Unlike mood backups (one JSON envelope read whole), a journal
 * backup is a binary stream so that export and restore use constant memory however large
 * the journal grows:
 *
 * ```
 * header (41 bytes, plaintext, authenticated with every chunk):
 *   magic "MCJOURNL" | format version (1 byte) | KDF id (1 byte) | PBKDF2 iterations (int)
 *   | salt (16) | nonce prefix (7) | chunk size (int)
 * chunks: AES-256-GCM, see [ChunkedAead]
 *   plaintext = zlib-compressed sequence of records, each an int length + UTF-8 JSON:
 *   one [JournalBackupMeta], then one [JournalDayDto] per page, then a 0-length end marker
 * ```
 *
 * Bump [FORMAT_VERSION] for container changes and [JOURNAL_VERSION] for record changes an
 * older reader would misread; older versions then refuse the file as unsupported.
 */
internal object JournalBackupFormat {
    val MAGIC: ByteArray = "MCJOURNL".toByteArray(Charsets.US_ASCII)
    const val MAGIC_TEXT = "MCJOURNL"
    const val FORMAT_VERSION: Byte = 1
    const val KDF_PBKDF2_SHA256: Byte = 1

    const val KIND = "mood-cairns-journal"
    const val JOURNAL_VERSION = 1
    val SUPPORTED_JOURNAL_VERSIONS = 1..JOURNAL_VERSION

    const val DEFAULT_CHUNK_BYTES = 64 * 1024
    const val MIN_CHUNK_BYTES = 16
    const val MAX_CHUNK_BYTES = 1024 * 1024
    val CHUNK_SIZE_RANGE = MIN_CHUNK_BYTES..MAX_CHUNK_BYTES

    /** A page of [com.lcdcode.moodcairns.data.dao.JournalSql.MAX_BODY_CHARS] is far below. */
    const val MAX_RECORD_BYTES = 2 * 1024 * 1024
    const val END_OF_RECORDS = 0

    const val FILE_EXTENSION = "mcj"
    const val MIME_TYPE = "application/octet-stream"
}

/** First record: identifies the file. Fields are nullable so a wrong file reports cleanly. */
@Serializable
internal data class JournalBackupMeta(
    val kind: String? = null,
    val journalVersion: Int? = null,
    val exportedAt: String? = null,
    val appVersion: String? = null,
)

/** One page. [day] is ISO "YYYY-MM-DD"; timestamps are ISO-8601 instants. */
@Serializable
data class JournalDayDto(
    val day: String,
    val createdAt: String,
    val updatedAt: String,
    val body: String,
)
