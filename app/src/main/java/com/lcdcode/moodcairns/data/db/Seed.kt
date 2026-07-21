package com.lcdcode.moodcairns.data.db

import android.graphics.Color
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import java.time.LocalTime

/**
 * Seed tags live outside [Seed] because that object's scale palette calls
 * android.graphics.Color at init, which throws in JVM unit tests. This object
 * is pure so migration tests can assert against the canonical list.
 */
internal object SeedTags {
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
    )
}

internal object Seed {
    val scales: List<Scale> = listOf(
        Scale(name = "Happiness", minValue = 1, maxValue = 10, colorArgb = Color.parseColor("#F6C453"), isBuiltIn = true, sortOrder = 0),
        Scale(name = "Anxiety",   minValue = 1, maxValue = 10, colorArgb = Color.parseColor("#7D99D1"), isBuiltIn = true, sortOrder = 1),
        Scale(name = "Stress",    minValue = 1, maxValue = 10, colorArgb = Color.parseColor("#D17D7D"), isBuiltIn = true, sortOrder = 2),
        Scale(name = "Boredom",   minValue = 1, maxValue = 10, colorArgb = Color.parseColor("#9AA39A"), isBuiltIn = true, sortOrder = 3),
        Scale(name = "Pain",      minValue = 1, maxValue = 10, colorArgb = Color.parseColor("#B5651D"), isBuiltIn = true, sortOrder = 4),
    )

    val windows: List<PromptWindow> = listOf(
        PromptWindow(label = "Morning", slot = PromptSlot.MORNING, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(10, 0)),
        PromptWindow(label = "Evening", slot = PromptSlot.EVENING, startTime = LocalTime.of(20, 0), endTime = LocalTime.of(22, 0)),
    )
}
