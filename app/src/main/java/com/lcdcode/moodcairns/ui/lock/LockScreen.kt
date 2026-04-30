package com.lcdcode.moodcairns.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity
    var promptedBiometric by remember { mutableStateOf(false) }

    LaunchedEffect(activity, biometricEnabled) {
        if (!promptedBiometric && biometricEnabled && activity != null && Biometrics.canAuthenticate(activity)) {
            promptedBiometric = true
            Biometrics.prompt(
                activity = activity,
                onSuccess = { viewModel.onBiometricSuccess() },
                onFailure = { /* fall through to PIN */ },
                onUsePin = { /* fall through to PIN */ },
            )
        }
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Enter PIN to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = state.pin,
                onValueChange = viewModel::onPinChanged,
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = state.error != null,
                supportingText = state.error?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = viewModel::submit, modifier = Modifier.fillMaxWidth()) {
                Text("Unlock")
            }

            if (biometricEnabled && activity != null && Biometrics.canAuthenticate(activity)) {
                TextButton(onClick = {
                    Biometrics.prompt(
                        activity = activity,
                        onSuccess = { viewModel.onBiometricSuccess() },
                        onFailure = {},
                        onUsePin = {},
                    )
                }) { Text("Use biometric") }
            }
        }
    }
}
