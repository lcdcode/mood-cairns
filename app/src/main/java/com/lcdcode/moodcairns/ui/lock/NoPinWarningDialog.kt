package com.lcdcode.moodcairns.ui.lock

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lcdcode.moodcairns.R

/**
 * Approved risk warning shown before the app is configured without a PIN, used
 * at both first-time setup ("Continue without a PIN?") and PIN removal from the
 * Change PIN screen ("Remove your PIN?"). The body copy (no_pin_warning_body) is
 * identical at both sites; only [title] differs.
 */
@Composable
fun NoPinWarningDialog(
    title: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(stringResource(R.string.no_pin_warning_body)) },
        confirmButton = {
            TextButton(onClick = onAccept) { Text(stringResource(R.string.no_pin_warning_accept)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}
