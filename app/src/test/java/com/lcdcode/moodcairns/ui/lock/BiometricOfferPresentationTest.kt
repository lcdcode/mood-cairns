package com.lcdcode.moodcairns.ui.lock

import com.lcdcode.moodcairns.security.BiometricOffer
import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricOfferPresentationTest {

    @Test
    fun `without a PIN every offer is dropped`() {
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
    fun `with a PIN and a usable biometric every offer asks`() {
        BiometricOffer.entries.forEach { offer ->
            assertEquals(
                "$offer",
                OfferPresentation.ASK,
                offerPresentation(offer, pinSet = true, canAuthenticate = true),
            )
        }
    }

    @Test
    fun `an invalidation notice is still shown when biometrics cannot be used`() {
        assertEquals(
            OfferPresentation.INFORM,
            offerPresentation(BiometricOffer.INVALIDATED, pinSet = true, canAuthenticate = false),
        )
    }

    @Test
    fun `upgrade and setup offers are dropped when biometrics cannot be used`() {
        listOf(BiometricOffer.UPGRADE, BiometricOffer.SETUP).forEach { offer ->
            assertEquals(
                "$offer",
                OfferPresentation.DISMISS,
                offerPresentation(offer, pinSet = true, canAuthenticate = false),
            )
        }
    }
}
