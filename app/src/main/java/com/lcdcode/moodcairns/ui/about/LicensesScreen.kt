package com.lcdcode.moodcairns.ui.about

import android.content.res.AssetManager
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.markdown.MarkdownParser
import com.lcdcode.moodcairns.markdown.MdBlock
import com.lcdcode.moodcairns.ui.journal.MarkdownView
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LicensesScreen"

private sealed interface NoticesState {
    data object Loading : NoticesState
    data class Loaded(val blocks: List<MdBlock>) : NoticesState
    data object Failed : NoticesState
}

/**
 * Shows THIRD_PARTY_NOTICES.md, which the build copies into the APK assets. License texts
 * stay in their original English; only the surrounding UI is translated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val assets = LocalContext.current.assets
    var notices by remember(assets) { mutableStateOf<NoticesState>(NoticesState.Loading) }
    LaunchedEffect(assets) {
        notices = withContext(Dispatchers.IO) { loadNotices(assets) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_title)) },
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
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.licenses_original_language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val state = notices) {
                NoticesState.Loading -> CircularProgressIndicator()
                is NoticesState.Loaded -> SelectionContainer { MarkdownView(state.blocks) }
                NoticesState.Failed -> Text(
                    stringResource(R.string.licenses_load_failed),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun loadNotices(assets: AssetManager): NoticesState = try {
    val text = assets.open(BuildConfig.THIRD_PARTY_NOTICES_ASSET)
        .bufferedReader()
        .use { it.readText() }
    NoticesState.Loaded(MarkdownParser.parse(text))
} catch (e: IOException) {
    Log.e(TAG, "Could not read asset ${BuildConfig.THIRD_PARTY_NOTICES_ASSET}", e)
    NoticesState.Failed
}
