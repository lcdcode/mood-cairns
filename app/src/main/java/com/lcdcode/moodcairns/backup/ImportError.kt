package com.lcdcode.moodcairns.backup

/**
 * Why a backup import failed. Carries data only; the UI layer turns it into localized text.
 * Nothing here holds raw file content: a refused value is named by a [MalformedReason], and
 * exception text is wrapped in a bounded, escaped [Diagnostic].
 */
sealed interface ImportError {
    data class TooLarge(val maxMegabytes: Long) : ImportError
    data class Unreadable(val detail: Diagnostic?) : ImportError
    data object NotABackup : ImportError
    data object WrongSecretOrCorrupt : ImportError
    /** A configuration-only file was picked for a full restore; it has no entries. */
    data object ConfigOnlyFile : ImportError
    /** A journal backup was picked for a mood or configuration import. */
    data object JournalFile : ImportError
    /** A mood backup or configuration file was picked for a journal restore. */
    data object NotAJournalBackup : ImportError
    /** [fromConfigFile] tells a configuration file's version from a full backup's. */
    data class UnsupportedVersion(
        val found: Int,
        val fromConfigFile: Boolean = false,
    ) : ImportError
    data class Malformed(val reason: MalformedReason) : ImportError
    data class WriteFailed(val detail: Diagnostic) : ImportError
}

/**
 * What was wrong with a damaged file. [code] is a fixed technical label, safe to show and to
 * log because it never contains file content.
 */
enum class MalformedReason(val code: String) {
    UNSUPPORTED_KDF("unsupported-kdf"),
    KDF_ITERATIONS_OUT_OF_RANGE("kdf-iterations"),
    ENVELOPE_SALT("envelope-salt"),
    ENVELOPE_IV("envelope-iv"),
    ENVELOPE_CIPHERTEXT("envelope-ciphertext"),
    CONTENTS_NOT_AN_OBJECT("contents-not-object"),
    CONTENTS_INVALID("contents-invalid"),
    MISSING_VERSION("missing-version"),
    INVALID_SCALE("invalid-scale"),
    INVALID_PROMPT_WINDOW("invalid-prompt-window"),
    CHUNK_SIZE_OUT_OF_RANGE("chunk-size"),
    CUT_OFF("cut-off"),
    ENDED_EARLY("ended-early"),
    COMPRESSION_DAMAGED("compression-damaged"),
    TRAILING_DATA("trailing-data"),
    RECORD_LENGTH_OUT_OF_RANGE("record-length"),
    RECORD_INVALID("record-invalid"),
    MISSING_METADATA("missing-metadata"),
    MISSING_JOURNAL_VERSION("missing-journal-version"),
    INVALID_DAY("invalid-day"),
    INVALID_CREATED_AT("invalid-created-at"),
    INVALID_UPDATED_AT("invalid-updated-at"),
    DAY_OUT_OF_RANGE("day-out-of-range"),
    DAYS_OUT_OF_ORDER("days-out-of-order"),
    BODY_TOO_LONG("body-too-long"),
}

/**
 * A short label for logs. Exception detail can name the picked file, so it is included only
 * when [withDetail] is set (debug builds); file content never appears either way.
 */
fun ImportError.logLabel(withDetail: Boolean = false): String = when (this) {
    is ImportError.TooLarge -> "TooLarge(${maxMegabytes}MB)"
    is ImportError.Unreadable -> if (withDetail) "Unreadable($detail)" else "Unreadable"
    ImportError.NotABackup -> "NotABackup"
    ImportError.WrongSecretOrCorrupt -> "WrongSecretOrCorrupt"
    ImportError.ConfigOnlyFile -> "ConfigOnlyFile"
    ImportError.JournalFile -> "JournalFile"
    ImportError.NotAJournalBackup -> "NotAJournalBackup"
    is ImportError.UnsupportedVersion -> "UnsupportedVersion($found, config=$fromConfigFile)"
    is ImportError.Malformed -> "Malformed(${reason.code})"
    is ImportError.WriteFailed -> if (withDetail) "WriteFailed($detail)" else "WriteFailed"
}

/** Thrown by [BackupSerializer.parse] for a backup that cannot be read. */
class BackupImportException(val error: ImportError) :
    Exception("Backup import failed: ${error.logLabel()}")
