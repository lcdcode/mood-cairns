package com.lcdcode.moodcairns.data.db

import com.lcdcode.moodcairns.data.entity.BuiltInTagCategory
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
        Tag(name = "Joy",         categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 0),
        Tag(name = "Excited",     categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 1),
        Tag(name = "Grateful",    categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 2),
        Tag(name = "Content",     categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 3),
        Tag(name = "Calm",        categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 4),
        Tag(name = "Surprise",    categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 5),
        Tag(name = "Frustrated",  categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 6),
        Tag(name = "Lonely",      categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 7),
        Tag(name = "Sadness",     categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 8),
        Tag(name = "Fear",        categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 9),
        Tag(name = "Anger",       categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 10),
        Tag(name = "Disgust",     categoryId = BuiltInTagCategory.MOOD.id,     sortOrder = 11),
    )

    /** Every seeded tag. Other is deliberately left empty. */
    val tags: List<Tag> = listOf(
        Tag(name = "Home",        categoryId = BuiltInTagCategory.PLACE.id,    sortOrder = 0),
        Tag(name = "Work",        categoryId = BuiltInTagCategory.PLACE.id,    sortOrder = 1),
        Tag(name = "School",      categoryId = BuiltInTagCategory.PLACE.id,    sortOrder = 2),
        Tag(name = "Commute",     categoryId = BuiltInTagCategory.PLACE.id,    sortOrder = 3),
        Tag(name = "Outdoors",    categoryId = BuiltInTagCategory.PLACE.id,    sortOrder = 4),
        Tag(name = "Travel",      categoryId = BuiltInTagCategory.PLACE.id,    sortOrder = 5),
        Tag(name = "Family",      categoryId = BuiltInTagCategory.PERSON.id,   sortOrder = 0),
        Tag(name = "Friends",     categoryId = BuiltInTagCategory.PERSON.id,   sortOrder = 1),
        Tag(name = "Partner",     categoryId = BuiltInTagCategory.PERSON.id,   sortOrder = 2),
        Tag(name = "Coworkers",   categoryId = BuiltInTagCategory.PERSON.id,   sortOrder = 3),
        Tag(name = "Alone",       categoryId = BuiltInTagCategory.PERSON.id,   sortOrder = 4),
        Tag(name = "Exercise",    categoryId = BuiltInTagCategory.ACTIVITY.id, sortOrder = 0),
        Tag(name = "Socializing", categoryId = BuiltInTagCategory.ACTIVITY.id, sortOrder = 1),
        Tag(name = "Reading",     categoryId = BuiltInTagCategory.ACTIVITY.id, sortOrder = 2),
        Tag(name = "Gaming",      categoryId = BuiltInTagCategory.ACTIVITY.id, sortOrder = 3),
        Tag(name = "Chores",      categoryId = BuiltInTagCategory.ACTIVITY.id, sortOrder = 4),
        Tag(name = "Rest",        categoryId = BuiltInTagCategory.ACTIVITY.id, sortOrder = 5),
    ) + moodTags
}

/**
 * The built-in categories in their default order, with no custom names. Seeded on
 * fresh installs, by MIGRATION_5_6, and when importing a backup that predates them.
 */
internal object SeedTagCategories {
    val all: List<TagCategory> = BuiltInTagCategory.entries.map { builtIn ->
        TagCategory(id = builtIn.id, label = null, sortOrder = builtIn.ordinal)
    }
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
