package com.lcdcode.moodcairns.backup

import kotlinx.serialization.Serializable

/**
 * On-disk schema for an encrypted journal backup, carried in the same [EncryptedBackup]
 * envelope as mood backups but kept as a separate file.
 *
 * It has no `entries` or `schemaVersion` key, so any app version that tries to restore it
 * as a mood backup fails to decode it instead of wiping mood data; likewise [KIND] differs
 * from [ConfigFile.KIND]. Bump [CURRENT_VERSION] when an older reader would misread a newer
 * file, so older versions refuse it as unsupported.
 */
@Serializable
data class JournalBackupFile(
    val kind: String,
    val journalVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val days: List<JournalDayDto>,
) {
    companion object {
        const val KIND = "mood-cairns-journal"
        const val CURRENT_VERSION = 1
        val SUPPORTED_VERSIONS = 1..CURRENT_VERSION
    }
}

/** One page. [day] is ISO "YYYY-MM-DD"; timestamps are ISO-8601 instants. */
@Serializable
data class JournalDayDto(
    val day: String,
    val createdAt: String,
    val updatedAt: String,
    val body: String,
)

/**
 * Just the fields needed to identify a file. Decoded first (unknown keys skipped, no JSON
 * tree built), so kind and version are checked before the bulky pages are decoded.
 */
@Serializable
internal data class JournalBackupHeader(
    val kind: String? = null,
    val journalVersion: Int? = null,
)
