package com.lcdcode.moodcairns.ui.lock

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Approved risk warning shown before the app is configured without a PIN, used
 * at both first-time setup ("Continue without a PIN?") and PIN removal from the
 * Change PIN screen ("Remove your PIN?"). The body copy is identical at both
 * sites; only [title] differs.
 */
const val NO_PIN_WARNING_BODY: String =
    "Without a PIN, anyone who can use this unlocked device can open Mood Cairns " +
        "and read all of your entries. Your data is still encrypted on disk, but " +
        "only by your device's secure keystore - the extra protection a PIN provides " +
        "will be removed, and biometric unlock will be turned off. You can set a PIN " +
        "again any time in Settings."

@Composable
fun NoPinWarningDialog(
    title: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(NO_PIN_WARNING_BODY) },
        confirmButton = {
            TextButton(onClick = onAccept) { Text("Accept and continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
