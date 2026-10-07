package com.lcdcode.moodcairns.ui.settings

import androidx.lifecycle.ViewModel
import com.lcdcode.moodcairns.settings.AppLocaleManager
import com.lcdcode.moodcairns.settings.SupportedLocales
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val appLocales: AppLocaleManager,
) : ViewModel() {

    val selectableTags: List<String> = SupportedLocales.selectable()

    /** The chosen language tag, or null when following the system language. */
    fun currentTag(): String? = appLocales.currentTag()

    fun systemLocale(): Locale = appLocales.systemLocale()

    /** Applies [tag] (null = system language); the activity is recreated in it. */
    fun select(tag: String?) {
        if (tag != appLocales.currentTag()) appLocales.setLocale(tag)
    }
}

/**
 * A language's name in that language ("Deutsch", "Français"), so someone stuck in a
 * language they can't read can still find their own.
 */
internal fun endonym(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/** Whether the app ships a translation in [system]'s language (region is ignored). */
internal fun isTranslated(system: Locale, shippedTags: List<String>): Boolean =
    shippedTags.any { Locale.forLanguageTag(it).language == system.language }
