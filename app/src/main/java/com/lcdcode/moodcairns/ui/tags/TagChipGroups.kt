package com.lcdcode.moodcairns.ui.tags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory
import com.lcdcode.moodcairns.ui.common.displayName

private val ChipSpacing = 8.dp

/**
 * Selectable tag chips, one section per category that has tags. A section whose tags
 * all sit in the first column flows chips across the width; one that uses more
 * columns shows them side by side, each stacking its chips top to bottom.
 */
@Composable
fun TagChipGroups(
    categories: List<TagCategory>,
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byCategory = remember(tags, categories) { tags.groupedByCategory(categories) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        categories.forEach { category ->
            val categoryTags = byCategory[category.id] ?: return@forEach
            Text(category.displayName(), style = MaterialTheme.typography.labelMedium)
            val columns = remember(categoryTags) { categoryTags.inColumns() }
            if (columns.size == 1) {
                FlowingChips(columns.single(), selectedTagIds, onToggle)
            } else {
                ColumnChips(columns, selectedTagIds, onToggle)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowingChips(tags: List<Tag>, selectedTagIds: Set<Long>, onToggle: (Long) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
        tags.forEach { TagChip(it, it.id in selectedTagIds, onToggle) }
    }
}

@Composable
private fun ColumnChips(
    columns: List<List<Tag>>,
    selectedTagIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ChipSpacing),
    ) {
        columns.forEach { columnTags ->
            Column(modifier = Modifier.weight(1f)) {
                columnTags.forEach { TagChip(it, it.id in selectedTagIds, onToggle) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagChip(tag: Tag, selected: Boolean, onToggle: (Long) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onToggle(tag.id) },
        label = { Text(tag.displayName()) },
    )
}
