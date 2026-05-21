package com.lcdcode.moodcairns.work

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.data.dao.PromptWindowDao
import com.lcdcode.moodcairns.data.entity.PromptWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class PromptScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val windowDao: PromptWindowDao,
) {
    private val wm by lazy { WorkManager.getInstance(context) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Register AlarmManager alarms for today's remaining prompts + tomorrow's prompts.
     *
     * Runs directly on our own coroutine scope rather than via WorkManager so that saving
     * a window or opening the app immediately registers alarms — WorkManager one-time work
     * can be batched or deferred by Doze.
     */
    fun scheduleNow() {
        scope.launch {
            val windows = windowDao.enabled()
            val today = LocalDate.now()
            if (BuildConfig.DEBUG) Log.i(TAG, "scheduleNow: ${windows.size} enabled windows")
            for (day in listOf(today, today.plusDays(1))) {
                for (w in windows) {
                    val fireAt = pickFireTime(day, w) ?: continue
                    setAlarm(day, w, fireAt)
                }
            }
        }
    }

    /** Suspending variant — used by [DailyScheduleWorker] so it can await completion. */
    suspend fun scheduleNowBlocking() {
        val windows = windowDao.enabled()
        val today = LocalDate.now()
        if (BuildConfig.DEBUG) Log.i(TAG, "scheduleNowBlocking: ${windows.size} enabled windows")
        for (day in listOf(today, today.plusDays(1))) {
            for (w in windows) {
                val fireAt = pickFireTime(day, w) ?: continue
                setAlarm(day, w, fireAt)
            }
        }
    }

    /** Daily rollover so prompts keep scheduling without needing the app open. */
    fun ensureDailyRolloverScheduled() {
        val request = PeriodicWorkRequestBuilder<DailyScheduleWorker>(1, TimeUnit.DAYS).build()
        wm.enqueueUniquePeriodicWork(
            UNIQUE_DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Fire a test notification in [seconds] seconds. Used to verify the alarm + notification path. */
    fun scheduleTestIn(seconds: Long) {
        val alarmMgr = context.getSystemService<AlarmManager>() ?: return
        val fireMs = System.currentTimeMillis() + seconds * 1000
        val testWindow = PromptWindow(
            id = -999L,
            label = "Test notification",
            slot = com.lcdcode.moodcairns.data.entity.PromptSlot.MANUAL,
            startTime = java.time.LocalTime.NOON,
            endTime = java.time.LocalTime.NOON.plusMinutes(1),
            enabled = true,
        )
        val pi = DailyScheduleWorker.pendingIntent(context, LocalDate.now(), testWindow)
        if (canExact(alarmMgr)) {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMs, pi)
        } else {
            alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMs, pi)
        }
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "scheduleTestIn: set alarm for +${seconds}s (exact=${canExact(alarmMgr)})")
        }
    }

    fun cancelAll() {
        wm.cancelUniqueWork(UNIQUE_DAILY)
        scope.launch {
            val alarmMgr = context.getSystemService<AlarmManager>() ?: return@launch
            val windows = windowDao.observeAll().first()
            val today = LocalDate.now()
            for (day in listOf(today, today.plusDays(1))) {
                for (w in windows) {
                    alarmMgr.cancel(DailyScheduleWorker.pendingIntent(context, day, w))
                }
            }
        }
    }

    private fun setAlarm(day: LocalDate, window: PromptWindow, fireAt: LocalDateTime) {
        val alarmMgr = context.getSystemService<AlarmManager>() ?: return
        val triggerMs = fireAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = DailyScheduleWorker.pendingIntent(context, day, window)
        if (canExact(alarmMgr)) {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        } else {
            alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
        if (BuildConfig.DEBUG) {
            // Window label is user-supplied free text; keep it out of release logcat.
            Log.i(TAG, "alarm set: ${window.label} day=$day at=$fireAt exact=${canExact(alarmMgr)}")
        }
    }

    private fun canExact(alarmMgr: AlarmManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmMgr.canScheduleExactAlarms()
        } else true
    }

    companion object {
        private const val TAG = "PromptScheduler"
        private const val UNIQUE_DAILY = "prompt-schedule-daily"

        /**
         * Pick when to fire a prompt for [window] on [day].
         *
         * Deterministic per (windowId, day): repeated calls within the same day return
         * the same instant, so calling [scheduleNow] over and over (on unlock, boot, edit)
         * always replaces the same alarm rather than rolling a fresh time and producing
         * a second notification later in the window.
         *
         * Returns null when there is nothing to schedule:
         *  - the window's end is malformed or already past,
         *  - or the deterministic fire instant has already elapsed (the alarm either
         *    already fired or was missed; we don't catch up later in the same window).
         */
        fun pickFireTime(day: LocalDate, window: PromptWindow): LocalDateTime? {
            val start = window.startTime
            val end = window.endTime
            if (!end.isAfter(start)) return null
            val windowStart = LocalDateTime.of(day, start)
            val windowEnd = LocalDateTime.of(day, end)
            val now = LocalDateTime.now(ZoneId.systemDefault())
            if (!windowEnd.isAfter(now)) return null

            val spanSec = Duration.between(windowStart, windowEnd).seconds
            if (spanSec <= 0) return null
            val seed = window.id * 1_000_003L xor day.toEpochDay()
            val offset = Random(seed).nextLong(spanSec)
            val fireAt = windowStart.plusSeconds(offset).withNano(0)
            if (!fireAt.isAfter(now)) return null
            return fireAt
        }
    }
}
