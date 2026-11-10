package com.lcdcode.moodcairns.ui.tags

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.TagCategory

/**
 * Names a new category or renames an existing one. For a built-in, [defaultName] is
 * its translated name: shown as the placeholder, and a blank save restores it. A user
 * category has no default, so a blank name shows an error instead. [onDelete], when
 * given, adds a Delete button.
 */
@Composable
fun CategoryNameDialog(
    title: String,
    initialName: String,
    defaultName: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var draft by rememberSaveable { mutableStateOf(initialName) }
    var showMissingName by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { new ->
                    if (new.codePointCount(0, new.length) <= TagCategory.MAX_LABEL_LENGTH) {
                        draft = new
                        showMissingName = false
                    }
                },
                label = { Text(stringResource(R.string.common_name_label)) },
                placeholder = defaultName?.let { { Text(it) } },
                singleLine = true,
                isError = showMissingName,
                supportingText = if (showMissingName) {
                    { Text(stringResource(R.string.error_name_required)) }
                } else {
                    null
                },
                trailingIcon = {
                    if (draft.isNotEmpty()) {
                        IconButton(onClick = { draft = "" }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = stringResource(R.string.common_clear),
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isMissingCategoryName(draft, defaultName)) {
                        showMissingName = true
                    } else {
                        onSave(draft)
                    }
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text(stringResource(R.string.common_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/**
 * Confirms deleting category [name], whose [tagCount] tags move to [otherName].
 */
@Composable
fun DeleteCategoryDialog(
    name: String,
    tagCount: Int,
    otherName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_category_delete_title)) },
        text = {
            Text(
                if (tagCount == 0) {
                    stringResource(R.string.tag_category_delete_body_empty, name)
                } else {
                    pluralStringResource(
                        R.plurals.tag_category_delete_body,
                        tagCount,
                        name,
                        tagCount,
                        otherName,
                    )
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(stringResource(R.string.common_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
