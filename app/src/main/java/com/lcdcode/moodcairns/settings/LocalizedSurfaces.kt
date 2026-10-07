package com.lcdcode.moodcairns.settings

import android.content.Context
import com.lcdcode.moodcairns.notifications.NotificationChannels
import com.lcdcode.moodcairns.widget.LogMoodWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text the system keeps outside the app, rendered in whatever language was current at
 * the time: notification channel names and the home-screen widget. Re-renders it when
 * the effective app language differs from the one last rendered.
 *
 * Any language change (in-app picker, system per-app setting, or device language while
 * following the system) recreates the activity or restarts the process, so calling
 * this from Application.onCreate and Activity.onCreate catches every path. The stored
 * comparison keeps rotations and other recreations free.
 */
@Singleton
class LocalizedSurfaces @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun refreshIfLanguageChanged() {
        val effective = context.withAppLocale().resources.configuration.locales.toLanguageTags()
        if (prefs.getString(KEY_RENDERED_LOCALES, null) == effective) return
        NotificationChannels.ensureCreated(context)
        LogMoodWidget.refresh(context)
        prefs.edit().putString(KEY_RENDERED_LOCALES, effective).apply()
    }

    private companion object {
        const val PREFS_NAME = "localized_surfaces"
        const val KEY_RENDERED_LOCALES = "rendered_locales"
    }
}
