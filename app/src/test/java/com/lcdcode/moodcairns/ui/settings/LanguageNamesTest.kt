package com.lcdcode.moodcairns.ui.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageNamesTest {

    @Test
    fun endonym_is_the_language_name_in_that_language() {
        assertEquals("English", endonym("en"))
        assertEquals("Deutsch", endonym("de"))
        assertEquals("Français", endonym("fr"))
    }

    @Test
    fun endonym_is_capitalized() {
        // Java's own display name for Spanish is lower case ("español").
        assertEquals("Español", endonym("es"))
    }

    @Test
    fun translated_matches_on_language_ignoring_region() {
        assertTrue(isTranslated(Locale.US, listOf("en")))
        assertTrue(isTranslated(Locale.forLanguageTag("en-GB"), listOf("en")))
    }

    @Test
    fun untranslated_when_no_shipped_tag_has_the_language() {
        assertFalse(isTranslated(Locale.GERMANY, listOf("en")))
        assertFalse(isTranslated(Locale.forLanguageTag("pt-BR"), listOf("en", "es")))
    }
}
