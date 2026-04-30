package com.lcdcode.moodcairns.data.db

import android.graphics.Color
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.data.entity.Scale
import java.time.LocalTime

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
