package com.lcdcode.moodcairns.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Mood Cairns") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Log how you're feeling, privately.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onLogNow, modifier = Modifier.fillMaxWidth()) {
                Text("Log entry")
            }
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) {
                Text("View history")
            }
            OutlinedButton(onClick = onCharts, modifier = Modifier.fillMaxWidth()) {
                Text("Charts")
            }
            OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Backup & import")
            }
            OutlinedButton(onClick = onScales, modifier = Modifier.fillMaxWidth()) {
                Text("Manage scales")
            }
            OutlinedButton(onClick = onTags, modifier = Modifier.fillMaxWidth()) {
                Text("Manage tags")
            }
            OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Settings")
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onAbout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("About")
            }
        }
    }
}
