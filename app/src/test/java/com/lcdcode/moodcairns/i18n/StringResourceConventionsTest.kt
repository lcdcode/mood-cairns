package com.lcdcode.moodcairns.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards the string-resource rules from values/strings.xml, and checks every
 * translation against the English source so a broken placeholder fails here instead
 * of crashing String.format on a user's device.
 */
class StringResourceConventionsTest {

    private val resDir: File = sequenceOf("src/main/res", "app/src/main/res")
        .map(::File)
        .firstOrNull(File::isDirectory)
        ?: error("src/main/res not found from ${File(".").absolutePath}")

    private val english: Map<String, StringEntry> =
        parseStrings(File(resDir, "values/strings.xml"))

    @Test
    fun englishPlaceholders_arePositional() {
        val offenders = english.filter { (_, entry) ->
            entry.texts.any { text ->
                FORMAT_SPECIFIER.findAll(text).any { !it.value.contains('$') }
            }
        }.keys
        assertTrue("Use positional args like %1\$s in: $offenders", offenders.isEmpty())
    }

    @Test
    fun englishPlurals_useTheSamePlaceholdersInEveryQuantity() {
        val offenders = english.filter { (_, entry) ->
            entry.texts.map(::placeholders).distinct().size > 1
        }.keys
        assertTrue("Plural quantities disagree on placeholders: $offenders", offenders.isEmpty())
    }

    /**
     * A <string> translation must use exactly the English placeholders. A plural
     * quantity may drop the count (e.g. "one" -> "a second") but never add or retype a
     * placeholder, which would crash String.format.
     */
    @Test
    fun translations_keepEnglishPlaceholders() {
        val mismatches = translationFiles().flatMap { file ->
            val locale = file.parentFile.name
            parseStrings(file).mapNotNull { (name, entry) ->
                val source = english[name] ?: return@mapNotNull "$locale/$name is not in values/"
                val expected = placeholders(source.texts.first())
                entry.texts.firstOrNull { text ->
                    val actual = placeholders(text)
                    if (source.isPlural) !expected.containsAll(actual) else actual != expected
                }?.let { "$locale/$name: expected $expected in \"$it\"" }
            }
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    private fun translationFiles(): List<File> =
        resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
            .map { File(it, "strings.xml") }
            .filter(File::exists)

    /** Placeholders like "%1$d", sorted so translators may reorder them. */
    private fun placeholders(text: String): List<String> =
        FORMAT_SPECIFIER.findAll(text).map { it.value }.sorted().toList()

    /** Skips translatable="false" entries, which translations must not contain anyway. */
    private fun parseStrings(file: File): Map<String, StringEntry> {
        val root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(file).documentElement
        val result = linkedMapOf<String, StringEntry>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val el = children.item(i) as? Element ?: continue
            if (el.getAttribute("translatable") == "false") continue
            val name = el.getAttribute("name")
            when (el.tagName) {
                "string" -> result[name] = StringEntry(isPlural = false, listOf(el.textContent))
                "plurals" -> {
                    val items = el.getElementsByTagName("item")
                    val texts = (0 until items.length).map { items.item(it).textContent }
                    result[name] = StringEntry(isPlural = true, texts)
                }
            }
        }
        return result
    }

    /** One text for a <string>, one per quantity for <plurals>. */
    private data class StringEntry(val isPlural: Boolean, val texts: List<String>)

    private companion object {
        // A java.util.Formatter specifier, excluding the literal "%%".
        val FORMAT_SPECIFIER = Regex("""%(?!%)(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[a-zA-Z]""")
    }
}
