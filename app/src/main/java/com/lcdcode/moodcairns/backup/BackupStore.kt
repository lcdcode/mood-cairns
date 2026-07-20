package com.lcdcode.moodcairns.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class BackupFileInfo(val uri: Uri, val displayName: String, val createdAt: Long, val sizeBytes: Long)

/**
 * Writes JSON backups into MediaStore under Documents/MoodCairns/. This is a
 * user-visible location so Syncthing (or any file browser) can pick the file
 * up. No storage permission is required on Android 10+ because we only ever
 * touch files our app creates.
 */
@Singleton
class BackupStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/${FOLDER_NAME}/"

    suspend fun writeBackup(
        name: String,
        content: String,
        mimeType: String = MIME_JSON,
    ): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: error("Failed to create backup entry in MediaStore")

        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: error("Failed to open MediaStore output for $uri")

            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (t: Throwable) {
            // Never leave a half-written pending file behind.
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    suspend fun list(): List<BackupFileInfo> {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.SIZE,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf(relativePath, "$FILE_PREFIX%.json")
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val result = mutableListOf<BackupFileInfo>()
        resolver.query(collection, projection, selection, args, sort)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                result += BackupFileInfo(
                    uri = uri,
                    displayName = cursor.getString(nameCol),
                    createdAt = cursor.getLong(dateCol) * 1000L,
                    sizeBytes = cursor.getLong(sizeCol),
                )
            }
        }
        return result
    }

    suspend fun delete(uri: Uri): Boolean {
        return context.contentResolver.delete(uri, null, null) > 0
    }

    /** Keep [keep] most-recent backups, delete the rest. */
    suspend fun trim(keep: Int): Int {
        val all = list()
        if (all.size <= keep) return 0
        val toDelete = all.drop(keep)
        var deleted = 0
        for (info in toDelete) if (delete(info.uri)) deleted++
        return deleted
    }

    fun suggestName(date: LocalDate = LocalDate.now()): String =
        "$FILE_PREFIX${FILE_DATE_FMT.format(date)}.json"

    fun suggestCsvName(date: LocalDate = LocalDate.now()): String =
        "$CSV_FILE_PREFIX${FILE_DATE_FMT.format(date)}.csv"

    companion object {
        const val MIME_JSON = "application/json"
        const val MIME_CSV = "text/csv"
        private const val FOLDER_NAME = "MoodCairns"
        private const val FILE_PREFIX = "mood-cairns-backup-"
        private const val CSV_FILE_PREFIX = "mood-cairns-export-"
        private val FILE_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
