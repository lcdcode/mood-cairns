package com.lcdcode.moodcairns.backup

import com.lcdcode.moodcairns.data.dao.EntryWithValues
import com.lcdcode.moodcairns.data.entity.Entry
import com.lcdcode.moodcairns.data.entity.EntryValue
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime

class MoodCsvTest {

    private fun scale(id: Long, name: String, sortOrder: Int = 0, archived: Boolean = false) =
        Scale(
            id = id, name = name, minValue = 0, maxValue = 10, colorArgb = 0,
            archived = archived, sortOrder = sortOrder,
        )

    private fun entry(
        id: Long,
        at: String,
        slot: PromptSlot = PromptSlot.MANUAL,
        windowId: Long? = null,
        note: String? = null,
        values: List<EntryValue> = emptyList(),
        tags: List<Tag> = emptyList(),
    ) = EntryWithValues(
        entry = Entry(
            id = id,
            recordedAt = Instant.parse(at),
            slot = slot,
            promptWindowId = windowId,
            note = note,
        ),
        values = values,
        tags = tags,
    )

    private fun rows(csv: String) = csv.split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun header_listsFixedColumnsThenScalesBySortOrder() {
        val scales = listOf(
            scale(1, "Energy", sortOrder = 2),
            scale(2, "Mood", sortOrder = 1),
        )
        val csv = MoodCsv.build(scales, emptyList(), emptyList())

        assertEquals("recordedAt,slot,window,note,tags,Mood,Energy", rows(csv).first())
    }

    @Test
    fun row_mapsValuesToColumns_blankWhenMissing() {
        val scales = listOf(scale(1, "Mood", sortOrder = 1), scale(2, "Energy", sortOrder = 2))
        val entries = listOf(
            entry(
                id = 10, at = "2026-01-01T08:00:00Z", slot = PromptSlot.MORNING,
                values = listOf(EntryValue(10, 1, 4f)),
            ),
        )
        val csv = MoodCsv.build(scales, emptyList(), entries)

        // Mood=4, Energy blank; window/note/tags empty.
        assertEquals("2026-01-01T08:00:00Z,MORNING,,,,4,", rows(csv)[1])
    }

    @Test
    fun rows_areSortedNewestFirst() {
        val entries = listOf(
            entry(1, "2026-01-01T08:00:00Z"),
            entry(2, "2026-01-03T08:00:00Z"),
            entry(3, "2026-01-02T08:00:00Z"),
        )
        val csv = MoodCsv.build(emptyList(), emptyList(), entries)
        val body = rows(csv).drop(1)

        assertEquals("2026-01-03T08:00:00Z", body[0].substringBefore(','))
        assertEquals("2026-01-02T08:00:00Z", body[1].substringBefore(','))
        assertEquals("2026-01-01T08:00:00Z", body[2].substringBefore(','))
    }

    @Test
    fun window_isResolvedToLabel() {
        val windows = listOf(
            PromptWindow(
                id = 7, label = "Evening", slot = PromptSlot.EVENING,
                startTime = LocalTime.of(20, 0), endTime = LocalTime.of(22, 0), enabled = true,
            ),
        )
        val entries = listOf(entry(1, "2026-01-01T21:00:00Z", windowId = 7))
        val csv = MoodCsv.build(emptyList(), windows, entries)

        assertTrue(rows(csv)[1].contains(",Evening,"))
    }

    @Test
    fun fields_areRfc4180Escaped() {
        val entries = listOf(
            entry(1, "2026-01-01T08:00:00Z", note = "hard, \"tough\" day\nreally"),
        )
        val csv = MoodCsv.build(emptyList(), emptyList(), entries)

        assertTrue(csv.contains("\"hard, \"\"tough\"\" day\nreally\""))
    }

    @Test
    fun deletedScale_stillGetsColumn_soDataIsNotLost() {
        // Scale 99 was recorded against but no longer declared.
        val entries = listOf(
            entry(1, "2026-01-01T08:00:00Z", values = listOf(EntryValue(1, 99, 3f))),
        )
        val csv = MoodCsv.build(emptyList(), emptyList(), entries)

        assertEquals("recordedAt,slot,window,note,tags,scale_99", rows(csv).first())
        assertTrue(rows(csv)[1].endsWith(",3"))
    }

    @Test
    fun tags_joinedWithSemicolonSpace() {
        val entries = listOf(
            entry(
                1, "2026-01-01T08:00:00Z",
                tags = listOf(
                    Tag(id = 1, name = "Home", category = TagCategory.PLACE),
                    Tag(id = 2, name = "Family", category = TagCategory.PERSON),
                ),
            ),
        )
        val csv = MoodCsv.build(emptyList(), emptyList(), entries)

        assertEquals("2026-01-01T08:00:00Z,MANUAL,,,Home; Family", rows(csv)[1])
    }

    @Test
    fun tagNameWithComma_isRfc4180Quoted() {
        val entries = listOf(
            entry(
                1, "2026-01-01T08:00:00Z",
                tags = listOf(Tag(id = 1, name = "work, late shift", category = TagCategory.ACTIVITY)),
            ),
        )
        val csv = MoodCsv.build(emptyList(), emptyList(), entries)

        assertTrue(rows(csv)[1].endsWith(",\"work, late shift\""))
    }

    @Test
    fun wholeNumbers_dropTrailingDecimal_realDecimalsKept() {
        val scales = listOf(scale(1, "A", sortOrder = 1), scale(2, "B", sortOrder = 2))
        val entries = listOf(
            entry(
                1, "2026-01-01T08:00:00Z",
                values = listOf(EntryValue(1, 1, 5f), EntryValue(1, 2, 2.5f)),
            ),
        )
        val csv = MoodCsv.build(scales, emptyList(), entries)

        assertTrue(rows(csv)[1].endsWith(",5,2.5"))
    }
}
