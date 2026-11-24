package com.lcdcode.moodcairns.ui.lock

import com.lcdcode.moodcairns.security.BiometricOffer

/** How a pending [BiometricOffer] is presented after unlocking. */
enum class OfferPresentation {
    /** Ask whether to turn biometric unlock on. */
    ASK,

    /** Tell the user what happened, without offering to turn it on (the device can't). */
    INFORM,

    /** Drop the offer without showing anything. */
    DISMISS,
}

/**
 * Decides how to present [offer]. Without a PIN biometric unlock gates nothing. Without a
 * usable strong biometric there is nothing to turn on, but an INVALIDATED notice is a security
 * signal (someone may have added a fingerprint), so it is still shown.
 */
fun offerPresentation(
    offer: BiometricOffer,
    pinSet: Boolean,
    canAuthenticate: Boolean,
): OfferPresentation = when {
    !pinSet -> OfferPresentation.DISMISS
    canAuthenticate -> OfferPresentation.ASK
    offer == BiometricOffer.INVALIDATED -> OfferPresentation.INFORM
    else -> OfferPresentation.DISMISS
}
