package com.lcdcode.moodcairns.ui.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageNamesTest {

    @Test
    fun endonym_isTheLanguageNameInThatLanguage() {
        assertEquals("English", endonym("en"))
        assertEquals("Deutsch", endonym("de"))
        assertEquals("Français", endonym("fr"))
    }

    @Test
    fun endonym_isCapitalized() {
        // Java's own display name for Spanish is lower case ("español").
        assertEquals("Español", endonym("es"))
    }

    @Test
    fun translated_matchesOnLanguageIgnoringRegion() {
        assertTrue(isTranslated(Locale.US, listOf("en")))
        assertTrue(isTranslated(Locale.forLanguageTag("en-GB"), listOf("en")))
    }

    @Test
    fun untranslated_whenNoShippedTagHasTheLanguage() {
        assertFalse(isTranslated(Locale.GERMANY, listOf("en")))
        assertFalse(isTranslated(Locale.forLanguageTag("pt-BR"), listOf("en", "es")))
    }
}
