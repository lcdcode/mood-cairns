package com.lcdcode.moodcairns.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback backgrounds. Also the references for warning about scale colors that would
// vanish; dynamic-color surfaces sit at nearly the same tones.
internal const val LIGHT_BACKGROUND_ARGB = 0xFFFBF7F1.toInt()
internal const val DARK_BACKGROUND_ARGB = 0xFF16140F.toInt()

// Warm stone/earth palette — evokes the "cairn" (stacked-stone) metaphor without
// being too saturated. Used only when dynamic color is unavailable (pre-S).
private val LightColors = lightColorScheme(
    primary = Color(0xFF6E5A41),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6E1C4),
    onPrimaryContainer = Color(0xFF251A0B),
    secondary = Color(0xFF6B5F52),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E4D2),
    onSecondaryContainer = Color(0xFF231B13),
    tertiary = Color(0xFF506A4E),
    onTertiary = Color.White,
    background = Color(LIGHT_BACKGROUND_ARGB),
    onBackground = Color(0xFF1D1B17),
    surface = Color(LIGHT_BACKGROUND_ARGB),
    onSurface = Color(0xFF1D1B17),
    surfaceVariant = Color(0xFFECE1D0),
    onSurfaceVariant = Color(0xFF4D463A),
    outline = Color(0xFF7F7769),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDCC2A0),
    onPrimary = Color(0xFF3C2E18),
    primaryContainer = Color(0xFF55432C),
    onPrimaryContainer = Color(0xFFF6E1C4),
    secondary = Color(0xFFD8C7B5),
    onSecondary = Color(0xFF3A3125),
    secondaryContainer = Color(0xFF52483B),
    onSecondaryContainer = Color(0xFFF4E4D2),
    tertiary = Color(0xFFB7CDB4),
    onTertiary = Color(0xFF233723),
    background = Color(DARK_BACKGROUND_ARGB),
    onBackground = Color(0xFFE8E2D7),
    surface = Color(DARK_BACKGROUND_ARGB),
    onSurface = Color(0xFFE8E2D7),
    surfaceVariant = Color(0xFF4D463A),
    onSurfaceVariant = Color(0xFFD0C6B4),
    outline = Color(0xFF998F80),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
)

@Composable
fun MoodCairnsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
