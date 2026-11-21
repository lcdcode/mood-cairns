package com.lcdcode.moodcairns.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R

private val ScreenPadding = 16.dp
private val ButtonSpacing = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogNow: () -> Unit,
    onHistory: () -> Unit,
    onBackup: () -> Unit,
    onScales: () -> Unit,
    onTags: () -> Unit,
    onCharts: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onOpenJournal: () -> Unit,
    onJournalHistory: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val journalEnabled by viewModel.journalEnabled.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { padding ->
        // The buttons scroll on short screens while About stays pinned to the bottom. A
        // weighted spacer would collapse inside a scrolling column, hence the nesting.
        Column(Modifier.padding(padding).fillMaxSize().padding(ScreenPadding)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ButtonSpacing),
            ) {
                Text(
                    stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                )
                ButtonPair {
                    Button(onClick = onLogNow, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PairedLabel(stringResource(R.string.home_log_mood))
                    }
                    OutlinedButton(onClick = onHistory, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        PairedLabel(stringResource(R.string.home_mood_history))
                    }
                }
                if (journalEnabled) {
                    ButtonPair {
                        FilledTonalButton(onClick = onOpenJournal, modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PairedLabel(stringResource(R.string.home_open_journal))
                        }
                        OutlinedButton(onClick = onJournalHistory, modifier = Modifier.weight(1f).fillMaxHeight()) {
                            PairedLabel(stringResource(R.string.journal_history_title))
                        }
                    }
                }
                OutlinedButton(onClick = onCharts, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_charts))
                }
                OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_backup))
                }
                OutlinedButton(onClick = onScales, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_scales))
                }
                OutlinedButton(onClick = onTags, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_tags))
                }
                OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.home_settings))
                }
            }
            TextButton(onClick = onAbout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.home_about))
            }
        }
    }
}

/** Two equal-width buttons; if one label wraps, both grow to the same height. */
@Composable
private fun ButtonPair(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ButtonSpacing),
        content = content,
    )
}

@Composable
private fun PairedLabel(text: String) {
    Text(text, textAlign = TextAlign.Center)
}
