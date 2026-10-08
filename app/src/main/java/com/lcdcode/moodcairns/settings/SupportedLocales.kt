package com.lcdcode.moodcairns.settings

import com.lcdcode.moodcairns.BuildConfig
import java.util.Locale

/**
 * BCP-47 tags of the languages the app ships, in picker order. Must match
 * res/xml/locale_config.xml (enforced by SupportedLocalesTest).
 */
object SupportedLocales {

    val tags: List<String> = listOf("en", "es", "de")

    /** Accented, lengthened English for spotting untranslated text. Debug builds only. */
    const val PSEUDO_LOCALE = "en-XA"

    /** Region codes Android reserves for pseudolocales; never a real region. */
    private val PSEUDO_REGIONS = setOf("XA", "XB")

    /** Languages the user may pick: [tags], plus [PSEUDO_LOCALE] in debug builds. */
    fun selectable(includePseudoLocale: Boolean = BuildConfig.DEBUG): List<String> =
        if (includePseudoLocale) tags + PSEUDO_LOCALE else tags

    /**
     * The selectable tag that [tag] is, or is a regional variant of: "es-MX" and
     * "es-419" belong to "es", "en-GB-u-mu-celsius" to "en". Null if none.
     */
    fun selectableBase(tag: String, includePseudoLocale: Boolean = BuildConfig.DEBUG): String? =
        baseOf(tag, selectable(includePseudoLocale))

    /**
     * Returns [tag] if it can be applied, else null (follow the system language).
     * Regional variants of a shipped language are accepted, so a choice can keep the
     * user's region. Guards against stale stored values, e.g. a removed language.
     */
    fun sanitize(tag: String?, includePseudoLocale: Boolean = BuildConfig.DEBUG): String? =
        tag?.takeIf { selectableBase(it, includePseudoLocale) != null }

    /**
     * Matching rules: language and script must agree, so "zh-Hant" is not taken for a
     * shipped "zh-Hans". An option with a region ("pt-BR") only matches that region. A
     * pseudolocale is neither a variant of a real language nor a base for one.
     */
    internal fun baseOf(tag: String, options: List<String>): String? {
        if (tag in options) return tag
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isEmpty() || locale.country in PSEUDO_REGIONS) return null
        return options.firstOrNull { option ->
            val base = Locale.forLanguageTag(option)
            base.country !in PSEUDO_REGIONS &&
                base.language == locale.language &&
                base.script == locale.script &&
                (base.country.isEmpty() || base.country == locale.country)
        }
    }
}
