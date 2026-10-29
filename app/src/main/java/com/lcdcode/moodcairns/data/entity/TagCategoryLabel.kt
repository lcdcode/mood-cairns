package com.lcdcode.moodcairns.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user's custom name for a [TagCategory]. Categories without a row show their
 * translated default name, so only renamed categories are stored.
 */
@Entity(tableName = "tag_category_label")
data class TagCategoryLabel(
    @PrimaryKey val category: TagCategory,
    val label: String,
) {
    companion object {
        const val MAX_LENGTH = 32

        /**
         * Normalizes user or backup input: drops control characters (including
         * newlines), trims, and caps at [MAX_LENGTH] code points. Returns null when
         * nothing is left, meaning "use the default name".
         */
        fun sanitize(raw: String): String? {
            val cleaned = raw.filterNot(Char::isISOControl).trim()
            if (cleaned.isEmpty()) return null
            val codePoints = cleaned.codePoints().limit(MAX_LENGTH.toLong()).toArray()
            return String(codePoints, 0, codePoints.size).trimEnd()
        }
    }
}
