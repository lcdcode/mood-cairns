package com.lcdcode.moodcairns.backup

import kotlinx.serialization.Serializable

/**
 * On-disk schema for a full export. Bump [CURRENT_VERSION] and widen
 * [SUPPORTED_VERSIONS] only when old readers would misinterpret the data or an
 * import-time backfill must be version-gated; older versions must keep parsing
 * via serialization defaults (v1 files simply have no [tags] and no
 * [EntryDto.tagIds]). Purely additive defaulted fields (e.g. [ScaleDto.inverted])
 * do NOT bump the version: a bump would make older app builds reject newer
 * files outright, while an unknown key is merely dropped on import.
 *
 * v3 added [tagCategories] (user-created categories). It is a real bump: a v2 reader
 * would drop every tag in a category it does not know, with no error.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val scales: List<ScaleDto>,
    val promptWindows: List<PromptWindowDto>,
    /**
     * Must never get a default value: its absence would make a [ConfigFile] fail to
     * decode as a full backup, so restoring one can never wipe the user's entries.
     */
    val entries: List<EntryDto>,
    val tags: List<TagDto> = emptyList(),
    /** v2 only: custom names for the built-in categories. Read on import, never written. */
    val tagCategoryLabels: List<TagCategoryLabelDto> = emptyList(),
    val tagCategories: List<TagCategoryDto> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 3
        const val FIRST_VERSION_WITH_CATEGORIES = 3
        val SUPPORTED_VERSIONS = 1..CURRENT_VERSION
    }
}

@Serializable
data class ScaleDto(
    val id: Long,
    val name: String,
    val minValue: Int,
    val maxValue: Int,
    val step: Float,
    val colorArgb: Int,
    val isBuiltIn: Boolean,
    val archived: Boolean,
    val sortOrder: Int,
    val inverted: Boolean = false,
    val defaultValue: Float? = null,
)

@Serializable
data class PromptWindowDto(
    val id: Long,
    val label: String,
    val slot: String,
    val startTime: String,
    val endTime: String,
    val enabled: Boolean,
)

@Serializable
data class EntryDto(
    val id: Long,
    val recordedAt: String,
    val slot: String,
    val promptWindowId: Long?,
    val note: String?,
    val values: List<EntryValueDto>,
    val tagIds: List<Long> = emptyList(),
)

@Serializable
data class TagDto(
    val id: Long,
    val name: String,
    val category: String,
    val sortOrder: Int,
    val columnIndex: Int = 0,
)

/** A tag category. [label] is null for a built-in that keeps its translated name. */
@Serializable
data class TagCategoryDto(
    val id: String,
    val label: String? = null,
    val sortOrder: Int,
)

/** A renamed tag category. Categories absent from the list use their default name. */
@Serializable
data class TagCategoryLabelDto(
    val category: String,
    val label: String,
)

@Serializable
data class EntryValueDto(
    val scaleId: Long,
    val value: Float,
)

/**
 * Encrypted wrapper actually written to disk. The plaintext [BackupFile] is
 * AES-GCM encrypted with a key derived from the user's PIN via PBKDF2. [salt]
 * and [iter] are freshly generated per export and carried inside the envelope
 * so any install — including a fresh one — can decrypt given the same PIN.
 */
@Serializable
data class EncryptedBackup(
    val envelope: Int = 1,
    val kdf: String,
    val iter: Int,
    val salt: String,
    val iv: String,
    val ciphertext: String,
)
