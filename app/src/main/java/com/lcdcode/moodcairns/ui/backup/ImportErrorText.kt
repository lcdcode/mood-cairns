package com.lcdcode.moodcairns.ui.backup

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.backup.ImportError
import com.lcdcode.moodcairns.ui.common.UiText

/** User-facing sentence for [this] import failure. */
fun ImportError.toUiText(): UiText = when (this) {
    is ImportError.TooLarge -> UiText.Res(R.string.import_error_too_large, listOf(maxMegabytes))
    is ImportError.Unreadable ->
        if (detail == null) {
            UiText.Res(R.string.import_error_unreadable)
        } else {
            UiText.Res(R.string.import_error_unreadable_detail, listOf(detail))
        }
    ImportError.NotABackup -> UiText.Res(R.string.import_error_not_a_backup)
    ImportError.WrongSecretOrCorrupt -> UiText.Res(R.string.import_error_wrong_secret)
    is ImportError.UnsupportedVersion ->
        UiText.Res(R.string.import_error_unsupported_version, listOf(found))
    is ImportError.Malformed -> UiText.Res(R.string.import_error_malformed, listOf(detail))
    is ImportError.WriteFailed -> UiText.Res(R.string.import_error_write_failed, listOf(detail))
}
