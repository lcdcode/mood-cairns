package com.lcdcode.moodcairns.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.ui.common.moveActions
import com.lcdcode.moodcairns.ui.common.withItemMoved
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private val RowMinHeight = 48.dp

/** [categories] with Other moved to the end, the place it always holds. */
fun List<TagCategory>.withOtherLast(): List<TagCategory> =
    sortedBy { it.id == TagCategory.OTHER_ID }

/**
 * This order with the category at [from] moved to [to], or null when either index is
 * out of range or holds Other, which never moves and cannot be displaced.
 */
fun List<TagCategory>.withCategoryMoved(from: Int, to: Int): List<TagCategory>? {
    val touchesOther = getOrNull(from)?.id == TagCategory.OTHER_ID ||
        getOrNull(to)?.id == TagCategory.OTHER_ID
    return if (touchesOther) null else withItemMoved(from, to)
}

/**
 * The Tags screen's category reorder mode: one row per category with a drag handle,
 * plus move actions for accessibility services. Other has neither and stays last.
 * Each completed move is passed to [onReorder] as the full id order.
 */
@Composable
fun CategoryOrderList(
    categories: List<TagCategory>,
    onReorder: (orderedIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf(categories.withOtherLast()) }
    LaunchedEffect(categories, isDragging) {
        if (!isDragging) order = categories.withOtherLast()
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIdx = order.indexOfFirst { it.id == from.key }
        val toIdx = order.indexOfFirst { it.id == to.key }
        order = order.withCategoryMoved(fromIdx, toIdx) ?: return@rememberReorderableLazyListState
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun moveBy(index: Int, offset: Int): (() -> Unit)? {
        val moved = order.withCategoryMoved(index, index + offset) ?: return null
        return {
            order = moved
            onReorder(moved.map { it.id })
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(order, key = { _, category -> category.id }) { index, category ->
            val movable = category.id != TagCategory.OTHER_ID
            ReorderableItem(reorderableState, key = category.id, enabled = movable) { _ ->
                val dragModifier = Modifier.draggableHandle(
                    onDragStarted = { _ -> isDragging = true },
                    onDragStopped = {
                        onReorder(order.map { it.id })
                        isDragging = false
                    },
                )
                CategoryOrderRow(
                    name = category.displayName(),
                    dragHandleModifier = dragModifier.takeIf { movable },
                    actions = moveActions(
                        onMoveUp = moveBy(index, -1),
                        onMoveDown = moveBy(index, 1),
                    ),
                )
            }
        }
    }
}

/** A category name with its drag handle; no handle when [dragHandleModifier] is null. */
@Composable
private fun CategoryOrderRow(
    name: String,
    dragHandleModifier: Modifier?,
    actions: List<CustomAccessibilityAction>,
) {
    // Not clickable, so merge explicitly: TalkBack reads the name and actions as one item.
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            customActions = actions
        },
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .heightIn(min = RowMinHeight)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (dragHandleModifier != null) {
                ReorderHandle(dragHandleModifier)
            }
        }
    }
}

/**
 * A drag handle hidden from accessibility services: dragging needs a pointer, so they
 * get the row's move actions instead of a button that does nothing.
 */
@Composable
internal fun ReorderHandle(modifier: Modifier) {
    IconButton(modifier = modifier.clearAndSetSemantics {}, onClick = {}) {
        Icon(
            Icons.Default.DragHandle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
