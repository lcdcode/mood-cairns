package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.db.Seed
import com.lcdcode.moodcairns.data.entity.BuiltInTagCategory
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory

/*
 * Decides how a configuration import changes the local setup, or why it must not.
 *
 * Rows are matched by name, never by id: ids are per-install, so id 7 in a file from
 * another device may be a different scale. A matched row is updated in place and keeps
 * its local id, so entries never need remapping. A local row without a match is deleted,
 * which is only allowed when no entry uses it:
 *  - entry_tag -> tag is CASCADE, so deleting a used tag would silently drop the links;
 *  - entry_value -> scale is RESTRICT, so deleting a used scale would abort the write.
 * A used scale must also keep its direction and a range covering its recorded values,
 * or the stored numbers would change meaning.
 */

/** The setup a file asks for, already sanitized (see [BackupSerializer.parseConfig]). */
data class ConfigEntities(
    val scales: List<Scale>,
    val tagCategories: List<TagCategory>,
    val tags: List<Tag>,
    val windows: List<PromptWindow>,
)

/** How many entries record a value on a scale, and the extremes of those values. */
data class ScaleUsage(val entryCount: Int, val minValue: Float, val maxValue: Float)

/** The mood database's current setup and how entries use it. */
data class LocalMoodConfig(
    val scales: List<Scale>,
    val tagCategories: List<TagCategory>,
    val tags: List<Tag>,
    val scaleUsage: Map<Long, ScaleUsage>,
    /** Entry count per tag id; unused tags may be absent. */
    val tagUsage: Map<Long, Int>,
)

data class RowChanges<T, K>(val inserts: List<T>, val updates: List<T>, val deleteIds: List<K>)

data class MoodConfigChanges(
    val categories: RowChanges<TagCategory, String>,
    val tags: RowChanges<Tag, Long>,
    val scales: RowChanges<Scale, Long>,
)

/** A reason the import would delete or change existing entry data. */
sealed interface ConfigConflict {
    val entryCount: Int

    data class ScaleMissing(val scale: Scale, override val entryCount: Int) : ConfigConflict

    data class ScaleDirectionChanged(
        val scale: Scale,
        override val entryCount: Int,
    ) : ConfigConflict

    data class ScaleRangeTooNarrow(
        val scale: Scale,
        override val entryCount: Int,
        val newMin: Int,
        val newMax: Int,
    ) : ConfigConflict

    /** [category] is null when the tag's category row is missing locally. */
    data class TagMissing(
        val tag: Tag,
        val category: TagCategory?,
        override val entryCount: Int,
    ) : ConfigConflict
}

sealed interface MoodConfigPlan {
    data class Apply(val changes: MoodConfigChanges) : MoodConfigPlan
    data class Incompatible(val conflicts: List<ConfigConflict>) : MoodConfigPlan
}

/**
 * Plans the mood-database side of a configuration import. [newCategoryId] supplies an
 * id for an incoming user category whose id is already taken locally.
 */
fun planMoodConfigImport(
    incoming: ConfigEntities,
    local: LocalMoodConfig,
    newCategoryId: () -> String = TagCategory::newCustomId,
): MoodConfigPlan {
    val categoryMatch = planCategories(incoming.tagCategories, local.tagCategories, newCategoryId)
    val tagChanges = planTags(incoming.tags, local.tags, categoryMatch.localIdFor)
    val conflicts = scaleConflicts(incoming.scales, local) +
        tagConflicts(tagChanges.deleteIds, local)
    if (conflicts.isNotEmpty()) return MoodConfigPlan.Incompatible(conflicts)
    return MoodConfigPlan.Apply(
        MoodConfigChanges(
            categories = categoryMatch.changes,
            tags = tagChanges,
            scales = planScales(incoming.scales, local.scales),
        ),
    )
}

/**
 * Plans the prompt-window side. Windows match on slot and label so entries keep their
 * window's label. Unmatched windows are deleted: no foreign key points at them, and an
 * entry whose window is gone shows its slot name, exactly as after an in-app delete.
 */
fun planWindowImport(
    incoming: List<PromptWindow>,
    local: List<PromptWindow>,
): RowChanges<PromptWindow, Long> {
    val unmatched = local.toMutableList()
    val inserts = mutableListOf<PromptWindow>()
    val updates = mutableListOf<PromptWindow>()
    for (window in incoming) {
        val match = unmatched.firstOrNull { it.slot == window.slot && it.label == window.label }
        if (match == null) {
            inserts += window.copy(id = 0)
        } else {
            unmatched.remove(match)
            updates += window.copy(id = match.id)
        }
    }
    return RowChanges(inserts, updates, unmatched.map { it.id })
}

private data class CategoryMatch(
    val changes: RowChanges<TagCategory, String>,
    /** Local category id for each incoming category id. */
    val localIdFor: Map<String, String>,
)

private fun planCategories(
    incoming: List<TagCategory>,
    local: List<TagCategory>,
    newCategoryId: () -> String,
): CategoryMatch {
    val unmatched = local.toMutableList()
    val takenIds = local.mapTo(HashSet()) { it.id }
    val inserts = mutableListOf<TagCategory>()
    val updates = mutableListOf<TagCategory>()
    val localIdFor = HashMap<String, String>()
    for (category in incoming) {
        val match = unmatched.firstOrNull { it.sameCategoryAs(category) }
        if (match == null) {
            val id = if (category.id in takenIds) newCategoryId() else category.id
            takenIds += id
            inserts += category.copy(id = id)
            localIdFor[category.id] = id
        } else {
            unmatched.remove(match)
            updates += category.copy(id = match.id)
            localIdFor[category.id] = match.id
        }
    }
    return CategoryMatch(RowChanges(inserts, updates, unmatched.map { it.id }), localIdFor)
}

/** Built-ins match on their fixed id; user categories, whose ids are random, on name. */
private fun TagCategory.sameCategoryAs(other: TagCategory): Boolean =
    if (isBuiltIn() || other.isBuiltIn()) id == other.id else label == other.label

private fun TagCategory.isBuiltIn(): Boolean = BuiltInTagCategory.fromId(id) != null

private fun planTags(
    incoming: List<Tag>,
    local: List<Tag>,
    localCategoryIdFor: Map<String, String>,
): RowChanges<Tag, Long> {
    val localByKey = local.associateBy { it.name to it.categoryId }
    val seenKeys = HashSet<Pair<String, String>>()
    val inserts = mutableListOf<Tag>()
    val updates = mutableListOf<Tag>()
    for (tag in incoming) {
        val categoryId = localCategoryIdFor[tag.categoryId] ?: TagCategory.OTHER_ID
        val key = tag.name to categoryId
        if (!seenKeys.add(key)) continue
        val match = localByKey[key]
        if (match == null) {
            inserts += tag.copy(id = 0, categoryId = categoryId)
        } else {
            updates += match.copy(sortOrder = tag.sortOrder, columnIndex = tag.columnIndex)
        }
    }
    val keptIds = updates.mapTo(HashSet()) { it.id }
    return RowChanges(inserts, updates, local.map { it.id }.filterNot { it in keptIds })
}

private fun planScales(incoming: List<Scale>, local: List<Scale>): RowChanges<Scale, Long> {
    val localByName = local.associateBy { it.name }
    val inserts = mutableListOf<Scale>()
    val updates = mutableListOf<Scale>()
    for (scale in incoming.distinctBy { it.name }) {
        val match = localByName[scale.name]
        if (match == null) {
            val builtIn = scale.isBuiltIn && scale.name in seedScaleNames
            inserts += scale.copy(id = 0, isBuiltIn = builtIn)
        } else {
            updates += scale.copy(id = match.id, isBuiltIn = match.isBuiltIn)
        }
    }
    val keptNames = incoming.mapTo(HashSet()) { it.name }
    return RowChanges(inserts, updates, local.filterNot { it.name in keptNames }.map { it.id })
}

/** isBuiltIn only means something for a seed name; anything else would be a stray flag. */
private val seedScaleNames: Set<String> by lazy { Seed.scales.mapTo(HashSet()) { it.name } }

private fun scaleConflicts(incoming: List<Scale>, local: LocalMoodConfig): List<ConfigConflict> {
    val incomingByName = incoming.distinctBy { it.name }.associateBy { it.name }
    return local.scales.mapNotNull { scale ->
        val usage = local.scaleUsage[scale.id]?.takeIf { it.entryCount > 0 }
            ?: return@mapNotNull null
        val target = incomingByName[scale.name]
        when {
            target == null -> ConfigConflict.ScaleMissing(scale, usage.entryCount)
            target.inverted != scale.inverted ->
                ConfigConflict.ScaleDirectionChanged(scale, usage.entryCount)
            usage.minValue < target.minValue || usage.maxValue > target.maxValue ->
                ConfigConflict.ScaleRangeTooNarrow(
                    scale, usage.entryCount, target.minValue, target.maxValue,
                )
            else -> null
        }
    }
}

private fun tagConflicts(deletedTagIds: List<Long>, local: LocalMoodConfig): List<ConfigConflict> {
    val tagsById = local.tags.associateBy { it.id }
    val categoriesById = local.tagCategories.associateBy { it.id }
    return deletedTagIds.mapNotNull { id ->
        val entryCount = local.tagUsage[id]?.takeIf { it > 0 } ?: return@mapNotNull null
        val tag = tagsById.getValue(id)
        ConfigConflict.TagMissing(tag, categoriesById[tag.categoryId], entryCount)
    }
}
