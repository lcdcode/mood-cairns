package com.lcdcode.moodcairns.ui.scales

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// Pure Kotlin so it runs in JVM unit tests, where android.graphics.Color is a stub.

private const val OPAQUE = 0xFF000000.toInt()
private const val RGB_MASK = 0x00FFFFFF
private const val CHANNEL_MAX = 255f
private const val FULL_TURN = 360f
private const val HUE_SECTOR = 60f
private const val HEX_DIGITS = 6
private val HEX_COLOR = Regex("^#?([0-9A-Fa-f]{$HEX_DIGITS})$")

/** Hue in degrees [0, 360], where 360 is red again; saturation and value in [0, 1]. */
internal data class Hsv(val hue: Float, val saturation: Float, val value: Float)

/** Converts to an opaque ARGB int. Out-of-range components are wrapped or clamped. */
internal fun Hsv.toArgb(): Int {
    val v = value.coerceIn(0f, 1f)
    val chroma = v * saturation.coerceIn(0f, 1f)
    val sector = hue.mod(FULL_TURN) / HUE_SECTOR
    val x = chroma * (1f - abs(sector % 2f - 1f))
    val (r, g, b) = when (sector.toInt()) {
        0 -> Triple(chroma, x, 0f)
        1 -> Triple(x, chroma, 0f)
        2 -> Triple(0f, chroma, x)
        3 -> Triple(0f, x, chroma)
        4 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val m = v - chroma
    return OPAQUE or (toChannel(r + m) shl 16) or (toChannel(g + m) shl 8) or toChannel(b + m)
}

private fun toChannel(fraction: Float): Int = (fraction * CHANNEL_MAX).roundToInt().coerceIn(0, 255)

/** HSV of [argb], ignoring alpha. Grays get hue 0. */
internal fun hsvOf(argb: Int): Hsv {
    val r = (argb shr 16 and 0xFF) / CHANNEL_MAX
    val g = (argb shr 8 and 0xFF) / CHANNEL_MAX
    val b = (argb and 0xFF) / CHANNEL_MAX
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val hue = when {
        delta == 0f -> 0f
        max == r -> HUE_SECTOR * ((g - b) / delta).mod(6f)
        max == g -> HUE_SECTOR * ((b - r) / delta + 2f)
        else -> HUE_SECTOR * ((r - g) / delta + 4f)
    }
    val saturation = if (max == 0f) 0f else delta / max
    return Hsv(hue, saturation, max)
}

/** Parses "#RRGGBB" or "RRGGBB" into an opaque ARGB int, or null if malformed. */
internal fun parseHexColor(raw: String): Int? =
    HEX_COLOR.matchEntire(raw.trim())?.groupValues?.get(1)?.toInt(16)?.let { OPAQUE or it }

/** Formats as "#RRGGBB", dropping alpha. */
internal fun formatHexColor(argb: Int): String = "#" + hexDigits(argb)

/** The six uppercase RGB hex digits of [argb]. */
internal fun hexDigits(argb: Int): String = "%06X".format(Locale.ROOT, argb and RGB_MASK)

/** Keeps up to six hex digits, uppercased: "#6b-aa75zz9" -> "6BAA75". */
internal fun sanitizeHexInput(raw: String): String =
    raw.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        .take(HEX_DIGITS)
        .uppercase()

/** True when [digits] is a complete six-digit hex color. */
internal fun isCompleteHex(digits: String): Boolean = parseHexColor(digits) != null
