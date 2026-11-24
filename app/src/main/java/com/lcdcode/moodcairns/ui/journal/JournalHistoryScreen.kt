package com.lcdcode.moodcairns.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.ui.common.rememberSkeletonDateFormat
import java.time.LocalDate

private val ScreenPadding = 16.dp
private val RowSpacing = 8.dp
private val CardPadding = 16.dp
private const val PREVIEW_MAX_LINES = 3
private const val ROW_DATE_SKELETON = "yMMMEd"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalHistoryScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: JournalHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.journal_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            SearchField(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(ScreenPadding),
            )
            when {
                !state.loaded -> Unit
                state.rows.isEmpty() -> EmptyMessage(
                    stringResource(
                        if (state.searching) {
                            R.string.journal_history_no_matches
                        } else {
                            R.string.journal_history_empty
                        },
                    ),
                )
                else -> PageList(state.rows, onOpenDay)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.journal_history_search_hint)) },
        singleLine = true,
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(R.string.history_search),
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = stringResource(R.string.history_clear_search),
                    )
                }
            }
        },
    )
}

@Composable
private fun PageList(rows: List<JournalHistoryRow>, onOpenDay: (LocalDate) -> Unit) {
    val dateFormat = rememberSkeletonDateFormat(ROW_DATE_SKELETON)
    LazyColumn(
        contentPadding = PaddingValues(
            start = ScreenPadding,
            end = ScreenPadding,
            bottom = ScreenPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(RowSpacing),
    ) {
        items(rows, key = { it.day.toString() }) { row ->
            Card(onClick = { onOpenDay(row.day) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(CardPadding)) {
                    Text(dateFormat.format(row.day), style = MaterialTheme.typography.titleSmall)
                    if (row.preview.text.isNotEmpty()) {
                        Text(
                            text = highlighted(row.preview),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = PREVIEW_MAX_LINES,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun highlighted(preview: HighlightedText): AnnotatedString {
    val matchColor = MaterialTheme.colorScheme.primary
    return remember(preview, matchColor) { preview.toAnnotatedString(matchColor) }
}

private fun HighlightedText.toAnnotatedString(matchColor: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        val style = SpanStyle(color = matchColor, fontWeight = FontWeight.Bold)
        highlights.forEach { addStyle(style, it.first, it.last + 1) }
    }

@Composable
private fun EmptyMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(ScreenPadding), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
