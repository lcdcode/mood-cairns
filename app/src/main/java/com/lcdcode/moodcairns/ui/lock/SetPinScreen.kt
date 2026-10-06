package com.lcdcode.moodcairns.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.asString

@Composable
fun SetPinScreen(viewModel: SetPinViewModel = hiltViewModel()) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    var showNoPinWarning by remember { mutableStateOf(false) }

    if (showNoPinWarning) {
        NoPinWarningDialog(
            title = stringResource(R.string.no_pin_warning_title_setup),
            onAccept = {
                showNoPinWarning = false
                viewModel.continueWithoutPin()
            },
            onDismiss = { showNoPinWarning = false },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.set_pin_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.set_pin_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.pin,
            onValueChange = viewModel::onPinChanged,
            label = {
                Text(
                    pluralStringResource(
                        R.plurals.set_pin_pin_label,
                        SetPinViewModel.MAX_PIN_LEN,
                        SetPinViewModel.MIN_PIN_LEN,
                        SetPinViewModel.MAX_PIN_LEN,
                    ),
                )
            },
            singleLine = true,
            enabled = !state.saving,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.confirm,
            onValueChange = viewModel::onConfirmChanged,
            label = { Text(stringResource(R.string.set_pin_confirm_label)) },
            singleLine = true,
            enabled = !state.saving,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it.asString()) } },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::submit,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.set_pin_save))
            }
        }

        TextButton(
            onClick = { showNoPinWarning = true },
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.set_pin_continue_without))
        }
    }
}
