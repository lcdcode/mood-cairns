package com.lcdcode.moodcairns.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.lcdcode.moodcairns.MainActivity
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.notifications.PromptAlarmReceiver

class LogMoodWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_log_mood)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PromptAlarmReceiver.EXTRA_FROM_NOTIFICATION, true)
            putExtra(PromptAlarmReceiver.EXTRA_SLOT, PromptSlot.MANUAL.name)
            putExtra(PromptAlarmReceiver.EXTRA_WINDOW_ID, -1L)
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_LOG,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        return views
    }

    companion object {
        private const val REQUEST_LOG = 0xC417 // stable request code

        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, LogMoodWidget::class.java))
            if (ids.isNotEmpty()) {
                val provider = LogMoodWidget()
                provider.onUpdate(context, mgr, ids)
            }
        }
    }
}
