package com.moodcairns.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

object NotificationChannels {
    const val PROMPTS = "mood_prompts"

    fun ensureCreated(context: Context) {
        val mgr = context.getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(PROMPTS) != null) return
        val channel = NotificationChannel(
            PROMPTS,
            "Mood prompts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Gentle reminders to log how you're feeling."
            setShowBadge(true)
        }
        mgr.createNotificationChannel(channel)
    }
}
