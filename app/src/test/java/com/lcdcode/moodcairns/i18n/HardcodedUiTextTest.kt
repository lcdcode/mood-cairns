package com.lcdcode.moodcairns.i18n

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Android lint's HardcodedText check only covers XML layouts, not Compose. This is a
 * ratchet instead: files listed in [EXTRACTED_FILES] have had their user-facing text
 * moved to string resources, and must not regain string literals in common UI text
 * positions. Add each file here once it is extracted.
 */
class HardcodedUiTextTest {

    @Test
    fun extractedFiles_haveNoLiteralUiText() {
        val sourceRoot = sequenceOf("src/main/java", "app/src/main/java")
            .map { File(it, "com/lcdcode/moodcairns") }
            .firstOrNull(File::isDirectory)
            ?: error("source root not found from ${File(".").absolutePath}")

        val offenders = EXTRACTED_FILES.flatMap { relativePath ->
            val file = File(sourceRoot, relativePath)
            check(file.exists()) { "Listed file no longer exists: $relativePath" }
            file.readLines().withIndex()
                .filter { (_, line) -> LITERAL_UI_TEXT.containsMatchIn(line) }
                .map { (index, line) -> "$relativePath:${index + 1}: ${line.trim()}" }
        }
        assertEquals(emptyList<String>(), offenders)
    }

    private companion object {
        val EXTRACTED_FILES = listOf(
            "notifications/NotificationChannels.kt",
            "notifications/PromptAlarmReceiver.kt",
            "ui/about/AboutScreen.kt",
            "ui/backup/BackupScreen.kt",
            "ui/charts/ChartAxis.kt",
            "ui/charts/ChartsScreen.kt",
            "ui/common/EntrySlotUi.kt",
            "ui/common/ScaleFormat.kt",
            "ui/entry/EntryScreen.kt",
            "ui/history/HistoryScreen.kt",
            "ui/home/HomeScreen.kt",
            "ui/lock/Biometrics.kt",
            "ui/lock/LockScreen.kt",
            "ui/lock/MigratingScreen.kt",
            "ui/lock/NoPinWarningDialog.kt",
            "ui/lock/SetPinScreen.kt",
            "ui/scales/ScaleEditScreen.kt",
            "ui/scales/ScaleListScreen.kt",
            "ui/scales/ScaleWarnings.kt",
            "ui/settings/ChangePinScreen.kt",
            "ui/settings/PromptWindowEditScreen.kt",
            "ui/settings/SettingsScreen.kt",
            "ui/tags/TagCategoryUi.kt",
            "ui/tags/TagEditScreen.kt",
            "ui/tags/TagListScreen.kt",
            "ui/tags/TagWarnings.kt",
            "widget/LogMoodWidget.kt",
        )

        // Text("..."), a literal passed as a common text-carrying parameter, or a
        // literal handed to a notification or biometric-prompt builder.
        val LITERAL_UI_TEXT = Regex(
            """\bText\(\s*"|\b(contentDescription|title|label|text|placeholder)\s*=\s*"""" +
                """|\.set(Title|Subtitle|NegativeButtonText|ContentTitle|ContentText)\(\s*"""",
        )
    }
}
