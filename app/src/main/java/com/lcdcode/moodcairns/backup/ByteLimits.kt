package com.lcdcode.moodcairns.backup

import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Counts the bytes read through it and throws [BackupImportException] with [exceeded] once
 * more than [maxBytes] have passed. Mark and reset are unsupported so nothing is counted
 * twice or skipped; wrap this in a BufferedInputStream when they are needed.
 */
internal class LimitedInputStream(
    input: InputStream,
    private val maxBytes: Long,
    private val exceeded: () -> ImportError,
) : FilterInputStream(input) {
    private var count = 0L

    override fun read(): Int = super.read().also { if (it >= 0) consume(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int =
        super.read(b, off, len).also { if (it > 0) consume(it.toLong()) }

    override fun skip(n: Long): Long = super.skip(n).also { consume(it) }

    override fun markSupported() = false

    override fun mark(readlimit: Int) = Unit

    override fun reset() = throw IOException("mark/reset not supported")

    private fun consume(n: Long) {
        count += n
        if (count > maxBytes) throw BackupImportException(exceeded())
    }
}

/**
 * Counts the bytes written through it and throws [BackupTooLargeException] before more than
 * [maxBytes] would reach [out].
 */
internal class LimitedOutputStream(
    out: OutputStream,
    private val maxBytes: Long,
    private val maxMegabytes: Long,
) : FilterOutputStream(out) {
    private var count = 0L

    override fun write(b: Int) {
        reserve(1)
        out.write(b)
    }

    // FilterOutputStream writes arrays one byte at a time; pass them through whole.
    override fun write(b: ByteArray, off: Int, len: Int) {
        reserve(len.toLong())
        out.write(b, off, len)
    }

    private fun reserve(n: Long) {
        if (count + n > maxBytes) throw BackupTooLargeException(maxMegabytes)
        count += n
    }
}
