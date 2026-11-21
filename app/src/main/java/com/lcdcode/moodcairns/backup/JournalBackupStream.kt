package com.lcdcode.moodcairns.backup

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import java.util.zip.ZipException
import javax.crypto.spec.SecretKeySpec

private val recordJson = Json { ignoreUnknownKeys = true }
private const val ZIP_BUFFER_BYTES = 8 * 1024
private const val AES = "AES"

/** The plaintext file header; its exact bytes are the associated data of every chunk. */
internal class JournalStreamHeader(
    val iterations: Int,
    val salt: ByteArray,
    val noncePrefix: ByteArray,
    val chunkSize: Int,
) {
    fun toBytes(): ByteArray = ByteArrayOutputStream(HEADER_BYTES).also { bytes ->
        DataOutputStream(bytes).apply {
            write(JournalBackupFormat.MAGIC)
            writeByte(JournalBackupFormat.FORMAT_VERSION.toInt())
            writeByte(JournalBackupFormat.KDF_PBKDF2_SHA256.toInt())
            writeInt(iterations)
            write(salt)
            write(noncePrefix)
            writeInt(chunkSize)
        }
    }.toByteArray()

    companion object {
        val HEADER_BYTES = JournalBackupFormat.MAGIC.size + 2 + Int.SIZE_BYTES +
            BackupCrypto.SALT_BYTES + ChunkedAead.NONCE_PREFIX_BYTES + Int.SIZE_BYTES

        /** Reads and checks a header; nothing expensive happens before it is validated. */
        fun read(input: InputStream): JournalStreamHeader {
            val data = DataInputStream(input)
            val magic = ByteArray(JournalBackupFormat.MAGIC.size)
            if (!readExactly(data, magic) || !magic.contentEquals(JournalBackupFormat.MAGIC)) {
                fail(ImportError.NotAJournalBackup)
            }
            val version = guarded { data.readByte() }
            if (version != JournalBackupFormat.FORMAT_VERSION) {
                fail(ImportError.UnsupportedVersion(version.toInt()))
            }
            if (guarded { data.readByte() } != JournalBackupFormat.KDF_PBKDF2_SHA256) {
                fail(ImportError.Malformed("Unsupported journal backup KDF"))
            }
            val iterations = guarded { data.readInt() }
            if (iterations !in 1..EnvelopeCodec.MAX_KDF_ITERATIONS) {
                fail(ImportError.Malformed("Backup KDF iteration count $iterations out of range"))
            }
            val salt = ByteArray(BackupCrypto.SALT_BYTES).also { guarded { data.readFully(it) } }
            val prefix =
                ByteArray(ChunkedAead.NONCE_PREFIX_BYTES).also { guarded { data.readFully(it) } }
            val chunkSize = guarded { data.readInt() }
            if (chunkSize !in JournalBackupFormat.CHUNK_SIZE_RANGE) {
                fail(ImportError.Malformed("Journal backup chunk size $chunkSize out of range"))
            }
            return JournalStreamHeader(iterations, salt, prefix, chunkSize)
        }

        private fun readExactly(data: DataInputStream, target: ByteArray): Boolean = try {
            data.readFully(target)
            true
        } catch (_: EOFException) {
            false
        }
    }
}

/**
 * Writes an encrypted journal backup to [out] one page at a time. Call [finish] after the
 * last page; [close] only releases native resources and never produces a valid file.
 * Does not close [out].
 */
internal class JournalBackupWriter(
    out: OutputStream,
    passphrase: CharArray,
    iterations: Int = BackupCrypto.PBKDF2_ITERATIONS,
    chunkSize: Int = JournalBackupFormat.DEFAULT_CHUNK_BYTES,
) : Closeable {
    private val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
    private val records: DataOutputStream

    init {
        val random = SecureRandom()
        val header = JournalStreamHeader(
            iterations = iterations,
            salt = BackupCrypto.newSalt(),
            noncePrefix = ByteArray(ChunkedAead.NONCE_PREFIX_BYTES).also(random::nextBytes),
            chunkSize = chunkSize,
        )
        val headerBytes = header.toBytes()
        out.write(headerBytes)
        val chunks = ChunkEncryptingOutputStream(
            out = out,
            key = deriveAesKey(passphrase, header.salt, iterations),
            header = headerBytes,
            noncePrefix = header.noncePrefix,
            chunkSize = chunkSize,
        )
        records = DataOutputStream(DeflaterOutputStream(chunks, deflater, ZIP_BUFFER_BYTES))
    }

    fun writeMeta(meta: JournalBackupMeta) =
        writeRecord(recordJson.encodeToString(JournalBackupMeta.serializer(), meta))

    fun writePage(page: JournalDayDto) =
        writeRecord(recordJson.encodeToString(JournalDayDto.serializer(), page))

    /** Writes the end marker, flushes the compressor and seals the last chunk. */
    fun finish() {
        records.writeInt(JournalBackupFormat.END_OF_RECORDS)
        records.close()
    }

    override fun close() = deflater.end()

    /** Exposed for tests that need to build deliberately invalid files. */
    internal fun writeRecord(json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        records.writeInt(bytes.size)
        records.write(bytes)
    }
}

/**
 * Reads an encrypted journal backup page by page. Construction validates the header,
 * derives the key and reads the metadata record, so a wrong passphrase or wrong file is
 * reported before anything else happens. Every failure throws [BackupImportException].
 */
internal class JournalBackupReader(input: InputStream, secret: CharArray) : Closeable {
    private val inflater = Inflater()
    private val chunks: ChunkDecryptingInputStream
    private val records: DataInputStream
    private var ended = false

    val meta: JournalBackupMeta

    init {
        val buffered = if (input.markSupported()) input else BufferedInputStream(input)
        val header = JournalStreamHeader.read(buffered)
        chunks = ChunkDecryptingInputStream(
            input = buffered,
            key = deriveAesKey(secret, header.salt, header.iterations),
            header = header.toBytes(),
            noncePrefix = header.noncePrefix,
            chunkSize = header.chunkSize,
        )
        records = DataInputStream(InflaterInputStream(chunks, inflater, ZIP_BUFFER_BYTES))
        meta = readMeta()
    }

    /** The next page, or null after the last one. */
    fun nextPage(): JournalDayDto? {
        if (ended) return null
        val record = readRecord()
        if (record == null) {
            ended = true
            return null
        }
        return decode(record, JournalDayDto.serializer())
    }

    /**
     * Confirms the file ended exactly after the end marker, in its authenticated last chunk.
     * Call after [nextPage] has returned null.
     */
    fun requireComplete() {
        check(ended) { "Read every page before checking completeness" }
        val moreRecords = guarded { records.read() } != END_OF_STREAM
        // Draining the chunk stream decrypts any chunk the decompressor did not need, which
        // is what proves the authenticated last chunk was present.
        val moreChunks = guarded { chunks.read() } != END_OF_STREAM
        if (moreRecords || moreChunks) {
            fail(ImportError.Malformed("Unexpected data after the last page"))
        }
        if (!chunks.isComplete) fail(ImportError.Malformed("Journal backup is cut off"))
    }

    override fun close() = inflater.end()

    private fun readMeta(): JournalBackupMeta {
        val record = readRecord() ?: fail(ImportError.Malformed("Journal backup has no header"))
        val meta = decode(record, JournalBackupMeta.serializer())
        if (meta.kind != JournalBackupFormat.KIND) fail(ImportError.NotAJournalBackup)
        val version = meta.journalVersion
            ?: fail(ImportError.Malformed("Journal backup is missing journalVersion"))
        if (version !in JournalBackupFormat.SUPPORTED_JOURNAL_VERSIONS) {
            fail(ImportError.UnsupportedVersion(version))
        }
        return meta
    }

    private fun readRecord(): String? {
        val length = guarded { records.readInt() }
        if (length == JournalBackupFormat.END_OF_RECORDS) return null
        if (length !in 1..JournalBackupFormat.MAX_RECORD_BYTES) {
            fail(ImportError.Malformed("Journal backup record of $length bytes"))
        }
        val bytes = ByteArray(length).also { guarded { records.readFully(it) } }
        return String(bytes, Charsets.UTF_8)
    }

    private fun <T> decode(record: String, serializer: KSerializer<T>): T = try {
        recordJson.decodeFromString(serializer, record)
    } catch (e: SerializationException) {
        fail(ImportError.Malformed(e.message ?: "Unreadable journal backup record"))
    } catch (e: IllegalArgumentException) {
        fail(ImportError.Malformed(e.message ?: "Unreadable journal backup record"))
    }

    private companion object {
        const val END_OF_STREAM = -1
    }
}

private fun deriveAesKey(secret: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
    val raw = BackupCrypto.deriveKey(secret, salt, iterations)
    return try {
        SecretKeySpec(raw, AES) // copies the bytes
    } finally {
        raw.fill(0)
    }
}

/**
 * Runs a stream read, mapping a truncated or corrupt stream to [ImportError.Malformed] and
 * any other I/O failure to [ImportError.Unreadable].
 */
private inline fun <T> guarded(read: () -> T): T = try {
    read()
} catch (_: EOFException) {
    fail(ImportError.Malformed("Journal backup ended early"))
} catch (e: ZipException) {
    fail(ImportError.Malformed(e.message ?: "Journal backup data is damaged"))
} catch (e: IOException) {
    fail(ImportError.Unreadable(e.message))
}

private fun fail(error: ImportError): Nothing = throw BackupImportException(error)
