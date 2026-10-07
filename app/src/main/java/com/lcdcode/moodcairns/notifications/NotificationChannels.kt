package com.lcdcode.moodcairns.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.settings.withAppLocale

object NotificationChannels {
    const val PROMPTS = "mood_prompts"

    /**
     * Creates the channels, or refreshes their name and description in the current
     * app language. Re-creating an existing channel only updates those text fields;
     * importance and the user's own channel settings are left untouched.
     */
    fun ensureCreated(context: Context) {
        val mgr = context.getSystemService<NotificationManager>() ?: return
        val localized = context.withAppLocale()
        val channel = NotificationChannel(
            PROMPTS,
            localized.getString(R.string.notif_channel_prompts_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = localized.getString(R.string.notif_channel_prompts_description)
            setShowBadge(true)
        }
        mgr.createNotificationChannel(channel)
    }
}
