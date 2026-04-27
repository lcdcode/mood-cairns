package com.moodcairns.backup

import kotlinx.serialization.Serializable

/**
 * On-disk schema for a full export. Bump [CURRENT_VERSION] and add a migration
 * branch in [BackupSerializer] whenever the shape changes.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: String,
    val appVersion: String,
    val scales: List<ScaleDto>,
    val promptWindows: List<PromptWindowDto>,
    val entries: List<EntryDto>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class ScaleDto(
    val id: Long,
    val name: String,
    val minValue: Int,
    val maxValue: Int,
    val step: Int,
    val colorArgb: Int,
    val isBuiltIn: Boolean,
    val archived: Boolean,
    val sortOrder: Int,
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
)

@Serializable
data class EntryValueDto(
    val scaleId: Long,
    val value: Int,
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
