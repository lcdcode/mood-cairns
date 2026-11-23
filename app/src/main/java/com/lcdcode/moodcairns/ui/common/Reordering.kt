package com.lcdcode.moodcairns.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.lcdcode.moodcairns.R

/** This list with the item at [from] moved to [to], or null when either is out of range. */
fun <T> List<T>.withItemMoved(from: Int, to: Int): List<T>? {
    if (from !in indices || to !in indices) return null
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * "Move up" and "Move down" accessibility actions: the TalkBack and Switch Access
 * alternative to a drag handle. Each is offered only when its callback is non-null.
 * Apply with `Modifier.semantics { customActions = ... }` on the row users focus.
 */
@Composable
fun moveActions(
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
): List<CustomAccessibilityAction> {
    val moveUpLabel = stringResource(R.string.common_move_up)
    val moveDownLabel = stringResource(R.string.common_move_down)
    return buildList {
        onMoveUp?.let { add(CustomAccessibilityAction(moveUpLabel) { it(); true }) }
        onMoveDown?.let { add(CustomAccessibilityAction(moveDownLabel) { it(); true }) }
    }
}
