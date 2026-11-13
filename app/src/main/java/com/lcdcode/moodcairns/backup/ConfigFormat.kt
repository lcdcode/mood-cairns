package com.lcdcode.moodcairns.backup

import kotlinx.serialization.Serializable

/**
 * On-disk schema for a configuration-only export: the tracking setup without any
 * entries. It travels in the same [EncryptedBackup] envelope as a full backup.
 *
 * Deliberately has no `entries` field. [BackupFile.entries] has no default, so any
 * app version that tries to restore this file as a full backup fails to decode it
 * instead of wiping every entry and restoring none.
 *
 * Bump [CURRENT_VERSION] when an older reader would misread a newer file; older
 * app versions then report the file as incompatible instead of importing it.
 */
@Serializable
data class ConfigFile(
    val kind: String,
    val configVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val scales: List<ScaleDto>,
    val promptWindows: List<PromptWindowDto>,
    val tags: List<TagDto>,
    val tagCategories: List<TagCategoryDto>,
) {
    companion object {
        /** Marks the decrypted JSON as a configuration file rather than a full backup. */
        const val KIND = "mood-cairns-config"
        const val CURRENT_VERSION = 1
        val SUPPORTED_VERSIONS = 1..CURRENT_VERSION

        /** JSON keys used to tell a configuration file from a full backup. */
        const val KIND_KEY = "kind"
        const val VERSION_KEY = "configVersion"
        const val ENTRIES_KEY = "entries"
    }
}
