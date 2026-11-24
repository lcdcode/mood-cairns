package com.lcdcode.moodcairns.ui.backup

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.backup.Diagnostic
import com.lcdcode.moodcairns.backup.ImportError
import com.lcdcode.moodcairns.backup.MalformedReason
import com.lcdcode.moodcairns.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportErrorTextTest {

    @Test
    fun everyError_mapsToItsOwnMessage() {
        val errors = listOf(
            ImportError.TooLarge(maxMegabytes = 50),
            ImportError.Unreadable(detail = null),
            ImportError.Unreadable(detail = Diagnostic.of("EACCES")),
            ImportError.NotABackup,
            ImportError.WrongSecretOrCorrupt,
            ImportError.ConfigOnlyFile,
            ImportError.JournalFile,
            ImportError.NotAJournalBackup,
            ImportError.UnsupportedVersion(found = 9),
            ImportError.Malformed(MalformedReason.ENVELOPE_SALT),
            ImportError.WriteFailed(detail = Diagnostic.of("disk full")),
        )
        val ids = errors.map { (it.toUiText() as UiText.Res).id }
        assertEquals("Each failure needs distinct wording", ids.size, ids.distinct().size)
    }

    @Test
    fun details_arePassedThroughAsFormatArgs() {
        assertEquals(
            UiText.Res(R.string.import_error_too_large, listOf(50L)),
            ImportError.TooLarge(maxMegabytes = 50).toUiText(),
        )
        assertEquals(
            UiText.Res(R.string.import_error_unreadable_detail, listOf("EACCES")),
            ImportError.Unreadable(detail = Diagnostic.of("EACCES")).toUiText(),
        )
        assertEquals(
            UiText.Res(R.string.import_error_unsupported_version, listOf(9)),
            ImportError.UnsupportedVersion(found = 9).toUiText(),
        )
        assertEquals(
            UiText.Res(R.string.import_error_malformed, listOf("envelope-salt")),
            ImportError.Malformed(MalformedReason.ENVELOPE_SALT).toUiText(),
        )
        assertEquals(
            UiText.Res(R.string.import_error_write_failed, listOf("disk full")),
            ImportError.WriteFailed(detail = Diagnostic.of("disk full")).toUiText(),
        )
    }

    @Test
    fun unreadableWithoutDetail_usesPlainMessage() {
        assertEquals(
            UiText.Res(R.string.import_error_unreadable),
            ImportError.Unreadable(detail = null).toUiText(),
        )
    }
}
