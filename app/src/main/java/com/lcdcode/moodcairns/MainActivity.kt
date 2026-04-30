package com.lcdcode.moodcairns

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.security.LockManager
import com.lcdcode.moodcairns.security.LockRepository
import com.lcdcode.moodcairns.security.LockState
import com.lcdcode.moodcairns.ui.AppNav
import com.lcdcode.moodcairns.ui.NotificationEntryArgs
import com.lcdcode.moodcairns.ui.lock.LockScreen
import com.lcdcode.moodcairns.ui.lock.SetPinScreen
import com.lcdcode.moodcairns.ui.theme.MoodCairnsTheme
import com.lcdcode.moodcairns.notifications.PromptAlarmReceiver
import com.lcdcode.moodcairns.work.PromptScheduler
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var lockManager: LockManager
    @Inject lateinit var lockRepository: LockRepository
    @Inject lateinit var scheduler: PromptScheduler

    private val pendingEntryArgs = MutableStateFlow<NotificationEntryArgs?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingEntryArgs.value = readNotificationArgs(intent)
        scheduler.ensureDailyRolloverScheduled()

        setContent {
            MoodCairnsTheme {
                Surface(Modifier.fillMaxSize()) {
                    val state by lockManager.state.collectAsStateWithLifecycle()

                    val pendingArgs by pendingEntryArgs.collectAsStateWithLifecycle()
                    val ctx = LocalContext.current
                    val notifPermLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { /* granted or not, proceed either way */ }

                    LaunchedEffect(state) {
                        if (state is LockState.Unlocked) {
                            scheduler.scheduleNow()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    ctx,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    when (state) {
                        LockState.NeedsSetup -> SetPinScreen()
                        LockState.Locked -> LockScreen(biometricEnabled = lockRepository.biometricEnabled)
                        LockState.Unlocked -> AppNav(
                            initialEntryArgs = pendingArgs,
                            onEntryArgsConsumed = { pendingEntryArgs.value = null },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNotificationArgs(intent)?.let { pendingEntryArgs.value = it }
    }

    override fun onPause() {
        super.onPause()
        lockManager.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        lockManager.onAppForegrounded()
    }

    private fun readNotificationArgs(intent: Intent?): NotificationEntryArgs? {
        if (intent == null) return null
        if (!intent.getBooleanExtra(PromptAlarmReceiver.EXTRA_FROM_NOTIFICATION, false)) return null
        val slot = intent.getStringExtra(PromptAlarmReceiver.EXTRA_SLOT) ?: return null
        val windowId = intent.getLongExtra(PromptAlarmReceiver.EXTRA_WINDOW_ID, -1L)
        intent.removeExtra(PromptAlarmReceiver.EXTRA_FROM_NOTIFICATION)
        return NotificationEntryArgs(slot = slot, windowId = windowId.takeIf { it >= 0 })
    }
}
