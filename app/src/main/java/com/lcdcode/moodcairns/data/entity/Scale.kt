package com.lcdcode.moodcairns.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scale",
    indices = [Index("name", unique = true)],
)
data class Scale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minValue: Int,
    val maxValue: Int,
    val step: Float = 1f,
    val colorArgb: Int,
    val isBuiltIn: Boolean = false,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    /** Lower values are better; flips the entry slider and Absolute chart mode. */
    @ColumnInfo(defaultValue = "0") val inverted: Boolean = false,
)
