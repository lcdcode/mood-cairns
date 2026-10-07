package com.lcdcode.moodcairns.ui.lock

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.asString
import java.util.Locale

@Composable
fun LockScreen(
    biometricEnabled: Boolean,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity
    var promptedBiometric by remember { mutableStateOf(false) }

    val canBiometric = biometricEnabled && viewModel.canBiometricUnlock()

    LaunchedEffect(activity, canBiometric) {
        if (!promptedBiometric && canBiometric && activity != null && Biometrics.canAuthenticate(activity)) {
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
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            val lockoutMs = state.lockoutRemainingMs
            val locale = LocalConfiguration.current.locales[0]
            val lockoutText = lockoutMs?.let { formatLockoutDuration(it, locale) }
            val supporting = lockoutText?.let { stringResource(R.string.lock_try_again_in, it) }
                ?: state.error?.asString()

            OutlinedTextField(
                value = state.pin,
                onValueChange = viewModel::onPinChanged,
                label = { Text(stringResource(R.string.lock_pin_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = supporting != null,
                supportingText = supporting?.let { { Text(it) } },
                enabled = lockoutMs == null && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::submit,
                enabled = lockoutMs == null && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (lockoutText != null) {
                            stringResource(R.string.lock_locked_for, lockoutText)
                        } else {
                            stringResource(R.string.lock_unlock)
                        },
                    )
                }
            }

            if (!state.busy && canBiometric && activity != null && Biometrics.canAuthenticate(activity)) {
                TextButton(onClick = {
                    Biometrics.prompt(
                        activity = activity,
                        onSuccess = { viewModel.onBiometricSuccess() },
                        onFailure = {},
                        onUsePin = {},
                    )
                }) { Text(stringResource(R.string.lock_use_biometric)) }
            }
        }
    }
}

/** E.g. "1m 30s" in English; ICU supplies the unit names and order for [locale]. */
private fun formatLockoutDuration(ms: Long, locale: Locale): String {
    val (minutes, seconds) = lockoutMinutesSeconds(ms)
    val measures = buildList {
        if (minutes > 0L) add(Measure(minutes, MeasureUnit.MINUTE))
        if (seconds > 0L || minutes == 0L) add(Measure(seconds, MeasureUnit.SECOND))
    }
    return MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.NARROW)
        .formatMeasures(*measures.toTypedArray())
}

/** Remaining lockout as (minutes, seconds), rounded up to at least one second. */
internal fun lockoutMinutesSeconds(ms: Long): Pair<Long, Long> {
    val totalSec = ((ms + MS_PER_SECOND - 1) / MS_PER_SECOND).coerceAtLeast(1)
    return totalSec / SECONDS_PER_MINUTE to totalSec % SECONDS_PER_MINUTE
}

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
