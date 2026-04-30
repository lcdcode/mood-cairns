package com.lcdcode.moodcairns.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lcdcode.moodcairns.work.PromptScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: PromptScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.MY_PACKAGE_REPLACED"
        ) return
        scheduler.ensureDailyRolloverScheduled()
        scheduler.scheduleNow()
    }
}
