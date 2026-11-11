package com.lcdcode.moodcairns.ui.tags

import com.lcdcode.moodcairns.data.entity.Tag

/** [Tag.columnIndex] limited to [Tag.COLUMN_RANGE], so stray values land in the last column. */
val Tag.visibleColumn: Int get() = columnIndex.coerceIn(Tag.COLUMN_RANGE)

/**
 * Splits one category's tags into its visual columns, each keeping the tags' order.
 *
 * The column count is the highest column used + 1, so tags placed only in columns 1
 * and 3 leave an empty middle column rather than shifting column 3 left. No tags means
 * no columns; tags all in the first column mean a single column.
 */
fun List<Tag>.inColumns(): List<List<Tag>> {
    if (isEmpty()) return emptyList()
    val columnCount = maxOf { it.visibleColumn } + 1
    val byColumn = groupBy { it.visibleColumn }
    return List(columnCount) { column -> byColumn[column].orEmpty() }
}

/** The column to keep when a tag's category changes: column positions only mean
 * something within one section, so a move starts over in the first column. */
fun columnAfterCategoryChange(
    currentColumn: Int,
    oldCategoryId: String,
    newCategoryId: String,
): Int = if (newCategoryId == oldCategoryId) currentColumn else 0
