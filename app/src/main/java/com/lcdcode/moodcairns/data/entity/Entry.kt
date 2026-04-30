package com.lcdcode.moodcairns.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "entry",
    indices = [Index("recordedAt"), Index("promptWindowId")],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAt: Instant,
    val slot: PromptSlot,
    val promptWindowId: Long? = null,
    val note: String? = null,
)
