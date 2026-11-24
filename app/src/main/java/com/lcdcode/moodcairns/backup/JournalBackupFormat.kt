package com.lcdcode.moodcairns.backup

import kotlinx.serialization.Serializable
import java.time.LocalDate

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
 *   (pages strictly oldest first, so a restore detects repeats without remembering days)
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

    /** No page is dated before this; the app's date pickers start here too. */
    val EARLIEST_DAY: LocalDate = LocalDate.of(1900, 1, 1)

    /**
     * Pages may be dated slightly after the restoring device's today: the writing device can
     * be up to 26 hours ahead (UTC+14 vs UTC-12), and clocks drift.
     */
    const val FUTURE_GRACE_DAYS = 2L

    /**
     * Days a backup may hold. Pages are written oldest first with no repeats, so together
     * with this range it also caps the page count.
     */
    fun allowedDays(today: LocalDate): ClosedRange<LocalDate> =
        EARLIEST_DAY..today.plusDays(FUTURE_GRACE_DAYS)
}

/**
 * Size ceilings shared by export and restore, so the app never writes a backup it would
 * refuse. [maxPlaintextBytes] bounds the decompressed record stream, which also bounds the
 * text written to the database. [maxFileBytes] bounds the bytes read from the file, which the
 * plaintext cap alone does not: deflate can consume input while producing nothing.
 */
internal data class JournalBackupLimits(val maxPlaintextBytes: Long, val maxFileBytes: Long) {

    val maxMegabytes: Long get() = maxPlaintextBytes / BYTES_PER_MEGABYTE

    companion object {
        private const val BYTES_PER_MEGABYTE = 1024L * 1024L
        private const val MAX_PLAINTEXT_MEGABYTES = 256L

        /**
         * Compressing and encrypting incompressible text adds well under 1% (deflate block
         * headers, one GCM tag per chunk); 1/64 plus a fixed allowance for the header, zlib
         * framing and the final chunk leaves a wide margin.
         */
        private const val FILE_OVERHEAD_DIVISOR = 64L
        private const val FILE_OVERHEAD_FIXED_BYTES = 4L * 1024L

        val DEFAULT = forPlaintext(MAX_PLAINTEXT_MEGABYTES * BYTES_PER_MEGABYTE)

        fun forPlaintext(maxPlaintextBytes: Long) = JournalBackupLimits(
            maxPlaintextBytes = maxPlaintextBytes,
            maxFileBytes = maxPlaintextBytes + maxPlaintextBytes / FILE_OVERHEAD_DIVISOR +
                FILE_OVERHEAD_FIXED_BYTES,
        )
    }
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
