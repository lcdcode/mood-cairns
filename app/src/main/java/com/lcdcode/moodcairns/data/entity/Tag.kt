package com.lcdcode.moodcairns.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The categories every install starts with. Each name is the stored [TagCategory.id],
 * so constants must never be renamed. Declaration order is the seeded display order.
 */
enum class BuiltInTagCategory {
    PLACE, PERSON, ACTIVITY, MOOD, OTHER;

    val id: String get() = name

    companion object {
        fun fromId(id: String): BuiltInTagCategory? = entries.find { it.name == id }
    }
}

@Entity(
    tableName = "tag",
    indices = [Index("name", "category", unique = true)],
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** A [TagCategory.id]: a [BuiltInTagCategory] name or a user category's UUID. */
    @ColumnInfo(name = "category") val categoryId: String,
    val sortOrder: Int = 0,
    /**
     * Zero-based visual column within the category section, below [MAX_COLUMNS];
     * [sortOrder] orders tags inside a column.
     */
    @ColumnInfo(defaultValue = "0") val columnIndex: Int = 0,
) {
    companion object {
        /** Most columns a category section can be split into. */
        const val MAX_COLUMNS = 3

        val COLUMN_RANGE: IntRange = 0 until MAX_COLUMNS
    }
}
