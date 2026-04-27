package com.moodcairns.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "entry_value",
    primaryKeys = ["entryId", "scaleId"],
    indices = [Index("scaleId")],
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Scale::class,
            parentColumns = ["id"],
            childColumns = ["scaleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class EntryValue(
    val entryId: Long,
    val scaleId: Long,
    val value: Int,
)
