package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.JournalSql
import com.lcdcode.moodcairns.data.entity.JournalDoc
import com.lcdcode.moodcairns.data.repo.JournalReplaceTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import kotlin.random.Random
import javax.crypto.spec.SecretKeySpec

/**
 * Restores against a journal that already holds a sentinel page: every refusal, crossed
 * limit and cancellation must leave it exactly as it was.
 */
class JournalRestoreTest {

    /** Mirrors the repository's transaction: pages replace the journal only on success. */
    private class FakeJournal(
        initial: List<JournalDoc>,
        private val onInsert: suspend (count: Int) -> Unit = {},
    ) : JournalReplaceTarget {
        @Volatile
        var pages: List<JournalDoc> = initial
            private set

        @Volatile
        var staged = 0
            private set

        override suspend fun replaceAll(
            produce: suspend (insert: suspend (JournalDoc) -> Unit) -> Unit,
        ) {
            val incoming = mutableListOf<JournalDoc>()
            produce { doc ->
                incoming += doc
                staged = incoming.size
                onInsert(incoming.size)
            }
            pages = incoming.toList()
        }
    }

    /**
     * Serves [prefix], then blocks like a provider that stopped answering. Closing it wakes
     * the blocked read with an IOException, as Android does for file descriptors.
     */
    private class StallingStream(private val prefix: ByteArray) : InputStream() {
        private var position = 0
        private val closedSignal = CountDownLatch(1)
        val stalled = CountDownLatch(1)

        @Volatile
        var closed = false
            private set

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) END_OF_STREAM else one[0].toInt() and BYTE_MASK
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (position < prefix.size) {
                val n = minOf(len, prefix.size - position)
                System.arraycopy(prefix, position, b, off, n)
                position += n
                return n
            }
            stalled.countDown()
            closedSignal.await()
            throw IOException("Stream closed")
        }

        override fun close() {
            closed = true
            closedSignal.countDown()
        }
    }

    private val sentinel = listOf(
        JournalDoc(
            day = LocalDate.of(2020, 1, 1),
            createdAt = Instant.parse("2020-01-01T08:00:00Z"),
            updatedAt = Instant.parse("2020-01-01T08:00:00Z"),
            preview = "sentinel",
            body = "sentinel page",
        ),
    )

    private fun page(day: String, body: String = "# $day") = JournalDayDto(
        day = day,
        createdAt = "2026-11-01T08:00:00Z",
        updatedAt = "2026-11-01T09:00:00Z",
        body = body,
    )

    private fun pages(count: Int, body: String = "entry") =
        (0 until count).map { page(FIRST_DAY.plusDays(it.toLong()).toString(), body) }

    private fun incompressible(seed: Int): String =
        Base64.getEncoder().encodeToString(Random(seed).nextBytes(RANDOM_BODY_BYTES))

    private fun backup(pages: List<JournalDayDto>): ByteArray =
        ByteArrayOutputStream().also { out ->
            JournalBackupWriter(out, PASSPHRASE.toCharArray(), FAST_ITERATIONS, SMALL_CHUNK)
                .use { w ->
                    w.writeMeta(META)
                    pages.forEach(w::writePage)
                    w.finish()
                }
        }.toByteArray()

    private fun restore(
        journal: FakeJournal,
        file: ByteArray,
        limits: JournalBackupLimits = JournalBackupLimits.DEFAULT,
    ): Int = runBlocking {
        JournalRestore(journal, limits).restore(
            ByteArrayInputStream(file),
            PASSPHRASE.toCharArray(),
            TODAY,
        )
    }

    private fun assertRefusedUnchanged(
        file: ByteArray,
        limits: JournalBackupLimits = JournalBackupLimits.DEFAULT,
        check: (ImportError) -> Boolean,
    ) {
        val journal = FakeJournal(sentinel)
        val thrown = assertThrows(BackupImportException::class.java) {
            restore(journal, file, limits)
        }
        assertTrue("unexpected ${thrown.error}", check(thrown.error))
        assertEquals(sentinel, journal.pages)
    }

    private fun refusedFor(reason: MalformedReason): (ImportError) -> Boolean =
        { it == ImportError.Malformed(reason) }

    @Test
    fun `a valid backup replaces the journal`() {
        val journal = FakeJournal(sentinel)
        val incoming = pages(3)
        assertEquals(3, restore(journal, backup(incoming)))
        assertEquals(incoming.map { it.day }, journal.pages.map { it.day.toString() })
    }

    // Each incoming limit leaves the sentinel in place

    @Test
    fun `contents over the plaintext limit leave the journal unchanged`() {
        val file = backup(pages(200, body = "a".repeat(2_000)))
        val limits = JournalBackupLimits.forPlaintext(64L * 1024)
        assertRefusedUnchanged(file, limits) { it is ImportError.ContentsTooLarge }
    }

    @Test
    fun `a file one byte over the file limit leaves the journal unchanged`() {
        val file = backup(pages(50))
        val atSize = JournalBackupLimits(
            maxPlaintextBytes = 1L shl 30,
            maxFileBytes = file.size.toLong(),
        )
        assertEquals(50, restore(FakeJournal(sentinel), file, atSize))

        val oneUnder = atSize.copy(maxFileBytes = file.size - 1L)
        assertRefusedUnchanged(file, oneUnder) { it is ImportError.TooLarge }
    }

    @Test(timeout = 30_000)
    fun `input that inflates to nothing is stopped by the file limit`() {
        // Empty deflate blocks consume input without producing output, so the plaintext limit
        // alone would read a stream like this to its end, however long it is.
        val file = emptyBlockFlood(blocks = 200_000)
        val limits = JournalBackupLimits(maxPlaintextBytes = 1L shl 30, maxFileBytes = 64 * 1024)
        assertRefusedUnchanged(file, limits) { it is ImportError.TooLarge }
    }

    @Test
    fun `a page before the earliest day leaves the journal unchanged`() {
        val file = backup(listOf(page("1899-12-31")) + pages(2))
        assertRefusedUnchanged(file, check = refusedFor(MalformedReason.DAY_OUT_OF_RANGE))
    }

    @Test
    fun `a page past the future grace leaves the journal unchanged`() {
        val tooLate = TODAY.plusDays(JournalBackupFormat.FUTURE_GRACE_DAYS + 1).toString()
        val file = backup(pages(2) + page(tooLate))
        assertRefusedUnchanged(file, check = refusedFor(MalformedReason.DAY_OUT_OF_RANGE))
    }

    @Test
    fun `a repeated day leaves the journal unchanged`() {
        val file = backup(pages(3) + pages(3).takeLast(1)) // adjacent, so only the repeat is wrong
        assertRefusedUnchanged(file, check = refusedFor(MalformedReason.DAYS_OUT_OF_ORDER))
    }

    @Test
    fun `pages out of order leave the journal unchanged`() {
        val file = backup(pages(3).reversed())
        assertRefusedUnchanged(file, check = refusedFor(MalformedReason.DAYS_OUT_OF_ORDER))
    }

    @Test
    fun `an over-long page leaves the journal unchanged`() {
        val file = backup(pages(2) + page("2026-11-20", "a".repeat(JournalSql.MAX_BODY_CHARS + 1)))
        assertRefusedUnchanged(file, check = refusedFor(MalformedReason.BODY_TOO_LONG))
    }

    @Test
    fun `a truncated file leaves the journal unchanged`() {
        val file = backup(pages(20))
        assertRefusedUnchanged(file.copyOf(file.size - 1)) { true }
    }

    @Test
    fun `a wrong passphrase leaves the journal unchanged`() {
        val journal = FakeJournal(sentinel)
        assertThrows(BackupImportException::class.java) {
            runBlocking {
                JournalRestore(journal).restore(
                    ByteArrayInputStream(backup(pages(2))),
                    "wrong".toCharArray(),
                    TODAY,
                )
            }
        }
        assertEquals(sentinel, journal.pages)
    }

    // Cancellation

    @Test(timeout = 15_000)
    fun `cancelling a restore stalled on its provider unblocks it and keeps the journal`() {
        // Incompressible pages spread across the file, so the stall lands mid-transaction.
        val file = backup(pages(40).mapIndexed { i, p -> p.copy(body = incompressible(i)) })
        val stream = StallingStream(file.copyOf(file.size * 2 / 3))
        val journal = FakeJournal(sentinel)
        val scope = CoroutineScope(Dispatchers.IO + Job())
        var outcome: Throwable? = null
        val job = scope.launch {
            outcome = runCatching {
                JournalRestore(journal).restore(stream, PASSPHRASE.toCharArray(), TODAY)
            }.exceptionOrNull()
        }

        assertTrue("never stalled", stream.stalled.await(STALL_WAIT_SECONDS, TimeUnit.SECONDS))
        assertTrue("stalled before the transaction began", journal.staged > 0)
        runBlocking {
            job.cancel()
            withTimeout(UNBLOCK_TIMEOUT_MILLIS) { job.join() }
        }

        assertTrue(stream.closed)
        assertTrue("got $outcome", outcome is CancellationException)
        assertEquals(sentinel, journal.pages)
    }

    @Test
    fun `cancellation is checked between pages`() {
        val journal = FakeJournal(sentinel) { count ->
            if (count == 1) currentCoroutineContext().cancel()
        }
        val thrown = runBlocking {
            runCatching {
                JournalRestore(journal).restore(
                    ByteArrayInputStream(backup(pages(10))),
                    PASSPHRASE.toCharArray(),
                    TODAY,
                )
            }.exceptionOrNull()
        }
        assertNotNull(thrown)
        assertTrue("got $thrown", thrown is CancellationException)
        assertEquals(1, journal.staged)
        assertEquals(sentinel, journal.pages)
    }

    // Export uses the same rules, so it never writes what restore refuses

    private fun export(docs: List<JournalDoc>): ByteArray =
        ByteArrayOutputStream().also { out ->
            JournalBackupWriter(out, PASSPHRASE.toCharArray(), FAST_ITERATIONS, SMALL_CHUNK)
                .use { writer ->
                    runBlocking {
                        writeJournal(writer, TODAY) { action -> docs.forEach { action(it) } }
                    }
                }
        }.toByteArray()

    private fun doc(day: String) = JournalDoc(
        day = LocalDate.parse(day),
        createdAt = Instant.parse("2026-11-01T08:00:00Z"),
        updatedAt = Instant.parse("2026-11-01T09:00:00Z"),
        preview = "",
        body = "page $day",
    )

    @Test
    fun `an exported journal restores`() {
        val docs = listOf(doc("1900-01-01"), doc("2026-11-24"), doc("2026-11-26"))
        val journal = FakeJournal(sentinel)
        assertEquals(3, restore(journal, export(docs)))
        assertEquals(docs.map { it.day }, journal.pages.map { it.day })
    }

    @Test
    fun `export refuses a page the restore would refuse, naming its day`() {
        for (day in listOf("1899-12-31", "2026-11-27")) {
            val thrown = assertThrows(UnrestorableJournalPageException::class.java) {
                export(listOf(doc("2026-11-01"), doc(day)))
            }
            assertEquals(LocalDate.parse(day), thrown.day)
            assertEquals(ImportError.Malformed(MalformedReason.DAY_OUT_OF_RANGE), thrown.error)
        }
    }

    /**
     * A valid, authenticated file whose compressed stream holds the metadata record and then
     * [blocks] empty stored deflate blocks: it never yields a page and never ends cleanly.
     */
    private fun emptyBlockFlood(blocks: Int): ByteArray {
        val metaJson = Json.encodeToString(JournalBackupMeta.serializer(), META)
            .toByteArray(Charsets.UTF_8)
        val metaRecord = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(metaJson.size)
                write(metaJson)
            }
        }.toByteArray()

        val compressed = ByteArrayOutputStream()
        val deflater = Deflater()
        try {
            deflater.setInput(metaRecord)
            val buffer = ByteArray(DEFLATE_BUFFER_BYTES)
            // SYNC_FLUSH leaves the output byte-aligned, where a stored block can start.
            val n = deflater.deflate(buffer, 0, buffer.size, Deflater.SYNC_FLUSH)
            compressed.write(buffer, 0, n)
        } finally {
            deflater.end()
        }
        repeat(blocks) { compressed.write(EMPTY_STORED_BLOCK) }

        val salt = BackupCrypto.newSalt()
        val prefix = ByteArray(ChunkedAead.NONCE_PREFIX_BYTES).also(SecureRandom()::nextBytes)
        val header = JournalStreamHeader(FAST_ITERATIONS, salt, prefix, DEFAULT_TEST_CHUNK)
        val headerBytes = header.toBytes()
        val rawKey = BackupCrypto.deriveKey(PASSPHRASE.toCharArray(), salt, FAST_ITERATIONS)
        val file = ByteArrayOutputStream()
        file.write(headerBytes)
        ChunkEncryptingOutputStream(
            out = file,
            key = SecretKeySpec(rawKey, "AES"),
            header = headerBytes,
            noncePrefix = prefix,
            chunkSize = DEFAULT_TEST_CHUNK,
        ).use { it.write(compressed.toByteArray()) }
        return file.toByteArray()
    }

    private companion object {
        const val PASSPHRASE = "journal passphrase"
        const val FAST_ITERATIONS = 1_000
        const val SMALL_CHUNK = 64
        const val DEFAULT_TEST_CHUNK = 4 * 1024
        const val DEFLATE_BUFFER_BYTES = 64 * 1024
        const val RANDOM_BODY_BYTES = 512
        const val STALL_WAIT_SECONDS = 10L
        const val UNBLOCK_TIMEOUT_MILLIS = 5_000L
        const val END_OF_STREAM = -1
        const val BYTE_MASK = 0xFF

        /** BFINAL=0, BTYPE=00 padded to a byte, then LEN=0 and NLEN=0xFFFF. */
        val EMPTY_STORED_BLOCK = byteArrayOf(0, 0, 0, 0xFF.toByte(), 0xFF.toByte())

        val TODAY: LocalDate = LocalDate.of(2026, 11, 24)
        val FIRST_DAY: LocalDate = LocalDate.of(2026, 1, 1)
        val META = JournalBackupMeta(
            kind = JournalBackupFormat.KIND,
            journalVersion = JournalBackupFormat.JOURNAL_VERSION,
            exportedAt = "2026-11-21T10:00:00Z",
            appVersion = "test",
        )
    }
}
