package com.lcdcode.moodcairns.settings

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-app language selection. A null tag means "follow the system language".
 *
 * API 33+: the platform LocaleManager stores and applies the choice, and it stays in
 * sync with the system per-app language settings page.
 *
 * API 29-32: AppCompatDelegate applies the choice to activities, but nothing persists
 * it, so the tag is kept in [backportPrefs] and re-applied in [applyOnStartup].
 */
@Singleton
class AppLocaleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Plain prefs on purpose: the tag must be readable before the PIN unlock and from
    // broadcast receivers, and a language tag is not sensitive.
    private val backportPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Call from Application.onCreate, before any activity is created. */
    fun applyOnStartup() {
        if (isPlatformManaged()) return
        val tag = SupportedLocales.sanitize(backportPrefs.getString(KEY_TAG, null)) ?: return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** The selected language tag, or null when following the system language. */
    fun currentTag(): String? {
        val tags = if (isPlatformManaged()) {
            platformLocaleManager().applicationLocales.toLanguageTags()
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
        return tags.substringBefore(',').ifEmpty { null }
    }

    /** Selects [tag] (null = system language). Running activities are recreated. */
    fun setLocale(tag: String?) {
        require(tag == null || tag in SupportedLocales.tags) {
            "Unsupported app locale '$tag'; expected one of ${SupportedLocales.tags} or null"
        }
        // An empty tag string yields an empty list, which means "follow the system".
        val tags = tag.orEmpty()
        if (isPlatformManaged()) {
            platformLocaleManager().applicationLocales = LocaleList.forLanguageTags(tags)
            return
        }
        backportPrefs.edit().apply {
            if (tag == null) remove(KEY_TAG) else putString(KEY_TAG, tag)
        }.apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private fun isPlatformManaged(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun platformLocaleManager(): LocaleManager =
        context.getSystemService(LocaleManager::class.java)

    private companion object {
        const val PREFS_NAME = "locale_prefs"
        const val KEY_TAG = "app_locale"
    }
}
