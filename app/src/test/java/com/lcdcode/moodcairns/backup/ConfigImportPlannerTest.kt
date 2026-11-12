package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.db.SeedTagCategories
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * Pins the rule that a configuration import never deletes or changes entry data: any
 * plan that would is refused with the reasons, and compatible plans keep local ids.
 */
class ConfigImportPlannerTest {

    // ---- scales ----

    @Test
    fun noEntries_appliesUpdatesInsertsAndDeletes() {
        val local = local(scales = listOf(scale(1, "Happiness"), scale(2, "Anxiety")))
        val incoming = incoming(
            scales = listOf(scale(9, "Happiness", color = RED), scale(10, "Sleep")),
        )

        val changes = plan(incoming, local).applied().scales

        assertEquals(listOf(scale(1, "Happiness", color = RED)), changes.updates)
        assertEquals(listOf(scale(0, "Sleep")), changes.inserts)
        assertEquals(listOf(2L), changes.deleteIds)
    }

    @Test
    fun builtInFlag_keepsLocalValueAndIsOnlyTrustedForSeedNames() {
        val local = local(scales = listOf(scale(1, "Happiness", builtIn = true)))
        val incoming = incoming(
            scales = listOf(
                scale(5, "Happiness", builtIn = false),
                scale(6, "Pain", builtIn = true),
                scale(7, "Custom", builtIn = true),
            ),
        )

        val changes = plan(incoming, local).applied().scales

        assertTrue(changes.updates.single().isBuiltIn)
        assertEquals(
            mapOf("Pain" to true, "Custom" to false),
            changes.inserts.associate { it.name to it.isBuiltIn },
        )
    }

    @Test
    fun usedScaleMissingFromFile_isRefused() {
        val sleep = scale(5, "Sleep")
        val local = local(scales = listOf(sleep), scaleUsage = mapOf(5L to usage(3, 1f, 9f)))

        val conflicts = plan(incoming(scales = emptyList()), local).conflicts()

        assertEquals(listOf(ConfigConflict.ScaleMissing(sleep, entryCount = 3)), conflicts)
    }

    @Test
    fun usedScaleWithFlippedDirection_isRefused() {
        val stress = scale(5, "Stress")
        val local = local(scales = listOf(stress), scaleUsage = mapOf(5L to usage(2, 1f, 9f)))
        val incoming = incoming(scales = listOf(scale(5, "Stress", inverted = true)))

        val conflicts = plan(incoming, local).conflicts()

        assertEquals(
            listOf(ConfigConflict.ScaleDirectionChanged(stress, entryCount = 2)),
            conflicts,
        )
    }

    @Test
    fun usedScaleWithRangeExcludingRecordedValues_isRefused() {
        val pain = scale(5, "Pain", min = 1, max = 10)
        val local = local(scales = listOf(pain), scaleUsage = mapOf(5L to usage(4, 1f, 9f)))
        val incoming = incoming(scales = listOf(scale(5, "Pain", min = 1, max = 5)))

        val conflicts = plan(incoming, local).conflicts()

        assertEquals(
            listOf(
                ConfigConflict.ScaleRangeTooNarrow(pain, entryCount = 4, newMin = 1, newMax = 5),
            ),
            conflicts,
        )
    }

    @Test
    fun usedScaleWithRangeStillCoveringRecordedValues_isAllowed() {
        val local = local(
            scales = listOf(scale(5, "Pain", min = 1, max = 10)),
            scaleUsage = mapOf(5L to usage(4, 2f, 8f)),
        )
        val incoming = incoming(scales = listOf(scale(77, "Pain", min = 2, max = 20)))

        val updated = plan(incoming, local).applied().scales.updates.single()

        assertEquals(5L, updated.id)
        assertEquals(2..20, updated.minValue..updated.maxValue)
    }

    @Test
    fun unusedScale_mayFlipAndShrink() {
        val local = local(scales = listOf(scale(5, "Pain", min = 1, max = 10)))
        val shrunkAndFlipped = scale(5, "Pain", min = 1, max = 3, inverted = true)
        val incoming = incoming(scales = listOf(shrunkAndFlipped))

        assertTrue(plan(incoming, local) is MoodConfigPlan.Apply)
    }

    // ---- tags and categories ----

    @Test
    fun usedTagMissingFromFile_isRefusedWithItsCategory() {
        val gym = tag(4, "Gym", category = "PLACE")
        val local = local(tags = listOf(gym), tagUsage = mapOf(4L to 2))

        val conflicts = plan(incoming(tags = emptyList()), local).conflicts()

        val place = SeedTagCategories.all.first { it.id == "PLACE" }
        assertEquals(listOf(ConfigConflict.TagMissing(gym, place, entryCount = 2)), conflicts)
    }

    @Test
    fun usedTagMovedToAnotherCategory_isRefused() {
        val local = local(
            tags = listOf(tag(4, "Gym", category = "PLACE")),
            tagUsage = mapOf(4L to 1),
        )
        val incoming = incoming(tags = listOf(tag(4, "Gym", category = "ACTIVITY")))

        val conflict = plan(incoming, local).conflicts().single()

        assertTrue(conflict is ConfigConflict.TagMissing)
    }

    @Test
    fun matchedTags_keepLocalIdAndTakeFileLayout_unusedTagsAreDeleted() {
        val local = local(
            tags = listOf(tag(4, "Gym", "PLACE"), tag(5, "Old", "PLACE")),
            tagUsage = mapOf(4L to 9),
        )
        val incoming = incoming(
            tags = listOf(
                tag(40, "Gym", "PLACE", sortOrder = 3, column = 2),
                tag(41, "New", "MOOD"),
            ),
        )

        val changes = plan(incoming, local).applied().tags

        assertEquals(listOf(tag(4, "Gym", "PLACE", sortOrder = 3, column = 2)), changes.updates)
        assertEquals(listOf(tag(0, "New", "MOOD")), changes.inserts)
        assertEquals(listOf(5L), changes.deleteIds)
    }

    @Test
    fun userCategories_matchByName_soUsedTagsInThemSurvive() {
        val localHealth = TagCategory(id = "local-uuid", label = "Health", sortOrder = 0)
        val fileHealth = TagCategory(id = "file-uuid", label = "Health", sortOrder = 4)
        val local = local(
            categories = SeedTagCategories.all + localHealth,
            tags = listOf(tag(4, "Gym", "local-uuid")),
            tagUsage = mapOf(4L to 1),
        )
        val incoming = incoming(
            categories = SeedTagCategories.all + fileHealth,
            tags = listOf(tag(40, "Gym", "file-uuid")),
        )

        val changes = plan(incoming, local).applied()

        assertTrue(fileHealth.copy(id = "local-uuid") in changes.categories.updates)
        assertEquals(listOf(tag(4, "Gym", "local-uuid")), changes.tags.updates)
        assertEquals(emptyList<String>(), changes.categories.deleteIds)
    }

    @Test
    fun newCategoryWhoseIdIsTakenLocally_getsAFreshId() {
        val localWork = TagCategory(id = "c1", label = "Work stuff", sortOrder = 0)
        val fileHobbies = TagCategory(id = "c1", label = "Hobbies", sortOrder = 0)
        val local = local(categories = SeedTagCategories.all + localWork)
        val incoming = incoming(
            categories = SeedTagCategories.all + fileHobbies,
            tags = listOf(tag(1, "Chess", "c1")),
        )

        val changes = plan(incoming, local, newCategoryId = { "fresh" }).applied()

        assertEquals(listOf(fileHobbies.copy(id = "fresh")), changes.categories.inserts)
        assertEquals(listOf("c1"), changes.categories.deleteIds)
        assertEquals(listOf(tag(0, "Chess", "fresh")), changes.tags.inserts)
    }

    @Test
    fun everyConflictIsReported_scalesBeforeTags() {
        val local = local(
            scales = listOf(scale(1, "Sleep")),
            tags = listOf(tag(2, "Gym", "PLACE")),
            scaleUsage = mapOf(1L to usage(1, 5f, 5f)),
            tagUsage = mapOf(2L to 1),
        )

        val conflicts = plan(incoming(scales = emptyList(), tags = emptyList()), local).conflicts()

        assertEquals(2, conflicts.size)
        assertTrue(conflicts[0] is ConfigConflict.ScaleMissing)
        assertTrue(conflicts[1] is ConfigConflict.TagMissing)
    }

    // ---- prompt windows ----

    @Test
    fun windows_matchBySlotAndLabel_keepingLocalIds() {
        val morning = window(1, "Morning", PromptSlot.MORNING, hour = 8)
        val extra = window(2, "Lunch", PromptSlot.CUSTOM, hour = 12)
        val incoming = listOf(
            window(50, "Morning", PromptSlot.MORNING, hour = 7),
            window(51, "Night", PromptSlot.EVENING, hour = 22),
        )

        val changes = planWindowImport(incoming, local = listOf(morning, extra))

        assertEquals(listOf(window(1, "Morning", PromptSlot.MORNING, hour = 7)), changes.updates)
        assertEquals(listOf(window(0, "Night", PromptSlot.EVENING, hour = 22)), changes.inserts)
        assertEquals(listOf(2L), changes.deleteIds)
    }

    // ---- helpers ----

    private fun plan(
        incoming: ConfigEntities,
        local: LocalMoodConfig,
        newCategoryId: () -> String = { error("unexpected new category id") },
    ) = planMoodConfigImport(incoming, local, newCategoryId)

    private fun MoodConfigPlan.applied(): MoodConfigChanges =
        (this as? MoodConfigPlan.Apply)?.changes ?: error("Expected Apply, got $this")

    private fun MoodConfigPlan.conflicts(): List<ConfigConflict> =
        (this as? MoodConfigPlan.Incompatible)?.conflicts
            ?: error("Expected Incompatible, got $this")

    private fun local(
        scales: List<Scale> = emptyList(),
        tags: List<Tag> = emptyList(),
        categories: List<TagCategory> = SeedTagCategories.all,
        scaleUsage: Map<Long, ScaleUsage> = emptyMap(),
        tagUsage: Map<Long, Int> = emptyMap(),
    ) = LocalMoodConfig(scales, categories, tags, scaleUsage, tagUsage)

    private fun incoming(
        scales: List<Scale> = emptyList(),
        tags: List<Tag> = emptyList(),
        categories: List<TagCategory> = SeedTagCategories.all,
    ) = ConfigEntities(scales, categories, tags, windows = emptyList())

    private fun usage(entries: Int, min: Float, max: Float) = ScaleUsage(entries, min, max)

    private fun scale(
        id: Long,
        name: String,
        min: Int = 1,
        max: Int = 10,
        inverted: Boolean = false,
        builtIn: Boolean = false,
        color: Int = GREY,
    ) = Scale(
        id = id, name = name, minValue = min, maxValue = max, colorArgb = color,
        isBuiltIn = builtIn, inverted = inverted,
    )

    private fun tag(id: Long, name: String, category: String, sortOrder: Int = 0, column: Int = 0) =
        Tag(
            id = id, name = name, categoryId = category, sortOrder = sortOrder,
            columnIndex = column,
        )

    private fun window(id: Long, label: String, slot: PromptSlot, hour: Int) = PromptWindow(
        id = id, label = label, slot = slot,
        startTime = LocalTime.of(hour, 0), endTime = LocalTime.of(hour + 1, 0),
    )

    private companion object {
        const val GREY = 0xFF9AA39A.toInt()
        const val RED = 0xFFD17D7D.toInt()
    }
}
