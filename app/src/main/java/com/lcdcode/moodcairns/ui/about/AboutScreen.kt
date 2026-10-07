package com.lcdcode.moodcairns.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lcdcode.moodcairns.BuildConfig
import com.lcdcode.moodcairns.R

private const val RELEASES_URL = "https://github.com/lcdcode/mood-cairns/releases/latest"
private const val ISSUES_URL = "https://github.com/lcdcode/mood-cairns/issues"
private const val AUTHOR = "lcdcode"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var showNoBrowser by remember { mutableStateOf(false) }

    fun openInBrowser(url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            showNoBrowser = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
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
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.about_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                stringResource(R.string.about_why_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.about_why_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.about_made_by, AUTHOR),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.about_updates_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                AnnotatedString.fromHtml(stringResource(R.string.about_updates_body)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { openInBrowser(RELEASES_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_get_latest))
            }
            Text(
                stringResource(R.string.about_get_latest_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showNoBrowser) {
                Text(
                    stringResource(R.string.about_no_browser),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                stringResource(R.string.about_contact_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                AnnotatedString.fromHtml(
                    stringResource(R.string.about_contact_body, ISSUES_URL),
                    linkStyles = TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    linkInteractionListener = { link ->
                        (link as? LinkAnnotation.Url)?.let { openInBrowser(it.url) }
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
