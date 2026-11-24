package com.lcdcode.moodcairns.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** The whole journal backup format: header, compression, records and their validation. */
class JournalBackupStreamTest {

    private val passphrase = "journal passphrase"
    private val meta = JournalBackupMeta(
        kind = JournalBackupFormat.KIND,
        journalVersion = JournalBackupFormat.JOURNAL_VERSION,
        exportedAt = "2026-11-21T10:00:00Z",
        appVersion = "test",
    )

    private fun page(day: Int, body: String = "# Day $day\n- [x] wrote") = JournalDayDto(
        day = "2026-11-%02d".format(day),
        createdAt = "2026-11-01T08:00:00Z",
        updatedAt = "2026-11-01T09:00:00Z",
        body = body,
    )

    private fun backup(
        pages: List<JournalDayDto>,
        fileMeta: JournalBackupMeta = meta,
        build: (JournalBackupWriter) -> Unit = { w ->
            w.writeMeta(fileMeta)
            pages.forEach(w::writePage)
            w.finish()
        },
    ): ByteArray {
        val out = ByteArrayOutputStream()
        JournalBackupWriter(out, passphrase.toCharArray(), FAST_ITERATIONS, SMALL_CHUNK)
            .use(build)
        return out.toByteArray()
    }

    private fun readAll(file: ByteArray, secret: String = passphrase): List<JournalDayDto> =
        JournalBackupReader(ByteArrayInputStream(file), secret.toCharArray()).use { reader ->
            generateSequence { reader.nextPage() }.toList().also { reader.requireComplete() }
        }

    private fun open(file: ByteArray) =
        JournalBackupReader(ByteArrayInputStream(file), passphrase.toCharArray())

    private fun failure(block: () -> Unit): ImportError =
        assertThrows(BackupImportException::class.java, block).error

    private fun withHeaderInt(file: ByteArray, offset: Int, value: Int) =
        file.copyOf().also { ByteBuffer.wrap(it).putInt(offset, value) }

    @Test
    fun pages_round_trip_in_order_with_every_character_intact() {
        val pages = listOf(
            page(1),
            page(2, "Сегодня **хорошо** 😀"),
            page(3, "| a | b |\n|---|---|\n| 1 | 2 |"),
        )
        assertEquals(pages, readAll(backup(pages)))
    }

    @Test
    fun an_empty_journal_round_trips() {
        assertEquals(emptyList<JournalDayDto>(), readAll(backup(emptyList())))
    }

    @Test
    fun many_pages_spanning_many_chunks_round_trip() {
        val pages = (1..28).map { page(it, "entry $it ".repeat(it * 7)) }
        assertEquals(pages, readAll(backup(pages)))
    }

    @Test(timeout = 30_000)
    fun a_multi_megabyte_journal_streams_through() {
        val pages = (1..28).map { day ->
            page(day, (1..7_000).joinToString(" ") { "word$it-$day" }) // ~75 KB, ~2 MB total
        }
        val file = ByteArrayOutputStream().also { out ->
            JournalBackupWriter(out, passphrase.toCharArray(), FAST_ITERATIONS).use { w ->
                w.writeMeta(meta)
                pages.forEach(w::writePage)
                w.finish()
            }
        }.toByteArray()
        assertEquals(pages, readAll(file))
    }

    @Test
    fun the_metadata_record_is_read_first() {
        open(backup(listOf(page(1)))).use { assertEquals(meta, it.meta) }
    }

    @Test
    fun nextpage_keeps_returning_null_after_the_end() {
        open(backup(emptyList())).use {
            assertNull(it.nextPage())
            assertNull(it.nextPage())
            it.requireComplete()
        }
    }

    @Test
    fun journal_text_is_compressed_before_encryption() {
        val pages = (1..20).map { page(it, "Today I went for a walk and felt calm. ".repeat(40)) }
        val plainSize = pages.sumOf { it.body.length }
        assertTrue(backup(pages).size < plainSize / 4)
    }

    // Wrong file, wrong secret, damage

    @Test
    fun a_wrong_passphrase_is_reported_before_any_page_is_read() {
        val file = backup(listOf(page(1)))
        assertEquals(ImportError.WrongSecretOrCorrupt, failure { readAll(file, secret = "wrong") })
    }

    @Test
    fun a_mood_backup_or_an_empty_file_is_not_a_journal_backup() {
        val moodEnvelope = "{\"envelope\":1,\"kdf\":\"PBKDF2WithHmacSHA256\"}".toByteArray()
        assertEquals(ImportError.NotAJournalBackup, failure { readAll(moodEnvelope) })
        assertEquals(ImportError.NotAJournalBackup, failure { readAll(ByteArray(0)) })
    }

    @Test
    fun a_newer_container_version_is_unsupported() {
        val file = backup(listOf(page(1))).also { it[VERSION_OFFSET] = 2 }
        assertEquals(ImportError.UnsupportedVersion(2), failure { readAll(file) })
    }

    @Test(timeout = 5_000)
    fun an_absurd_iteration_count_is_refused_before_key_derivation() {
        val file = withHeaderInt(backup(listOf(page(1))), ITERATIONS_OFFSET, Int.MAX_VALUE)
        assertTrue(failure { readAll(file) } is ImportError.Malformed)
    }

    @Test
    fun an_out_of_range_chunk_size_is_refused() {
        val file = withHeaderInt(backup(listOf(page(1))), CHUNK_SIZE_OFFSET, Int.MAX_VALUE)
        assertTrue(failure { readAll(file) } is ImportError.Malformed)
    }

    @Test
    fun editing_the_salt_or_nonce_prefix_breaks_decryption() {
        for (offset in listOf(SALT_OFFSET, PREFIX_OFFSET)) {
            val file = backup(listOf(page(1)))
            file[offset] = (file[offset].toInt() xor 1).toByte()
            assertEquals(ImportError.WrongSecretOrCorrupt, failure { readAll(file) })
        }
    }

    @Test
    fun a_truncated_file_is_refused() {
        val file = backup((1..10).map { page(it) })
        for (keep in listOf(file.size - 1, file.size - 40, JournalStreamHeader.HEADER_BYTES + 3)) {
            failure { readAll(file.copyOf(keep)) } // must throw, never return partial pages
        }
    }

    @Test
    fun an_export_interrupted_before_finishing_is_refused() {
        val file = backup(emptyList()) { w ->
            w.writeMeta(meta)
            w.writePage(page(1))
            // No finish(): the last chunk is never sealed.
        }
        failure { readAll(file) }
    }

    @Test
    fun a_record_over_the_size_limit_is_refused() {
        val file = backup(emptyList()) { w ->
            w.writeMeta(meta)
            w.writeRecord("x".repeat(JournalBackupFormat.MAX_RECORD_BYTES + 1))
            w.finish()
        }
        assertTrue(failure { readAll(file) } is ImportError.Malformed)
    }

    @Test
    fun a_record_that_is_not_a_page_is_malformed() {
        val file = backup(emptyList()) { w ->
            w.writeMeta(meta)
            w.writeRecord("not json")
            w.finish()
        }
        val error = failure { readAll(file) }
        assertEquals(ImportError.Malformed(MalformedReason.RECORD_INVALID), error)
    }

    @Test
    fun a_broken_records_text_never_reaches_the_error() {
        // Decoder messages quote the input; the page text must not leak into logs or the UI.
        val file = backup(emptyList()) { w ->
            w.writeMeta(meta)
            w.writeRecord("{\"day\": \"" + "secret page\n\u202E".repeat(5_000))
            w.finish()
        }
        val thrown = assertThrows(BackupImportException::class.java) { readAll(file) }
        assertEquals(ImportError.Malformed(MalformedReason.RECORD_INVALID), thrown.error)
        assertFalse(thrown.message.orEmpty().contains("secret"))
    }

    @Test
    fun metadata_of_another_kind_or_a_newer_version_is_refused() {
        val otherKind = backup(listOf(page(1)), fileMeta = meta.copy(kind = "something-else"))
        assertEquals(ImportError.NotAJournalBackup, failure { readAll(otherKind) })
        val newer = backup(listOf(page(1)), fileMeta = meta.copy(journalVersion = 2))
        assertEquals(ImportError.UnsupportedVersion(2), failure { readAll(newer) })
        val noVersion = backup(listOf(page(1)), fileMeta = meta.copy(journalVersion = null))
        assertTrue(failure { readAll(noVersion) } is ImportError.Malformed)
    }

    // Size limits shared by export and restore

    /** Decompressed bytes of a backup: length-prefixed records plus the end marker. */
    private fun plaintextSize(pages: List<JournalDayDto>): Long {
        val json = Json
        val records = listOf(json.encodeToString(JournalBackupMeta.serializer(), meta)) +
            pages.map { json.encodeToString(JournalDayDto.serializer(), it) }
        return records.sumOf { Int.SIZE_BYTES + it.toByteArray(Charsets.UTF_8).size }.toLong() +
            Int.SIZE_BYTES
    }

    private fun backupWithin(limits: JournalBackupLimits, pages: List<JournalDayDto>) =
        ByteArrayOutputStream().also { out ->
            JournalBackupWriter(out, passphrase.toCharArray(), FAST_ITERATIONS, SMALL_CHUNK, limits)
                .use { w ->
                    w.writeMeta(meta)
                    pages.forEach(w::writePage)
                    w.finish()
                }
        }.toByteArray()

    private fun readWithin(limits: JournalBackupLimits, file: ByteArray) =
        JournalBackupReader(ByteArrayInputStream(file), passphrase.toCharArray(), limits)
            .use { reader ->
                generateSequence { reader.nextPage() }.toList().also { reader.requireComplete() }
            }

    @Test
    fun a_backup_exactly_at_the_plaintext_limit_is_written_and_restored() {
        val pages = (1..5).map { page(it) }
        val exact = JournalBackupLimits.forPlaintext(plaintextSize(pages))
        assertEquals(pages, readWithin(exact, backupWithin(exact, pages)))
    }

    @Test
    fun export_refuses_one_byte_over_the_plaintext_limit() {
        val pages = (1..5).map { page(it) }
        val tight = JournalBackupLimits.forPlaintext(plaintextSize(pages) - 1)
        assertThrows(BackupTooLargeException::class.java) { backupWithin(tight, pages) }
    }

    @Test
    fun restore_refuses_one_byte_over_the_plaintext_limit() {
        val pages = (1..5).map { page(it) }
        val tight = JournalBackupLimits.forPlaintext(plaintextSize(pages) - 1)
        val error = failure { readWithin(tight, backup(pages)) }
        assertEquals(ImportError.ContentsTooLarge(tight.maxMegabytes), error)
    }

    @Test
    fun a_small_file_that_inflates_past_the_limit_is_refused_while_streaming() {
        // 2,000 near-empty-compressing pages: a few kilobytes on disk, far more unpacked.
        val pages = (1..2_000).map { page(1, "a".repeat(2_000)) }
        val file = backup(pages)
        val limits = JournalBackupLimits.forPlaintext(512L * 1024)
        assertTrue(file.size < limits.maxPlaintextBytes / 4)
        assertTrue(failure { readWithin(limits, file) } is ImportError.ContentsTooLarge)
    }

    @Test
    fun export_refuses_past_the_file_limit_even_within_the_plaintext_limit() {
        val pages = (1..5).map { page(it) }
        val limits = JournalBackupLimits(maxPlaintextBytes = 1L shl 20, maxFileBytes = 100)
        assertThrows(BackupTooLargeException::class.java) { backupWithin(limits, pages) }
    }

    // Cross-kind safety

    @Test
    fun the_mood_import_names_a_journal_backup_instead_of_failing_vaguely() {
        // Exactly how BackupFileReader.readText decodes a picked file.
        val raw = String(backup(listOf(page(1))), Charsets.UTF_8)
        val codec = EnvelopeCodec(Json { ignoreUnknownKeys = true })
        assertEquals(ImportError.JournalFile, failure { codec.decrypt(raw, "x".toCharArray()) })
    }

    @Test
    fun older_apps_cannot_read_a_journal_backup_as_a_mood_backup_envelope() {
        // Older versions JSON-decode the file as an envelope before doing anything else.
        val raw = String(backup(listOf(page(1))), Charsets.UTF_8)
        assertThrows(SerializationException::class.java) {
            Json { ignoreUnknownKeys = true }.decodeFromString(EncryptedBackup.serializer(), raw)
        }
    }

    private companion object {
        const val FAST_ITERATIONS = 1_000
        const val SMALL_CHUNK = 64
        val VERSION_OFFSET = JournalBackupFormat.MAGIC.size
        val ITERATIONS_OFFSET = VERSION_OFFSET + 2
        val SALT_OFFSET = ITERATIONS_OFFSET + Int.SIZE_BYTES
        val PREFIX_OFFSET = SALT_OFFSET + BackupCrypto.SALT_BYTES
        val CHUNK_SIZE_OFFSET = PREFIX_OFFSET + ChunkedAead.NONCE_PREFIX_BYTES
    }
}
