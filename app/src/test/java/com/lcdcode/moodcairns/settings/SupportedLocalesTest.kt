package com.lcdcode.moodcairns.settings

import com.lcdcode.moodcairns.i18n.TestResources
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SupportedLocalesTest {

    @Test
    fun tags_match_locale_config_xml() {
        assertEquals(
            "SupportedLocales.tags and res/xml/locale_config.xml must list the same languages",
            localeConfigTags("main").toSet(),
            SupportedLocales.tags.toSet(),
        )
    }

    @Test
    fun debug_locale_config_is_main_plus_pseudolocale() {
        assertEquals(
            "debug locale_config.xml must list the main languages plus $PSEUDO_LOCALE",
            localeConfigTags("main").toSet() + PSEUDO_LOCALE,
            localeConfigTags("debug").toSet(),
        )
    }

    /**
     * A strings file whose language is missing from locale_config.xml is silently
     * stripped from the APK by the build's resource filter, so it must fail here.
     */
    @Test
    fun translation_folders_match_shipped_languages() {
        val folders = TestResources.resDir
            .listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
            .filter { File(it, "strings.xml").exists() }
            .map { it.name.removePrefix("values-") }
            .toSet()
        val expected = SupportedLocales.tags
            .filter { it != SOURCE_LANGUAGE }
            .map(::toResourceQualifier)
            .toSet()
        assertEquals(
            "values-*/strings.xml folders must match the non-English SupportedLocales.tags",
            expected,
            folders,
        )
    }

    @Test
    fun resource_qualifiers_follow_android_naming() {
        assertEquals("de", toResourceQualifier("de"))
        assertEquals("pt-rBR", toResourceQualifier("pt-BR"))
        assertEquals("b+zh+Hans", toResourceQualifier("zh-Hans"))
    }

    @Test
    fun tags_are_unique() {
        assertEquals(SupportedLocales.tags.distinct(), SupportedLocales.tags)
    }

    @Test
    fun tags_are_well_formed_bcp47() {
        SupportedLocales.tags.forEach { tag ->
            assertEquals("Not a canonical BCP-47 tag: $tag", tag, Locale.forLanguageTag(tag).toLanguageTag())
        }
    }

    @Test
    fun english_fallback_is_always_shipped() {
        assertTrue(SupportedLocales.tags.contains("en"))
    }

    @Test
    fun sanitize_keeps_supported_tag() {
        assertEquals("en", SupportedLocales.sanitize("en"))
    }

    @Test
    fun pseudo_locale_is_selectable_only_when_included() {
        val shipped = SupportedLocales.tags
        assertEquals(shipped, SupportedLocales.selectable(includePseudoLocale = false))
        assertEquals(shipped + PSEUDO_LOCALE, SupportedLocales.selectable(includePseudoLocale = true))
        assertNull(SupportedLocales.sanitize(PSEUDO_LOCALE, includePseudoLocale = false))
        assertEquals(
            PSEUDO_LOCALE,
            SupportedLocales.sanitize(PSEUDO_LOCALE, includePseudoLocale = true),
        )
    }

    @Test
    fun pseudo_locale_constant_matches_debug_locale_config() {
        assertEquals(PSEUDO_LOCALE, SupportedLocales.PSEUDO_LOCALE)
    }

    @Test
    fun regional_variants_belong_to_their_shipped_language() {
        val options = listOf("en", "es")
        assertEquals("es", SupportedLocales.baseOf("es-MX", options))
        assertEquals("es", SupportedLocales.baseOf("es-419", options))
        assertEquals("en", SupportedLocales.baseOf("en-GB", options))
        assertEquals("en", SupportedLocales.baseOf("en-GB-u-mu-celsius", options))
        assertNull(SupportedLocales.baseOf("fr-FR", options))
    }

    @Test
    fun pseudolocale_is_never_a_regional_variant() {
        assertNull(SupportedLocales.baseOf(PSEUDO_LOCALE, listOf("en")))
        assertEquals(
            PSEUDO_LOCALE,
            SupportedLocales.baseOf(PSEUDO_LOCALE, listOf("en", PSEUDO_LOCALE)),
        )
        // en-XA must not be a base for real regions either.
        assertNull(SupportedLocales.baseOf("en-GB", listOf(PSEUDO_LOCALE)))
    }

    @Test
    fun script_must_match() {
        assertEquals("zh-Hans", SupportedLocales.baseOf("zh-Hans-CN", listOf("zh-Hans")))
        assertNull(SupportedLocales.baseOf("zh-Hant-TW", listOf("zh-Hans")))
    }

    @Test
    fun region_specific_option_only_matches_that_region() {
        assertEquals("pt-BR", SupportedLocales.baseOf("pt-BR", listOf("pt-BR")))
        assertNull(SupportedLocales.baseOf("pt-PT", listOf("pt-BR")))
    }

    @Test
    fun sanitize_keeps_regional_variants_of_shipped_languages() {
        assertEquals("es-MX", SupportedLocales.sanitize("es-MX", includePseudoLocale = false))
        assertEquals("en-GB", SupportedLocales.sanitize("en-GB", includePseudoLocale = false))
        assertNull(SupportedLocales.sanitize(PSEUDO_LOCALE, includePseudoLocale = false))
    }

    @Test
    fun sanitize_drops_unsupported_or_missing_tag() {
        assertNull(SupportedLocales.sanitize("xx"))
        assertNull(SupportedLocales.sanitize(""))
        assertNull(SupportedLocales.sanitize(null))
    }

    /** Tags listed in the locale_config.xml of [sourceSet] ("main" or "debug"). */
    private fun localeConfigTags(sourceSet: String): List<String> {
        val file = sequenceOf("src/$sourceSet/res/xml", "app/src/$sourceSet/res/xml")
            .map { File(it, "locale_config.xml") }
            .firstOrNull(File::exists)
            ?: error("$sourceSet locale_config.xml not found from ${File(".").absolutePath}")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
        val nodes = doc.getElementsByTagName("locale")
        return (0 until nodes.length).map {
            (nodes.item(it) as Element).getAttributeNS(ANDROID_NS, "name")
        }
    }

    /** Same conversion as toResourceQualifier in app/build.gradle.kts. */
    private fun toResourceQualifier(tag: String): String =
        if (SIMPLE_TAG.matches(tag)) tag.replace("-", "-r") else "b+" + tag.replace("-", "+")

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val PSEUDO_LOCALE = "en-XA"

        /** English lives in plain values/, with no qualifier. */
        const val SOURCE_LANGUAGE = "en"
        val SIMPLE_TAG = Regex("^[a-z]{2,3}(-[A-Z]{2})?$")
    }
}
