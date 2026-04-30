package com.lcdcode.moodcairns.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lcdcode.moodcairns.data.entity.PromptWindow
import com.lcdcode.moodcairns.notifications.PromptAlarmReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Daily rollover worker. Delegates to [PromptScheduler] so the alarm-picking logic
 * lives in exactly one place.
 *
 * AlarmManager (not WorkManager.setInitialDelay) is used for the actual prompt fire
 * because Doze and App Standby can defer delayed work by hours; this worker only
 * runs once a day to top up alarms for today + tomorrow.
 */
@HiltWorker
class DailyScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val promptScheduler: PromptScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        promptScheduler.scheduleNowBlocking()
        return Result.success()
    }

    companion object {
        const val TAG_PROMPT = "mood_prompt"

        fun pendingIntent(
            context: Context,
            day: LocalDate,
            window: PromptWindow,
        ): PendingIntent {
            val intent = Intent(context, PromptAlarmReceiver::class.java).apply {
                action = PromptAlarmReceiver.ACTION_FIRE
                putExtra(PromptAlarmReceiver.EXTRA_SLOT, window.slot.name)
                putExtra(PromptAlarmReceiver.EXTRA_WINDOW_ID, window.id)
                putExtra(PromptAlarmReceiver.EXTRA_LABEL, window.label)
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode(day, window.id),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun requestCode(day: LocalDate, windowId: Long): Int {
            // dayOfYear in [1,366], windowId assumed < ~20k. Fits comfortably in Int.
            return day.dayOfYear * 100_000 + windowId.toInt()
        }
    }
}
