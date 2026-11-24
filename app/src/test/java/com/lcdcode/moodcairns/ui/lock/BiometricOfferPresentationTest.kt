package com.lcdcode.moodcairns.ui.lock

import com.lcdcode.moodcairns.security.BiometricOffer
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricOfferPresentationTest {

    @Test
    fun without_a_pin_every_offer_is_dropped() {
        BiometricOffer.entries.forEach { offer ->
            listOf(true, false).forEach { canAuth ->
                assertEquals(
                    "$offer/$canAuth",
                    OfferPresentation.DISMISS,
                    offerPresentation(offer, pinSet = false, canAuthenticate = canAuth),
                )
            }
        }
    }

    @Test
    fun with_a_pin_and_a_usable_biometric_every_offer_asks() {
        BiometricOffer.entries.forEach { offer ->
            assertEquals(
                "$offer",
                OfferPresentation.ASK,
                offerPresentation(offer, pinSet = true, canAuthenticate = true),
            )
        }
    }

    @Test
    fun an_invalidation_notice_is_still_shown_when_biometrics_cannot_be_used() {
        assertEquals(
            OfferPresentation.INFORM,
            offerPresentation(BiometricOffer.INVALIDATED, pinSet = true, canAuthenticate = false),
        )
    }

    @Test
    fun upgrade_and_setup_offers_are_dropped_when_biometrics_cannot_be_used() {
        listOf(BiometricOffer.UPGRADE, BiometricOffer.SETUP).forEach { offer ->
            assertEquals(
                "$offer",
                OfferPresentation.DISMISS,
                offerPresentation(offer, pinSet = true, canAuthenticate = false),
            )
        }
    }
}
