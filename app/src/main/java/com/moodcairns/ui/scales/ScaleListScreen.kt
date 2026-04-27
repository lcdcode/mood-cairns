package com.moodcairns.ui.scales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodcairns.data.entity.Scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleListScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ScaleListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scales") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add scale")
            }
        },
    ) { padding ->
        if (state.active.isEmpty() && state.archived.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No scales yet.")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.active.isNotEmpty()) {
                item(key = "hdr-active") {
                    SectionHeader("Active")
                }
                items(state.active, key = { "a-${it.id}" }) { scale ->
                    ScaleRow(
                        scale = scale,
                        onClick = { onEdit(scale.id) },
                        onToggleArchive = { viewModel.setArchived(scale.id, true) },
                    )
                }
            }
            if (state.archived.isNotEmpty()) {
                item(key = "hdr-archived") {
                    SectionHeader("Archived")
                }
                items(state.archived, key = { "z-${it.id}" }) { scale ->
                    ScaleRow(
                        scale = scale,
                        onClick = { onEdit(scale.id) },
                        onToggleArchive = { viewModel.setArchived(scale.id, false) },
                        archived = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ScaleRow(
    scale: Scale,
    onClick: () -> Unit,
    onToggleArchive: () -> Unit,
    archived: Boolean = false,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color(scale.colorArgb),
                modifier = Modifier.size(16.dp),
            ) {}
            Column(modifier = Modifier.weight(1f)) {
                Text(scale.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${scale.minValue}–${scale.maxValue} · step ${scale.step}" +
                        if (scale.isBuiltIn) " · built-in" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onToggleArchive) {
                Text(if (archived) "Unarchive" else "Archive")
            }
        }
    }
}

