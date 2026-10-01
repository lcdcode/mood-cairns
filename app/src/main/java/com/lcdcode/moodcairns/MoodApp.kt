package com.lcdcode.moodcairns

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.lcdcode.moodcairns.notifications.NotificationChannels
import com.lcdcode.moodcairns.settings.AppLocaleManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MoodApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appLocaleManager: AppLocaleManager

    override fun onCreate() {
        super.onCreate()
        // Must run before any activity is created so API 29-32 starts in the chosen language.
        appLocaleManager.applyOnStartup()
        NotificationChannels.ensureCreated(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
