package com.lcdcode.moodcairns.settings

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
    fun tags_matchLocaleConfigXml() {
        assertEquals(
            "SupportedLocales.tags and res/xml/locale_config.xml must list the same languages",
            localeConfigTags("main").toSet(),
            SupportedLocales.tags.toSet(),
        )
    }

    @Test
    fun debugLocaleConfig_isMainPlusPseudolocale() {
        assertEquals(
            "debug locale_config.xml must list the main languages plus $PSEUDO_LOCALE",
            localeConfigTags("main").toSet() + PSEUDO_LOCALE,
            localeConfigTags("debug").toSet(),
        )
    }

    @Test
    fun tags_areUnique() {
        assertEquals(SupportedLocales.tags.distinct(), SupportedLocales.tags)
    }

    @Test
    fun tags_areWellFormedBcp47() {
        SupportedLocales.tags.forEach { tag ->
            assertEquals("Not a canonical BCP-47 tag: $tag", tag, Locale.forLanguageTag(tag).toLanguageTag())
        }
    }

    @Test
    fun englishFallback_isAlwaysShipped() {
        assertTrue(SupportedLocales.tags.contains("en"))
    }

    @Test
    fun sanitize_keepsSupportedTag() {
        assertEquals("en", SupportedLocales.sanitize("en"))
    }

    @Test
    fun sanitize_dropsUnsupportedOrMissingTag() {
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

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val PSEUDO_LOCALE = "en-XA"
    }
}
