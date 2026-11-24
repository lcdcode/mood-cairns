package com.lcdcode.moodcairns.data.entity

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * One Markdown journal document per local calendar day. The day is stored as a date, not
 * derived from a timestamp, so travelling across time zones never moves an entry to
 * another day.
 *
 * Column order matters: SQLite stores columns in declaration order and long text spills
 * into a chain of overflow pages, so reading any column after [body] means walking that
 * chain. [body] is therefore last, and the history list reads only [preview], a short
 * plain-text summary written alongside the body on every save.
 */
@Entity(tableName = "journal_doc", indices = [Index(value = ["day"], unique = true)])
data class JournalDoc(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
    val preview: String,
    val body: String,
)

/**
 * Full-text index over [JournalDoc.body]. External-content FTS4: Room keeps it in sync with
 * triggers, and the text itself is stored only once (in journal_doc). unicode61 folds case
 * and diacritics for every script the app ships, including Cyrillic.
 */
@Entity(tableName = "journal_fts")
@Fts4(contentEntity = JournalDoc::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
data class JournalDocFts(val body: String)
