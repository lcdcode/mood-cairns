package com.lcdcode.moodcairns.backup

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Streaming authenticated encryption (the STREAM construction, as used by age and Tink):
 * the plaintext is cut into fixed-size chunks, each sealed with AES-256-GCM under its own
 * nonce = 7-byte random prefix || 4-byte chunk counter || 1-byte "last chunk" flag, with
 * the file header as associated data. Reordering, dropping, duplicating or cutting off
 * chunks, or editing the header, makes authentication fail. Memory stays at one chunk no
 * matter how long the stream is.
 */
internal object ChunkedAead {
    const val NONCE_PREFIX_BYTES = 7
    const val TAG_BYTES = BackupCrypto.TAG_BITS / Byte.SIZE_BITS
    private const val NONCE_BYTES = BackupCrypto.IV_BYTES
    private const val LAST_CHUNK: Byte = 1
    private const val MIDDLE_CHUNK: Byte = 0

    fun nonce(prefix: ByteArray, counter: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(NONCE_BYTES)
            .put(prefix)
            .putInt(counter)
            .put(if (last) LAST_CHUNK else MIDDLE_CHUNK)
            .array()

    fun cipher(mode: Int, key: SecretKeySpec, nonce: ByteArray, header: ByteArray): Cipher =
        Cipher.getInstance(BackupCrypto.CIPHER).apply {
            init(mode, key, GCMParameterSpec(BackupCrypto.TAG_BITS, nonce))
            updateAAD(header)
        }
}

/**
 * Encrypts everything written to it onto [out] in [chunkSize] chunks. [close] seals the
 * final chunk (possibly empty) with the last-chunk flag; it does not close [out].
 */
internal class ChunkEncryptingOutputStream(
    private val out: OutputStream,
    private val key: SecretKeySpec,
    private val header: ByteArray,
    private val noncePrefix: ByteArray,
    chunkSize: Int,
) : OutputStream() {
    private val buffer = ByteArray(chunkSize)
    private var filled = 0
    private var counter = 0
    private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        check(!closed) { "Stream already closed" }
        var offset = off
        var remaining = len
        while (remaining > 0) {
            // A full buffer is only sealed once more data arrives, so it is never mistaken
            // for the last chunk.
            if (filled == buffer.size) seal(last = false)
            val n = minOf(remaining, buffer.size - filled)
            System.arraycopy(b, offset, buffer, filled, n)
            filled += n
            offset += n
            remaining -= n
        }
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        seal(last = true)
        closed = true
        out.flush()
    }

    private fun seal(last: Boolean) {
        check(counter != Int.MAX_VALUE) { "Journal backup has too many chunks" }
        val nonce = ChunkedAead.nonce(noncePrefix, counter, last)
        val cipher = ChunkedAead.cipher(Cipher.ENCRYPT_MODE, key, nonce, header)
        out.write(cipher.doFinal(buffer, 0, filled))
        filled = 0
        counter++
    }
}

/**
 * Decrypts a stream written by [ChunkEncryptingOutputStream]. [input] must support mark and
 * reset (wrap it in a BufferedInputStream) so the end of the stream can be detected, which
 * is what tells the last chunk apart. Throws [BackupImportException] with
 * [ImportError.WrongSecretOrCorrupt] when any chunk fails authentication, and with
 * [ImportError.Malformed] when the stream ends before its last chunk.
 */
internal class ChunkDecryptingInputStream(
    private val input: InputStream,
    private val key: SecretKeySpec,
    private val header: ByteArray,
    private val noncePrefix: ByteArray,
    chunkSize: Int,
) : InputStream() {
    private val segment = ByteArray(chunkSize + ChunkedAead.TAG_BYTES)
    private var plain = ByteArray(0)
    private var position = 0
    private var counter = 0
    private var done = false

    init {
        require(input.markSupported()) { "Input must support mark/reset" }
    }

    override fun read(): Int {
        if (!ensureAvailable()) return END_OF_STREAM
        return plain[position++].toInt() and BYTE_MASK
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!ensureAvailable()) return END_OF_STREAM
        val n = minOf(len, plain.size - position)
        System.arraycopy(plain, position, b, off, n)
        position += n
        return n
    }

    /** True once the authenticated last chunk has been decrypted and fully read. */
    val isComplete: Boolean get() = done && position >= plain.size

    private fun ensureAvailable(): Boolean {
        while (position >= plain.size) {
            if (done) return false
            openNextChunk()
        }
        return true
    }

    private fun openNextChunk() {
        val n = readFully(segment)
        if (n < ChunkedAead.TAG_BYTES) fail(ImportError.Malformed(MalformedReason.CUT_OFF))
        val last = n < segment.size || atEndOfInput()
        val nonce = ChunkedAead.nonce(noncePrefix, counter, last)
        plain = try {
            ChunkedAead.cipher(Cipher.DECRYPT_MODE, key, nonce, header).doFinal(segment, 0, n)
        } catch (_: GeneralSecurityException) {
            fail(ImportError.WrongSecretOrCorrupt)
        }
        position = 0
        counter++
        done = last
    }

    private fun atEndOfInput(): Boolean {
        input.mark(1)
        val next = input.read()
        input.reset()
        return next == END_OF_STREAM
    }

    private fun readFully(target: ByteArray): Int {
        var total = 0
        while (total < target.size) {
            val n = input.read(target, total, target.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private companion object {
        const val END_OF_STREAM = -1
        const val BYTE_MASK = 0xFF
    }
}

private fun fail(error: ImportError): Nothing = throw BackupImportException(error)
