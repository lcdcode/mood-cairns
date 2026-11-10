package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.db.SeedTagCategories
import com.lcdcode.moodcairns.data.entity.BuiltInTagCategory
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory

/*
 * Turns the tag sections of a decrypted backup into rows that are safe to insert.
 * The file is untrusted (it may be hand-edited), so invalid data is repaired or
 * dropped here rather than aborting the import on a constraint violation.
 */

/** Tags to insert, plus the surviving tag id for each tag id that was merged away. */
internal data class ResolvedTags(val tags: List<Tag>, val replacedIds: Map<Long, Long>)

/**
 * The categories to restore, renumbered 0..n-1 with Other last (added if missing).
 * Files before v3 have no categories, so they get the built-ins with any v2 names.
 */
internal fun resolveCategories(file: BackupFile): List<TagCategory> {
    val categories = if (file.schemaVersion < BackupFile.FIRST_VERSION_WITH_CATEGORIES) {
        builtInCategoriesWithLabels(file.tagCategoryLabels)
    } else {
        file.tagCategories.mapNotNull(TagCategoryDto::toEntityOrNull).distinctBy { it.id }
    }
    return withOtherLast(categories)
}

/**
 * Converts backup tags. A tag whose category is not in [categoryIds] moves to Other
 * with columnIndex 0; a negative columnIndex is clamped to 0. Tags that would share a
 * name within a category (rejected by the unique index) collapse into the first one,
 * preferring tags already in their own category, and a repeated id keeps its first tag.
 */
internal fun resolveTags(dtos: List<TagDto>, categoryIds: Set<String>): ResolvedTags {
    val (homed, orphaned) = dtos.partition { it.category in categoryIds }
    val byNameAndCategory = LinkedHashMap<Pair<String, String>, Tag>()
    val seenIds = HashSet<Long>()
    val replacedIds = HashMap<Long, Long>()
    for (dto in homed + orphaned) {
        if (!seenIds.add(dto.id)) continue
        val tag = dto.toEntity(categoryIds)
        val survivor = byNameAndCategory.getOrPut(tag.name to tag.categoryId) { tag }
        if (survivor !== tag) replacedIds[tag.id] = survivor.id
    }
    return ResolvedTags(byNameAndCategory.values.toList(), replacedIds)
}

/** The built-in categories with any custom names from [labels]; the first label per id wins. */
internal fun builtInCategoriesWithLabels(labels: List<TagCategoryLabelDto>): List<TagCategory> {
    val renamed = labels.mapNotNull(TagCategoryLabelDto::toEntityOrNull)
        .distinctBy { it.id }
        .associateBy { it.id }
    return SeedTagCategories.all.map { renamed[it.id] ?: it }
}

/**
 * Returns null for an unusable id (blank, too long, control characters) or for a
 * user category whose name sanitizes to blank, since it would have nothing to show.
 */
internal fun TagCategoryDto.toEntityOrNull(): TagCategory? {
    val validId = id.isNotBlank() && id.length <= TagCategory.MAX_ID_LENGTH &&
        id.none(Char::isISOControl)
    if (!validId) return null
    val cleanLabel = label?.let(TagCategory::sanitizeLabel)
    if (cleanLabel == null && BuiltInTagCategory.fromId(id) == null) return null
    return TagCategory(id = id, label = cleanLabel, sortOrder = sortOrder)
}

/**
 * Returns null for an unknown category or a label that sanitizes to blank, so a
 * hand-edited v2 file cannot abort the import or store an invalid name.
 */
internal fun TagCategoryLabelDto.toEntityOrNull(): TagCategory? {
    val builtIn = BuiltInTagCategory.fromId(category) ?: return null
    val cleanLabel = TagCategory.sanitizeLabel(label) ?: return null
    return TagCategory(id = builtIn.id, label = cleanLabel, sortOrder = builtIn.ordinal)
}

private fun TagDto.toEntity(categoryIds: Set<String>): Tag {
    val keepsCategory = category in categoryIds
    return Tag(
        id = id,
        name = name,
        categoryId = if (keepsCategory) category else TagCategory.OTHER_ID,
        sortOrder = sortOrder,
        columnIndex = if (keepsCategory) columnIndex.coerceAtLeast(0) else 0,
    )
}

/** Sorts by sortOrder (ties by id), puts Other last, adding it if missing, and renumbers. */
private fun withOtherLast(categories: List<TagCategory>): List<TagCategory> {
    val (others, rest) = categories.partition { it.id == TagCategory.OTHER_ID }
    val other = others.firstOrNull()
        ?: TagCategory(id = TagCategory.OTHER_ID, label = null, sortOrder = 0)
    return (rest.sortedWith(compareBy({ it.sortOrder }, { it.id })) + other)
        .mapIndexed { index, category -> category.copy(sortOrder = index) }
}
