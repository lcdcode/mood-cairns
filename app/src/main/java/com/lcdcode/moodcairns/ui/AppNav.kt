package com.lcdcode.moodcairns.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lcdcode.moodcairns.data.entity.PromptSlot
import com.lcdcode.moodcairns.ui.backup.BackupScreen
import com.lcdcode.moodcairns.ui.charts.ChartsScreen
import com.lcdcode.moodcairns.ui.entry.EntryScreen
import com.lcdcode.moodcairns.ui.history.HistoryScreen
import com.lcdcode.moodcairns.ui.home.HomeScreen
import com.lcdcode.moodcairns.ui.scales.ScaleEditScreen
import com.lcdcode.moodcairns.ui.scales.ScaleListScreen
import com.lcdcode.moodcairns.ui.settings.ChangePinScreen
import com.lcdcode.moodcairns.ui.settings.PromptWindowEditScreen
import com.lcdcode.moodcairns.ui.settings.SettingsScreen

data class NotificationEntryArgs(val slot: String, val windowId: Long?)

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val ENTRY = "entry"
    const val BACKUP = "backup"
    const val SCALES = "scales"
    const val SCALE_EDIT = "scale_edit"
    const val CHARTS = "charts"
    const val SETTINGS = "settings"
    const val WINDOW_EDIT = "window_edit"
    const val CHANGE_PIN = "change_pin"
    fun scaleEdit(id: Long? = null) = "$SCALE_EDIT?scaleId=${id ?: 0L}"
    fun windowEdit(id: Long? = null) = "$WINDOW_EDIT?windowId=${id ?: 0L}"

    fun entry(slot: PromptSlot = PromptSlot.MANUAL, windowId: Long? = null, recordedAt: Long? = null) =
        "$ENTRY?slot=${slot.name}&windowId=${windowId ?: -1L}&recordedAt=${recordedAt ?: -1L}"

    fun entryFromArgs(args: NotificationEntryArgs) =
        "$ENTRY?slot=${args.slot}&windowId=${args.windowId ?: -1L}&recordedAt=-1"
}

@Composable
fun AppNav(
    modifier: Modifier = Modifier,
    initialEntryArgs: NotificationEntryArgs? = null,
    onEntryArgsConsumed: () -> Unit = {},
) {
    val nav = rememberNavController()

    LaunchedEffect(initialEntryArgs) {
        if (initialEntryArgs != null) {
            nav.navigate(Routes.entryFromArgs(initialEntryArgs))
            onEntryArgsConsumed()
        }
    }

    NavHost(navController = nav, startDestination = Routes.HOME, modifier = modifier) {

        composable(Routes.HOME) {
            HomeScreen(
                onLogNow = { nav.navigate(Routes.entry()) },
                onHistory = { nav.navigate(Routes.HISTORY) },
                onBackup = { nav.navigate(Routes.BACKUP) },
                onScales = { nav.navigate(Routes.SCALES) },
                onCharts = { nav.navigate(Routes.CHARTS) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CHARTS) {
            ChartsScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onAddWindow = { nav.navigate(Routes.windowEdit()) },
                onEditWindow = { id -> nav.navigate(Routes.windowEdit(id)) },
                onChangePin = { nav.navigate(Routes.CHANGE_PIN) },
            )
        }

        composable(
            route = "${Routes.WINDOW_EDIT}?windowId={windowId}",
            arguments = listOf(
                navArgument("windowId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) {
            PromptWindowEditScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.CHANGE_PIN) {
            ChangePinScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.BACKUP) {
            BackupScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.SCALES) {
            ScaleListScreen(
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate(Routes.scaleEdit()) },
                onEdit = { id -> nav.navigate(Routes.scaleEdit(id)) },
            )
        }

        composable(
            route = "${Routes.SCALE_EDIT}?scaleId={scaleId}",
            arguments = listOf(
                navArgument("scaleId") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) {
            ScaleEditScreen(onBack = { nav.popBackStack() })
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { nav.popBackStack() },
                onAddPast = { nav.navigate(Routes.entry()) },
            )
        }

        composable(
            route = "${Routes.ENTRY}?slot={slot}&windowId={windowId}&recordedAt={recordedAt}",
            arguments = listOf(
                navArgument("slot") { type = NavType.StringType; defaultValue = PromptSlot.MANUAL.name },
                navArgument("windowId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("recordedAt") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            EntryScreen(
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
    }
}
