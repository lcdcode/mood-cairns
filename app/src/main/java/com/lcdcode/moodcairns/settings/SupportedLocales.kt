package com.lcdcode.moodcairns.settings

import com.lcdcode.moodcairns.BuildConfig

/**
 * BCP-47 tags of the languages the app ships, in picker order. Must match
 * res/xml/locale_config.xml (enforced by SupportedLocalesTest).
 */
object SupportedLocales {

    val tags: List<String> = listOf("en")

    /** Accented, lengthened English for spotting untranslated text. Debug builds only. */
    const val PSEUDO_LOCALE = "en-XA"

    /** Languages the user may pick: [tags], plus [PSEUDO_LOCALE] in debug builds. */
    fun selectable(includePseudoLocale: Boolean = BuildConfig.DEBUG): List<String> =
        if (includePseudoLocale) tags + PSEUDO_LOCALE else tags

    /**
     * Returns [tag] if it can be selected, else null (follow the system language).
     * Guards against stale stored values, e.g. a language removed in a later release.
     */
    fun sanitize(tag: String?, includePseudoLocale: Boolean = BuildConfig.DEBUG): String? =
        tag?.takeIf { it in selectable(includePseudoLocale) }
}
