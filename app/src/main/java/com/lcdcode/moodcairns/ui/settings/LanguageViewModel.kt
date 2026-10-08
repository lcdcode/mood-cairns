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

    /** The applied language tag, possibly regional ("en-GB"), or null for the system's. */
    fun currentTag(): String? = appLocales.currentTag()

    fun systemLocale(): Locale = appLocales.systemLocale()

    /** Applies picker option [choice] (null = system language); the activity is recreated. */
    fun select(choice: String?) {
        val tag = choice?.let { tagToApply(it, appLocales.systemLocale()) }
        if (tag != appLocales.currentTag()) appLocales.setLocale(tag)
    }
}

/**
 * The tag to apply for picker option [choice]. When the device's own locale is a
 * variant of that language, apply the device locale instead, so its regional formats
 * survive: picking "English" on an en-GB phone keeps "5 Mar" dates, and picking
 * "Español" on an es-MX phone keeps "2.5" decimals.
 */
internal fun tagToApply(choice: String, system: Locale): String {
    val systemTag = system.toLanguageTag()
    val systemMatchesChoice = choice != SupportedLocales.PSEUDO_LOCALE &&
        SupportedLocales.selectableBase(systemTag) == choice
    return if (systemMatchesChoice) systemTag else choice
}

/** The picker option an applied tag belongs to ("en-GB" -> "en"), or null if none. */
internal fun pickerOptionFor(appliedTag: String?): String? =
    appliedTag?.let { SupportedLocales.selectableBase(it) }

/**
 * A language's name in that language ("Deutsch", "Français"), so someone stuck in a
 * language they can't read can still find their own. Unicode extensions (regional
 * preferences such as a temperature unit) are left out of the name.
 */
internal fun endonym(tag: String): String {
    val locale = Locale.forLanguageTag(tag).stripExtensions()
    return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
}

/** Whether the app ships a translation in [system]'s language (region is ignored). */
internal fun isTranslated(system: Locale, shippedTags: List<String>): Boolean =
    shippedTags.any { Locale.forLanguageTag(it).language == system.language }
