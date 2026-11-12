package com.lcdcode.moodcairns.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
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
        if (knownSize > MAX_BACKUP_BYTES) throw BackupImportException(tooLarge())

        // Read up to MAX+1 bytes so an overrun shows even when the provider reported no length.
        val bytes = try {
            context.contentResolver.openInputStream(uri)?.use {
                it.readCapped(MAX_BACKUP_BYTES + 1)
            }
        } catch (t: Throwable) {
            val detail = t.message ?: t.javaClass.simpleName
            throw BackupImportException(ImportError.Unreadable(detail))
        } ?: throw BackupImportException(ImportError.Unreadable(detail = null))

        if (bytes.size > MAX_BACKUP_BYTES) throw BackupImportException(tooLarge())
        return String(bytes, Charsets.UTF_8)
    }

    private companion object {
        const val UNKNOWN_SIZE = -1L
        const val BYTES_PER_MEGABYTE: Long = 1024 * 1024
        const val MAX_BACKUP_BYTES: Long = 50L * BYTES_PER_MEGABYTE
        const val READ_BUFFER_BYTES = 8 * 1024

        fun tooLarge() =
            ImportError.TooLarge(maxMegabytes = MAX_BACKUP_BYTES / BYTES_PER_MEGABYTE)

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
