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
     * Visual column within the category section; [sortOrder] orders tags inside a
     * column. Stored for an upcoming layout feature, not yet read by the UI.
     */
    @ColumnInfo(defaultValue = "0") val columnIndex: Int = 0,
)
