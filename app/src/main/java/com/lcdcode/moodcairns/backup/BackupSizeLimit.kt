package com.lcdcode.moodcairns.backup

/**
 * The largest backup file this app reads. Import refuses bigger files to protect the heap,
 * so export refuses to write them: a backup that cannot be restored is worse than none.
 */
internal object BackupSizeLimit {
    private const val BYTES_PER_MEGABYTE = 1024L * 1024L

    const val MAX_BYTES: Long = 50L * BYTES_PER_MEGABYTE
    const val MAX_MEGABYTES: Long = MAX_BYTES / BYTES_PER_MEGABYTE

    fun exceeds(sizeBytes: Long): Boolean = sizeBytes > MAX_BYTES

    /** Throws [BackupTooLargeException] if [envelope] could not be imported again. */
    fun requireImportable(envelope: String) {
        // Envelopes are ASCII (JSON around base64), so one char is one byte.
        if (exceeds(envelope.length.toLong())) throw BackupTooLargeException(MAX_MEGABYTES)
    }
}

/** An export was refused because its file would exceed [BackupSizeLimit.MAX_BYTES]. */
class BackupTooLargeException(val maxMegabytes: Long) :
    Exception("Backup would exceed the $maxMegabytes MB import limit")
