package com.lcdcode.moodcairns.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate

/**
 * A context whose resources use the app language chosen in [AppLocaleManager].
 * Use it to load strings outside an activity: broadcast receivers, the widget,
 * notification channels, workers.
 *
 * API 33+: the platform already applies the per-app language to every context, so
 * this returns [this]. API 29-32: AppCompatDelegate only localizes activities, so
 * a configuration context is built from the locales set in applyOnStartup.
 */
fun Context.withAppLocale(): Context {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return this
    val appLocales = AppCompatDelegate.getApplicationLocales()
    if (appLocales.isEmpty) return this
    val config = Configuration(resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(appLocales.toLanguageTags()))
    }
    return createConfigurationContext(config)
}
