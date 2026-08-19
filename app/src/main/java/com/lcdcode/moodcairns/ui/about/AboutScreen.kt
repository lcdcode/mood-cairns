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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.lcdcode.moodcairns.BuildConfig

private const val RELEASES_URL = "https://github.com/lcdcode/mood-cairns/releases/latest"
private const val ISSUES_URL = "https://github.com/lcdcode/mood-cairns/issues"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var noBrowserMessage by remember { mutableStateOf<String?>(null) }

    fun openInBrowser(url: String) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            noBrowserMessage = "No browser found to open the page."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            Text("Mood Cairns", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Why this app exists", style = MaterialTheme.typography.titleSmall)
            Text(
                "I built Mood Cairns because I wanted a mood tracker with all the features " +
                    "I liked and none of the things I didn't: no creepy tracking, no " +
                    "analytics, no account, and no Google Play Store required for " +
                    "purchases. Your entries live only on your device - the app declares " +
                    "no network permissions, and it always will be that way.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Made by lcdcode. Free and open source. Thank you!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Updates", style = MaterialTheme.typography.titleSmall)
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("F-Droid users should get updates automatically")
                    }
                    append(
                        ", but if not, or if you downloaded Mood Cairns from GitHub, " +
                            "use the below button to check for updates and ",
                    )
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append("download the latest .apk file")
                    }
                    append(" under Assets.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { openInBrowser(RELEASES_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Get latest version")
            }
            Text(
                "Opens the latest release on GitHub in your browser. The app itself " +
                    "never touches the network.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            noBrowserMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text("Contact", style = MaterialTheme.typography.titleSmall)
            Text(
                buildAnnotatedString {
                    append("The best way to contact me for changes or feature requests is to ")
                    withLink(
                        LinkAnnotation.Url(
                            url = ISSUES_URL,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                            linkInteractionListener = { openInBrowser(ISSUES_URL) },
                        ),
                    ) {
                        append("open a new issue on GitHub")
                    }
                    append(".")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
