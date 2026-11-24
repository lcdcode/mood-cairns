package com.lcdcode.moodcairns.ui.lock

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.security.BiometricOffer
import kotlinx.coroutines.launch

/**
 * Shows a pending [BiometricOffer] once the app is unlocked. Any answer, including closing the
 * dialog, consumes the offer so it is never shown twice.
 */
@Composable
fun BiometricOfferHost(viewModel: BiometricEnrollmentViewModel = hiltViewModel()) {
    val offer by viewModel.offer.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity ?: return
    val current = offer ?: return
    val scope = rememberCoroutineScope()
    var enrolling by remember { mutableStateOf(false) }

    val presentation = offerPresentation(
        offer = current,
        pinSet = viewModel.isPinSet(),
        canAuthenticate = Biometrics.canAuthenticate(activity),
    )
    when (presentation) {
        OfferPresentation.DISMISS -> LaunchedEffect(current) { viewModel.dismissOffer() }
        OfferPresentation.INFORM -> BiometricOfferDialog(
            offer = current,
            onTurnOn = null,
            onDismiss = viewModel::dismissOffer,
        )
        OfferPresentation.ASK -> if (!enrolling) {
            BiometricOfferDialog(
                offer = current,
                onTurnOn = {
                    enrolling = true
                    scope.launch {
                        enrollBiometric(activity, viewModel)
                        // A successful enrollment already cleared it; a cancel counts as "not now".
                        viewModel.dismissOffer()
                        enrolling = false
                    }
                },
                onDismiss = viewModel::dismissOffer,
            )
        }
    }
}

/** [onTurnOn] null means an information-only dialog with a single OK button. */
@Composable
private fun BiometricOfferDialog(
    offer: BiometricOffer,
    onTurnOn: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.biometric_offer_title)) },
        text = { Text(stringResource(offerBody(offer))) },
        confirmButton = {
            if (onTurnOn != null) {
                TextButton(onClick = onTurnOn) { Text(stringResource(R.string.common_enable)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
            }
        },
        dismissButton = onTurnOn?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.biometric_offer_not_now))
                }
            }
        },
    )
}

private fun offerBody(offer: BiometricOffer): Int = when (offer) {
    BiometricOffer.UPGRADE -> R.string.biometric_offer_body_upgrade
    BiometricOffer.SETUP -> R.string.biometric_offer_body_setup
    BiometricOffer.INVALIDATED -> R.string.biometric_offer_body_invalidated
}
