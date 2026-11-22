package com.lcdcode.moodcairns.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.lock.BiometricEnrollmentViewModel
import com.lcdcode.moodcairns.ui.lock.Biometrics
import com.lcdcode.moodcairns.ui.lock.enrollBiometric
import kotlinx.coroutines.launch

/**
 * Biometric unlock switch. Turning it on runs an authenticated enrollment (the switch stays
 * off if the prompt is cancelled); turning it off is always allowed, even when the device no
 * longer has a usable biometric.
 */
@Composable
fun BiometricUnlockRow(
    enabled: Boolean,
    onDisable: () -> Unit,
    onEnrollmentFinished: () -> Unit,
    enrollment: BiometricEnrollmentViewModel = hiltViewModel(),
) {
    val activity = LocalContext.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    var enrolling by remember { mutableStateOf(false) }
    val available = activity != null && Biometrics.canAuthenticate(activity)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_biometric_unlock),
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = enabled,
                enabled = !enrolling && (enabled || available),
                onCheckedChange = { turnOn ->
                    when {
                        !turnOn -> onDisable()
                        activity != null -> {
                            enrolling = true
                            scope.launch {
                                enrollBiometric(activity, enrollment)
                                enrolling = false
                                onEnrollmentFinished()
                            }
                        }
                    }
                },
            )
        }
        if (!enabled && !available) {
            Text(
                stringResource(R.string.settings_biometric_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
