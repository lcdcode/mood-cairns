package com.lcdcode.moodcairns.ui.common

import androidx.annotation.StringRes
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.PromptSlot

/** Display name of a slot. Slots are stored by enum name, which is never shown. */
@StringRes
fun PromptSlot.displayNameRes(): Int = when (this) {
    PromptSlot.MORNING -> R.string.enum_prompt_slot_morning
    PromptSlot.EVENING -> R.string.enum_prompt_slot_evening
    PromptSlot.MANUAL -> R.string.enum_prompt_slot_manual
    PromptSlot.CUSTOM -> R.string.enum_prompt_slot_custom
}
