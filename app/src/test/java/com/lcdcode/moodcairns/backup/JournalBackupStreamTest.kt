package com.lcdcode.moodcairns.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
    fun `pages round trip in order with every character intact`() {
        val pages = listOf(
            page(1),
            page(2, "Сегодня **хорошо** 😀"),
            page(3, "| a | b |\n|---|---|\n| 1 | 2 |"),
        )
        assertEquals(pages, readAll(backup(pages)))
    }

    @Test
    fun `an empty journal round trips`() {
        assertEquals(emptyList<JournalDayDto>(), readAll(backup(emptyList())))
    }

    @Test
    fun `many pages spanning many chunks round trip`() {
        val pages = (1..28).map { page(it, "entry $it ".repeat(it * 7)) }
        assertEquals(pages, readAll(backup(pages)))
    }

    @Test(timeout = 30_000)
    fun `a multi-megabyte journal streams through`() {
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
    fun `the metadata record is read first`() {
        open(backup(listOf(page(1)))).use { assertEquals(meta, it.meta) }
    }

    @Test
    fun `nextPage keeps returning null after the end`() {
        open(backup(emptyList())).use {
            assertNull(it.nextPage())
            assertNull(it.nextPage())
            it.requireComplete()
        }
    }

    @Test
    fun `journal text is compressed before encryption`() {
        val pages = (1..20).map { page(it, "Today I went for a walk and felt calm. ".repeat(40)) }
        val plainSize = pages.sumOf { it.body.length }
        assertTrue(backup(pages).size < plainSize / 4)
    }

    // Wrong file, wrong secret, damage

    @Test
    fun `a wrong passphrase is reported before any page is read`() {
        val file = backup(listOf(page(1)))
        assertEquals(ImportError.WrongSecretOrCorrupt, failure { readAll(file, secret = "wrong") })
    }

    @Test
    fun `a mood backup or an empty file is not a journal backup`() {
        val moodEnvelope = "{\"envelope\":1,\"kdf\":\"PBKDF2WithHmacSHA256\"}".toByteArray()
        assertEquals(ImportError.NotAJournalBackup, failure { readAll(moodEnvelope) })
        assertEquals(ImportError.NotAJournalBackup, failure { readAll(ByteArray(0)) })
    }

    @Test
    fun `a newer container version is unsupported`() {
        val file = backup(listOf(page(1))).also { it[VERSION_OFFSET] = 2 }
        assertEquals(ImportError.UnsupportedVersion(2), failure { readAll(file) })
    }

    @Test(timeout = 5_000)
    fun `an absurd iteration count is refused before key derivation`() {
        val file = withHeaderInt(backup(listOf(page(1))), ITERATIONS_OFFSET, Int.MAX_VALUE)
        assertTrue(failure { readAll(file) } is ImportError.Malformed)
    }

    @Test
    fun `an out of range chunk size is refused`() {
        val file = withHeaderInt(backup(listOf(page(1))), CHUNK_SIZE_OFFSET, Int.MAX_VALUE)
        assertTrue(failure { readAll(file) } is ImportError.Malformed)
    }

    @Test
    fun `editing the salt or nonce prefix breaks decryption`() {
        for (offset in listOf(SALT_OFFSET, PREFIX_OFFSET)) {
            val file = backup(listOf(page(1)))
            file[offset] = (file[offset].toInt() xor 1).toByte()
            assertEquals(ImportError.WrongSecretOrCorrupt, failure { readAll(file) })
        }
    }

    @Test
    fun `a truncated file is refused`() {
        val file = backup((1..10).map { page(it) })
        for (keep in listOf(file.size - 1, file.size - 40, JournalStreamHeader.HEADER_BYTES + 3)) {
            failure { readAll(file.copyOf(keep)) } // must throw, never return partial pages
        }
    }

    @Test
    fun `an export interrupted before finishing is refused`() {
        val file = backup(emptyList()) { w ->
            w.writeMeta(meta)
            w.writePage(page(1))
            // No finish(): the last chunk is never sealed.
        }
        failure { readAll(file) }
    }

    @Test
    fun `a record over the size limit is refused`() {
        val file = backup(emptyList()) { w ->
            w.writeMeta(meta)
            w.writeRecord("x".repeat(JournalBackupFormat.MAX_RECORD_BYTES + 1))
            w.finish()
        }
        assertTrue(failure { readAll(file) } is ImportError.Malformed)
    }

    @Test
    fun `a record that is not a page is malformed`() {
        val file = backup(emptyList()) { w ->
            w.writeMeta(meta)
            w.writeRecord("not json")
            w.finish()
        }
        assertTrue(failure { readAll(file) } is ImportError.Malformed)
    }

    @Test
    fun `metadata of another kind or a newer version is refused`() {
        val otherKind = backup(listOf(page(1)), fileMeta = meta.copy(kind = "something-else"))
        assertEquals(ImportError.NotAJournalBackup, failure { readAll(otherKind) })
        val newer = backup(listOf(page(1)), fileMeta = meta.copy(journalVersion = 2))
        assertEquals(ImportError.UnsupportedVersion(2), failure { readAll(newer) })
        val noVersion = backup(listOf(page(1)), fileMeta = meta.copy(journalVersion = null))
        assertTrue(failure { readAll(noVersion) } is ImportError.Malformed)
    }

    // Cross-kind safety

    @Test
    fun `the mood import names a journal backup instead of failing vaguely`() {
        // Exactly how BackupFileReader.readText decodes a picked file.
        val raw = String(backup(listOf(page(1))), Charsets.UTF_8)
        val codec = EnvelopeCodec(Json { ignoreUnknownKeys = true })
        assertEquals(ImportError.JournalFile, failure { codec.decrypt(raw, "x".toCharArray()) })
    }

    @Test
    fun `older apps cannot read a journal backup as a mood backup envelope`() {
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
