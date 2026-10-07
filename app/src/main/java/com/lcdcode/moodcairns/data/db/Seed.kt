package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import java.time.LocalTime

/*
 * The names and labels below are stored in the database as written, in English, and
 * double as the identity of the built-in rows: SeedNames maps them to translated
 * string resources for display. NEVER change one here, or existing installs and
 * backups stop being recognized as built-in. To change the wording users see, edit
 * the seed_* string resource instead.
 */

/** Seed tags, kept separate so migrations can seed just the [moodTags] subset. */
internal object SeedTags {
    /**
     * Moods a user might tag without tracking them as a scale; the built-in
     * scales already cover happiness, anxiety, stress, boredom, and pain.
     * Added in DB v4, so MIGRATION_3_4 seeds exactly this subset.
     *
     * Ordered by valence, most positive to most negative, with Surprise as the
     * neutral pivot. Users can reorder from the Tags screen.
     */
    val moodTags: List<Tag> = listOf(
        Tag(name = "Joy",         category = TagCategory.MOOD,     sortOrder = 0),
        Tag(name = "Excited",     category = TagCategory.MOOD,     sortOrder = 1),
        Tag(name = "Grateful",    category = TagCategory.MOOD,     sortOrder = 2),
        Tag(name = "Content",     category = TagCategory.MOOD,     sortOrder = 3),
        Tag(name = "Calm",        category = TagCategory.MOOD,     sortOrder = 4),
        Tag(name = "Surprise",    category = TagCategory.MOOD,     sortOrder = 5),
        Tag(name = "Frustrated",  category = TagCategory.MOOD,     sortOrder = 6),
        Tag(name = "Lonely",      category = TagCategory.MOOD,     sortOrder = 7),
        Tag(name = "Sadness",     category = TagCategory.MOOD,     sortOrder = 8),
        Tag(name = "Fear",        category = TagCategory.MOOD,     sortOrder = 9),
        Tag(name = "Anger",       category = TagCategory.MOOD,     sortOrder = 10),
        Tag(name = "Disgust",     category = TagCategory.MOOD,     sortOrder = 11),
    )

    /** Every seeded tag. TagCategory.OTHER is deliberately left empty. */
    val tags: List<Tag> = listOf(
        Tag(name = "Home",        category = TagCategory.PLACE,    sortOrder = 0),
        Tag(name = "Work",        category = TagCategory.PLACE,    sortOrder = 1),
        Tag(name = "School",      category = TagCategory.PLACE,    sortOrder = 2),
        Tag(name = "Commute",     category = TagCategory.PLACE,    sortOrder = 3),
        Tag(name = "Outdoors",    category = TagCategory.PLACE,    sortOrder = 4),
        Tag(name = "Travel",      category = TagCategory.PLACE,    sortOrder = 5),
        Tag(name = "Family",      category = TagCategory.PERSON,   sortOrder = 0),
        Tag(name = "Friends",     category = TagCategory.PERSON,   sortOrder = 1),
        Tag(name = "Partner",     category = TagCategory.PERSON,   sortOrder = 2),
        Tag(name = "Coworkers",   category = TagCategory.PERSON,   sortOrder = 3),
        Tag(name = "Alone",       category = TagCategory.PERSON,   sortOrder = 4),
        Tag(name = "Exercise",    category = TagCategory.ACTIVITY, sortOrder = 0),
        Tag(name = "Socializing", category = TagCategory.ACTIVITY, sortOrder = 1),
        Tag(name = "Reading",     category = TagCategory.ACTIVITY, sortOrder = 2),
        Tag(name = "Gaming",      category = TagCategory.ACTIVITY, sortOrder = 3),
        Tag(name = "Chores",      category = TagCategory.ACTIVITY, sortOrder = 4),
        Tag(name = "Rest",        category = TagCategory.ACTIVITY, sortOrder = 5),
    ) + moodTags
}

/** Plain ARGB literals rather than android.graphics.Color, so JVM tests can load this. */
internal object Seed {
    val scales: List<Scale> = listOf(
        Scale(name = "Happiness", minValue = 1, maxValue = 10, colorArgb = 0xFFF6C453.toInt(), isBuiltIn = true, sortOrder = 0),
        Scale(name = "Anxiety",   minValue = 1, maxValue = 10, colorArgb = 0xFF7D99D1.toInt(), isBuiltIn = true, sortOrder = 1),
        Scale(name = "Stress",    minValue = 1, maxValue = 10, colorArgb = 0xFFD17D7D.toInt(), isBuiltIn = true, sortOrder = 2),
        Scale(name = "Boredom",   minValue = 1, maxValue = 10, colorArgb = 0xFF9AA39A.toInt(), isBuiltIn = true, sortOrder = 3),
        Scale(name = "Pain",      minValue = 1, maxValue = 10, colorArgb = 0xFFB5651D.toInt(), isBuiltIn = true, sortOrder = 4),
    )

    val windows: List<PromptWindow> = listOf(
        PromptWindow(label = "Morning", slot = PromptSlot.MORNING, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0)),
        PromptWindow(label = "Evening", slot = PromptSlot.EVENING, startTime = LocalTime.of(20, 0), endTime = LocalTime.of(22, 0)),
    )
}
