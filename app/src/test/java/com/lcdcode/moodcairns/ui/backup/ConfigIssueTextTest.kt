package com.lcdcode.moodcairns.ui.backup

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.backup.ConfigConflict
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.ui.common.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigIssueTextTest {

    private val sleep = Scale(id = 1, name = "Sleep", minValue = 1, maxValue = 10, colorArgb = 0)

    @Test
    fun unsupportedVersion_namesTheKindOfFile() {
        assertEquals(
            listOf(UiText.Res(R.string.backup_config_incompatible_version, listOf(7))),
            ConfigIssue.UnsupportedVersion(found = 7, fromConfigFile = true).toUiTexts(),
        )
        assertEquals(
            listOf(UiText.Res(R.string.backup_config_incompatible_backup_version, listOf(7))),
            ConfigIssue.UnsupportedVersion(found = 7, fromConfigFile = false).toUiTexts(),
        )
    }

    @Test
    fun dataLoss_listsConflictsAfterTheExplanation() {
        val conflict = ConfigConflict.ScaleMissing(sleep, entryCount = 3)

        val texts = ConfigIssue.WouldLoseData(listOf(conflict)).toUiTexts()

        assertEquals(
            listOf(
                UiText.Res(R.string.backup_config_incompatible_data_loss),
                UiText.Res(R.string.backup_config_conflict_item, listOf(conflict.toUiText())),
            ),
            texts,
        )
    }

    @Test
    fun dataLoss_capsTheListAndCountsTheRest() {
        val conflicts = List(MAX_LISTED_CONFLICTS + 3) {
            ConfigConflict.ScaleMissing(sleep, entryCount = it + 1)
        }

        val texts = ConfigIssue.WouldLoseData(conflicts).toUiTexts()

        assertEquals(1 + MAX_LISTED_CONFLICTS + 1, texts.size)
        assertEquals(UiText.Plural(R.plurals.backup_config_conflict_more, 3), texts.last())
    }

    @Test
    fun conflicts_passCountNameAndRangeAsArgs() {
        assertEquals(
            UiText.Plural(
                R.plurals.backup_config_conflict_scale_range,
                4,
                listOf(4, "Sleep", 2, 5),
            ),
            ConfigConflict.ScaleRangeTooNarrow(sleep, entryCount = 4, newMin = 2, newMax = 5)
                .toUiText(),
        )
    }

    @Test
    fun tagConflict_namesACustomCategoryByItsLabel() {
        val tag = Tag(id = 1, name = "Chess", categoryId = "c1")
        val category = TagCategory(id = "c1", label = "Hobbies", sortOrder = 0)

        assertEquals(
            UiText.Plural(
                R.plurals.backup_config_conflict_tag_missing,
                2,
                listOf(2, "Chess", "Hobbies"),
            ),
            ConfigConflict.TagMissing(tag, category, entryCount = 2).toUiText(),
        )
    }

    @Test
    fun tagConflict_withMissingCategory_isShownUnderOther() {
        val tag = Tag(id = 1, name = "Chess", categoryId = "gone")

        assertEquals(
            UiText.Plural(
                R.plurals.backup_config_conflict_tag_missing,
                1,
                listOf(1, "Chess", UiText.Res(R.string.enum_tag_category_other)),
            ),
            ConfigConflict.TagMissing(tag, category = null, entryCount = 1).toUiText(),
        )
    }
}
