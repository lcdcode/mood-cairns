package com.lcdcode.moodcairns.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Reads a picked backup or configuration file as text, refusing oversized files. */
@Singleton
class BackupFileReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * The UTF-8 text of [uri]. Throws [BackupImportException] when the file is too large
     * or cannot be read. Real backups are well under a megabyte even with thousands of
     * entries; the cap keeps a huge (intentional or accidental) file from exhausting the heap.
     */
    fun readText(uri: Uri): String {
        val knownSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: UNKNOWN_SIZE
        if (BackupSizeLimit.exceeds(knownSize)) throw BackupImportException(tooLarge())

        // Read up to MAX+1 bytes so an overrun shows even when the provider reported no length.
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use {
                it.readCapped(BackupSizeLimit.MAX_BYTES + 1)
            }
        } catch (t: Throwable) {
            throw BackupImportException(ImportError.Unreadable(Diagnostic.of(t)))
        } ?: throw BackupImportException(ImportError.Unreadable(detail = null))

        if (BackupSizeLimit.exceeds(bytes.size.toLong())) throw BackupImportException(tooLarge())
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * A stream over [uri] for formats read incrementally (journal backups). The caller
     * enforces [maxBytes] while reading, since providers may report no size or a wrong one;
     * this only refuses early when the reported size already exceeds it. Throws
     * [BackupImportException] when the file is too large or cannot be opened.
     */
    fun openStream(uri: Uri, maxBytes: Long, maxMegabytes: Long): InputStream {
        val knownSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: UNKNOWN_SIZE
        if (knownSize > maxBytes) throw BackupImportException(ImportError.TooLarge(maxMegabytes))

        val stream = try {
            context.contentResolver.openInputStream(uri)
        } catch (e: FileNotFoundException) {
            throw BackupImportException(ImportError.Unreadable(Diagnostic.of(e)))
        } catch (e: SecurityException) {
            throw BackupImportException(ImportError.Unreadable(Diagnostic.of(e)))
        }
        return stream ?: throw BackupImportException(ImportError.Unreadable(detail = null))
    }

    private companion object {
        const val UNKNOWN_SIZE = -1L
        const val READ_BUFFER_BYTES = 8 * 1024

        fun tooLarge() = ImportError.TooLarge(maxMegabytes = BackupSizeLimit.MAX_MEGABYTES)

        fun InputStream.readCapped(max: Long): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(READ_BUFFER_BYTES)
            var total = 0L
            while (total < max) {
                val n = read(buf, 0, minOf(buf.size.toLong(), max - total).toInt())
                if (n <= 0) break
                out.write(buf, 0, n)
                total += n
            }
            return out.toByteArray()
        }
    }
}
