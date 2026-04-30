package com.lcdcode.moodcairns.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lcdcode.moodcairns.MainActivity

class PromptAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(EXTRA_SLOT) ?: return
        val windowId = intent.getLongExtra(EXTRA_WINDOW_ID, -1L)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Time to check in"

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_SLOT, slot)
            putExtra(EXTRA_WINDOW_ID, windowId)
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            windowId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.PROMPTS)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(label)
            .setContentText("Log how you're feeling")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val id = (windowId.takeIf { it >= 0 } ?: System.currentTimeMillis()).toInt()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked; nothing to do.
        }
    }

    companion object {
        const val ACTION_FIRE = "com.lcdcode.moodcairns.action.FIRE_PROMPT"

        const val EXTRA_SLOT = "com.lcdcode.moodcairns.extra.SLOT"
        const val EXTRA_WINDOW_ID = "com.lcdcode.moodcairns.extra.WINDOW_ID"
        const val EXTRA_LABEL = "com.lcdcode.moodcairns.extra.LABEL"
        const val EXTRA_FROM_NOTIFICATION = "com.lcdcode.moodcairns.extra.FROM_NOTIFICATION"
    }
}
