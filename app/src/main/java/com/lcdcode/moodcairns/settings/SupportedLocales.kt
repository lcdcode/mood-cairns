package com.lcdcode.moodcairns.settings

/**
 * BCP-47 tags of the languages the app ships, in picker order. Must match
 * res/xml/locale_config.xml (enforced by SupportedLocalesTest).
 */
object SupportedLocales {

    val tags: List<String> = listOf("en")

    /**
     * Returns [tag] if the app ships it, else null (follow the system language). Guards
     * against stale stored values, e.g. a language removed in a later release.
     */
    fun sanitize(tag: String?): String? = tag?.takeIf { it in tags }
}
