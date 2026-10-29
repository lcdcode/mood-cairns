package com.lcdcode.moodcairns.ui.settings

import com.lcdcode.moodcairns.settings.SupportedLocales
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Picking a language must keep the device's regional formats when it can. */
class LanguageChoiceTest {

    private fun locale(tag: String) = Locale.forLanguageTag(tag)

    @Test
    fun choosingTheDeviceLanguage_keepsTheDeviceRegion() {
        assertEquals("en-GB", tagToApply("en", locale("en-GB")))
        assertEquals("es-MX", tagToApply("es", locale("es-MX")))
        assertEquals("es-419", tagToApply("es", locale("es-419")))
    }

    @Test
    fun regionalPreferences_areKept() {
        assertEquals("en-GB-u-mu-celsius", tagToApply("en", locale("en-GB-u-mu-celsius")))
    }

    @Test
    fun choosingAnotherLanguage_appliesItAsIs() {
        assertEquals("es", tagToApply("es", locale("en-GB")))
        assertEquals("en", tagToApply("en", locale("de-DE")))
    }

    @Test
    fun pseudolocale_isNeverRegionalized() {
        val pseudo = SupportedLocales.PSEUDO_LOCALE
        assertEquals(pseudo, tagToApply(pseudo, locale("en-US")))
        // A device set to en-XA picking English gets plain English, not the pseudolocale.
        assertEquals("en", tagToApply("en", locale(SupportedLocales.PSEUDO_LOCALE)))
    }

    @Test
    fun appliedRegionalTag_selectsItsPickerOption() {
        assertEquals("en", pickerOptionFor("en-GB"))
        assertEquals("es", pickerOptionFor("es-MX"))
        assertEquals("es", pickerOptionFor("es"))
        assertNull(pickerOptionFor(null))
        assertNull(pickerOptionFor("fr-FR"))
    }

    @Test
    fun endonym_omitsRegionalPreferenceExtensions() {
        assertEquals(endonym("en-GB"), endonym("en-GB-u-mu-celsius"))
    }
}
