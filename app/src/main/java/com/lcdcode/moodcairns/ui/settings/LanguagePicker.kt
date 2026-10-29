package com.lcdcode.moodcairns.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.settings.SupportedLocales

/** Settings row showing the current app language; [onClick] opens the picker. */
@Composable
fun LanguageSettingRow(onClick: () -> Unit, viewModel: LanguageViewModel = hiltViewModel()) {
    // The configuration changes with the language, so re-read the selection then.
    val current = remember(LocalConfiguration.current) { viewModel.currentTag() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        Text(stringResource(R.string.language_app_language))
        Text(
            current?.let(::endonym) ?: stringResource(R.string.language_system_default),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Lets the user pick the app language or follow the system's. Choosing one closes the
 * dialog; the activity is then recreated in the new language.
 */
@Composable
fun LanguagePickerDialog(onDismiss: () -> Unit, viewModel: LanguageViewModel = hiltViewModel()) {
    val configuration = LocalConfiguration.current
    val current = remember(configuration) { viewModel.currentTag() }
    val system = remember(configuration) { viewModel.systemLocale() }

    fun choose(tag: String?) {
        onDismiss()
        viewModel.select(tag)
    }

    val systemName = endonym(system.toLanguageTag())
    val systemDetail = if (isTranslated(system, SupportedLocales.tags)) {
        systemName
    } else {
        stringResource(R.string.language_system_untranslated, systemName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_app_language)) },
        text = {
            Column(
                modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState()),
            ) {
                LanguageOptionRow(
                    label = stringResource(R.string.language_system_default),
                    detail = systemDetail,
                    selected = current == null,
                    onClick = { choose(null) },
                )
                val currentOption = pickerOptionFor(current)
                viewModel.selectableTags.forEach { tag ->
                    LanguageOptionRow(
                        label = endonym(tag),
                        detail = null,
                        selected = currentOption == tag,
                        onClick = { choose(tag) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun LanguageOptionRow(
    label: String,
    detail: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The whole row is the click target, so the button itself takes no clicks.
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(label)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
