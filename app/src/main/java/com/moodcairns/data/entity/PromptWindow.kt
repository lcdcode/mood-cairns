package com.moodcairns.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(tableName = "prompt_window")
data class PromptWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val slot: PromptSlot,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val enabled: Boolean = true,
)
