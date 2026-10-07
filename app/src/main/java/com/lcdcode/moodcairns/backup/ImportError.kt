package com.lcdcode.moodcairns.backup

/**
 * Why a backup import failed. Carries data only; the UI layer turns it into
 * localized text. `detail` fields hold technical diagnostics (exception messages),
 * shown as-is inside a translated sentence.
 */
sealed interface ImportError {
    data class TooLarge(val maxMegabytes: Long) : ImportError
    data class Unreadable(val detail: String?) : ImportError
    data object NotABackup : ImportError
    data object WrongSecretOrCorrupt : ImportError
    data class UnsupportedVersion(val found: Int) : ImportError
    data class Malformed(val detail: String) : ImportError
    data class WriteFailed(val detail: String) : ImportError
}

/** Thrown by [BackupSerializer.parse] for a backup that cannot be read. */
class BackupImportException(val error: ImportError) : Exception("Backup import failed: $error")
