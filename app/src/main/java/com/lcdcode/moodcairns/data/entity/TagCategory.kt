package com.lcdcode.moodcairns.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A section that groups tags. Built-in categories use their [BuiltInTagCategory]
 * name as [id]; user-created ones get a random UUID.
 */
@Entity(tableName = "tag_category")
data class TagCategory(
    @PrimaryKey val id: String,
    /** User-chosen name. Null shows a built-in's translated default; user categories set it. */
    val label: String?,
    val sortOrder: Int,
) {
    companion object {
        const val MAX_LABEL_LENGTH = 32

        /** Longest id accepted from a backup; generated UUIDs are 36 characters. */
        const val MAX_ID_LENGTH = 64

        /** Tags of a deleted category move here, so it can never be deleted itself. */
        val OTHER_ID: String = BuiltInTagCategory.OTHER.id

        fun newCustomId(): String = UUID.randomUUID().toString()

        /**
         * Normalizes user or backup input: drops control characters (including
         * newlines), trims, and caps at [MAX_LABEL_LENGTH] code points. Returns null
         * when nothing is left.
         */
        fun sanitizeLabel(raw: String): String? {
            val cleaned = raw.filterNot(Char::isISOControl).trim()
            if (cleaned.isEmpty()) return null
            val codePoints = cleaned.codePoints().limit(MAX_LABEL_LENGTH.toLong()).toArray()
            return String(codePoints, 0, codePoints.size).trimEnd()
        }
    }
}
