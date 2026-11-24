package com.lcdcode.moodcairns.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ByteLimitsTest {

    /** A provider that never reaches end of stream. */
    private class EndlessStream : InputStream() {
        override fun read(): Int = 0
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            b.fill(0, off, off + len)
            return len
        }
    }

    private val tooLarge = ImportError.TooLarge(maxMegabytes = 1)

    private fun limited(input: InputStream, max: Long) = LimitedInputStream(input, max) { tooLarge }

    @Test(timeout = 5_000)
    fun `an endless stream is cut off just past the limit`() {
        val stream = limited(EndlessStream(), max = 10_000)
        val thrown = assertThrows(BackupImportException::class.java) { stream.readBytes() }
        assertEquals(tooLarge, thrown.error)
    }

    @Test
    fun `exactly the limit reads through`() {
        val data = ByteArray(4096) { it.toByte() }
        assertArrayEquals(data, limited(ByteArrayInputStream(data), max = 4096).readBytes())
    }

    @Test
    fun `single-byte reads and skips count too`() {
        val stream = limited(ByteArrayInputStream(ByteArray(10)), max = 3)
        stream.read()
        stream.skip(2)
        assertThrows(BackupImportException::class.java) { stream.read() }
    }

    @Test
    fun `mark is refused so nothing is read twice uncounted`() {
        assertFalse(limited(ByteArrayInputStream(ByteArray(1)), max = 1).markSupported())
    }

    @Test
    fun `output stops before the limit is crossed`() {
        val sink = ByteArrayOutputStream()
        val out = LimitedOutputStream(sink, maxBytes = 5, maxMegabytes = 7)
        out.write(ByteArray(4))
        out.write(1)
        val thrown = assertThrows(BackupTooLargeException::class.java) { out.write(ByteArray(1)) }
        assertEquals(7, thrown.maxMegabytes)
        assertEquals(5, sink.size()) // the refused write never reached the sink
    }
}
