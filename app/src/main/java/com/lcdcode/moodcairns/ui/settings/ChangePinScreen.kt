package com.lcdcode.moodcairns.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.lock.NoPinWarningDialog
import com.lcdcode.moodcairns.ui.common.asString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinScreen(
    onBack: () -> Unit,
    viewModel: ChangePinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    // Empty new PIN while a PIN exists is the "remove PIN" gesture.
    val isRemoving = state.hasExistingPin && state.next.isEmpty() && state.confirm.isEmpty()
    val title = stringResource(
        if (state.hasExistingPin) {
            R.string.change_pin_title_change
        } else {
            R.string.change_pin_title_set
        },
    )
    val actionLabel = stringResource(
        when {
            state.saving -> R.string.common_saving
            !state.hasExistingPin -> R.string.change_pin_action_set
            isRemoving -> R.string.change_pin_action_remove
            else -> R.string.change_pin_action_change
        },
    )

    if (state.showRemoveWarning) {
        NoPinWarningDialog(
            title = stringResource(R.string.no_pin_warning_title_remove),
            onAccept = viewModel::confirmRemovePin,
            onDismiss = viewModel::cancelRemovePin,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.hasExistingPin) {
                PinField(
                    value = state.current,
                    onValueChange = viewModel::setCurrent,
                    label = stringResource(R.string.change_pin_current_label),
                )
            }
            PinField(
                value = state.next,
                onValueChange = viewModel::setNext,
                label = stringResource(R.string.change_pin_new_label),
            )
            PinField(
                value = state.confirm,
                onValueChange = viewModel::setConfirm,
                label = stringResource(R.string.change_pin_confirm_label),
            )

            if (state.hasExistingPin) {
                Text(
                    stringResource(R.string.change_pin_remove_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.error?.let {
                Text(it.asString(), color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}
