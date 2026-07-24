package com.lcdcode.moodcairns.ui.common

import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.data.entity.PromptWindow

/**
 * Human-readable label for an entry's slot: the prompt window's label when the
 * entry belongs to a still-existing window, otherwise the slot name.
 */
fun slotLabel(
    slot: PromptSlot,
    promptWindowId: Long?,
    windows: Map<Long, PromptWindow>,
): String =
    promptWindowId?.let { windows[it]?.label }
        ?: slot.name.lowercase().replaceFirstChar { it.uppercase() }

@Composable
fun SlotChip(label: String) {
    AssistChip(onClick = {}, label = { Text(label) })
}
