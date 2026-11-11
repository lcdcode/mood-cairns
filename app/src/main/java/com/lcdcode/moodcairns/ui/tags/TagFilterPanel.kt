package com.lcdcode.moodcairns.ui.tags

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Tag
import com.lcdcode.moodcairns.data.entity.TagCategory

private val HeaderMinHeight = 48.dp
private const val EXPANDED_CHEVRON_DEGREES = 180f

/** Debug name for Android Studio's animation inspector; never shown to users. */
private const val CHEVRON_ANIMATION_LABEL = "tagFilterChevron"

/**
 * Tag filter for History and Charts: a header that toggles the tag chips, starting
 * collapsed. While tags are selected the header shows how many and offers Clear, so
 * an active filter stays visible and removable when the chips are hidden.
 */
@Composable
fun TagFilterPanel(
    categories: List<TagCategory>,
    tags: List<Tag>,
    selectedTagIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        TagFilterHeader(
            selectedCount = selectedTagIds.size,
            expanded = expanded,
            onToggleExpanded = { expanded = !expanded },
            onClear = onClear,
        )
        AnimatedVisibility(visible = expanded) {
            TagChipGroups(
                categories = categories,
                tags = tags,
                selectedTagIds = selectedTagIds,
                onToggle = onToggle,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
private fun TagFilterHeader(
    selectedCount: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onClear: () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) EXPANDED_CHEVRON_DEGREES else 0f,
        label = CHEVRON_ANIMATION_LABEL,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = HeaderMinHeight)
            .clickable(onClick = onToggleExpanded)
            // TalkBack announces expanded/collapsed from whichever action is offered.
            .semantics {
                if (expanded) {
                    collapse { onToggleExpanded(); true }
                } else {
                    expand { onToggleExpanded(); true }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.common_tags_label),
            style = MaterialTheme.typography.labelMedium,
        )
        if (selectedCount > 0) {
            Text(
                pluralStringResource(
                    R.plurals.tag_filter_selected_count,
                    selectedCount,
                    selectedCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.weight(1f))
        if (selectedCount > 0) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.common_clear)) }
        }
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(chevronRotation),
        )
    }
}
