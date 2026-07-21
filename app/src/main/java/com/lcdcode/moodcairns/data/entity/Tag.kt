package com.lcdcode.moodcairns.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TagCategory { PLACE, PERSON, ACTIVITY }

@Entity(
    tableName = "tag",
    indices = [Index("name", "category", unique = true)],
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: TagCategory,
    val sortOrder: Int = 0,
)
