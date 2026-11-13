package com.lcdcode.moodcairns.ui.scales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the color picker's HSV conversion and hex parsing. */
class ScaleColorTest {

    private val tolerance = 1e-4f

    private fun assertHsv(expected: Hsv, actual: Hsv) {
        assertEquals("hue", expected.hue, actual.hue, tolerance)
        assertEquals("saturation", expected.saturation, actual.saturation, tolerance)
        assertEquals("value", expected.value, actual.value, tolerance)
    }

    @Test
    fun primaries_haveExpectedHsv() {
        assertHsv(Hsv(0f, 1f, 1f), hsvOf(0xFFFF0000.toInt()))
        assertHsv(Hsv(120f, 1f, 1f), hsvOf(0xFF00FF00.toInt()))
        assertHsv(Hsv(240f, 1f, 1f), hsvOf(0xFF0000FF.toInt()))
        assertHsv(Hsv(60f, 1f, 1f), hsvOf(0xFFFFFF00.toInt()))
        assertHsv(Hsv(300f, 1f, 1f), hsvOf(0xFFFF00FF.toInt()))
    }

    @Test
    fun grays_haveZeroHueAndSaturation() {
        assertHsv(Hsv(0f, 0f, 1f), hsvOf(0xFFFFFFFF.toInt()))
        assertHsv(Hsv(0f, 0f, 0f), hsvOf(0xFF000000.toInt()))
        assertHsv(Hsv(0f, 0f, 0x80 / 255f), hsvOf(0xFF808080.toInt()))
    }

    @Test
    fun hsvToArgb_primaries() {
        assertEquals(0xFFFF0000.toInt(), Hsv(0f, 1f, 1f).toArgb())
        assertEquals(0xFF00FF00.toInt(), Hsv(120f, 1f, 1f).toArgb())
        assertEquals(0xFF0000FF.toInt(), Hsv(240f, 1f, 1f).toArgb())
    }

    @Test
    fun hue360_wrapsToRed() {
        assertEquals(Hsv(0f, 1f, 1f).toArgb(), Hsv(360f, 1f, 1f).toArgb())
    }

    @Test
    fun outOfRangeComponents_areClamped() {
        assertEquals(0xFFFFFFFF.toInt(), Hsv(0f, -1f, 2f).toArgb())
        assertEquals(0xFF000000.toInt(), Hsv(0f, 1f, -1f).toArgb())
    }

    @Test
    fun hsvToArgb_isAlwaysOpaque() {
        assertEquals(0xFF, Hsv(200f, 0.5f, 0.5f).toArgb() ushr 24)
    }

    /** Every color the hex field can produce must survive the trip through HSV state. */
    @Test
    fun argbToHsvToArgb_roundTripsExactly() {
        val levels = (0..255 step 15) + listOf(1, 127, 128, 254)
        val failures = mutableListOf<String>()
        for (r in levels) for (g in levels) for (b in levels) {
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val back = hsvOf(argb).toArgb()
            if (back != argb) failures += "${formatHexColor(argb)} -> ${formatHexColor(back)}"
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun seedAndPaletteColors_roundTrip() {
        PRESETS.forEach { assertEquals(formatHexColor(it), it, hsvOf(it).toArgb()) }
    }

    @Test
    fun contrastRatio_matchesWcagEndpoints() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        assertEquals(21.0, contrastRatio(black, white), 1e-9)
        assertEquals(1.0, contrastRatio(white, white), 1e-9)
        assertEquals(contrastRatio(black, white), contrastRatio(white, black), 1e-9)
    }

    @Test
    fun relativeLuminance_usesLinearSegmentForDarkChannels() {
        // 10/255 is under the 0.04045 cutoff, so it is divided by 12.92, not gamma-expanded.
        assertEquals(10 / 255.0 / 12.92, relativeLuminance(0xFF0A0A0A.toInt()), 1e-9)
    }

    @Test
    fun happinessYellow_onLightBackground_isPinned() {
        val yellow = 0xFFF6C453.toInt()
        assertEquals(1.52, contrastRatio(yellow, ThemeTone.LIGHT.backgroundArgb), 0.01)
    }

    /** The warning flags vanishing colors only; the curated presets must never trip it. */
    @Test
    fun presets_neverWarn() {
        val warned = PRESETS.filter { lowContrastThemes(it).isNotEmpty() }.map(::formatHexColor)
        assertEquals(emptyList<String>(), warned)
    }

    @Test
    fun paleColor_warnsForLightOnly() {
        assertEquals(listOf(ThemeTone.LIGHT), lowContrastThemes(0xFFFFFFF0.toInt()))
    }

    @Test
    fun nearBlack_warnsForDarkOnly() {
        assertEquals(listOf(ThemeTone.DARK), lowContrastThemes(0xFF1A1A1A.toInt()))
    }

    /** At 1.5:1 the light and dark danger zones cannot overlap, so at most one line shows. */
    @Test
    fun noColor_warnsForBothThemes() {
        val levels = 0..255 step 5
        for (r in levels) for (g in levels) for (b in levels) {
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            assertTrue(formatHexColor(argb), lowContrastThemes(argb).size <= 1)
        }
    }

    private companion object {
        val PRESETS = ScaleEditUiState.PALETTE
    }

    @Test
    fun hsvOf_ignoresAlpha() {
        assertEquals(hsvOf(0xFF6BAA75.toInt()), hsvOf(0x006BAA75))
    }

    @Test
    fun parseHexColor_acceptsWithOrWithoutHash_anyCase() {
        val expected = 0xFF6BAA75.toInt()
        assertEquals(expected, parseHexColor("#6BAA75"))
        assertEquals(expected, parseHexColor("6baa75"))
        assertEquals(expected, parseHexColor("  #6bAA75 "))
    }

    @Test
    fun parseHexColor_rejectsMalformed() {
        assertNull(parseHexColor(""))
        assertNull(parseHexColor("#"))
        assertNull(parseHexColor("#6BAA7"))
        assertNull(parseHexColor("#6BAA755"))
        assertNull(parseHexColor("#FF6BAA75"))
        assertNull(parseHexColor("#GGGGGG"))
        assertNull(parseHexColor("##6BAA75"))
        assertNull(parseHexColor("-6BAA75"))
    }

    @Test
    fun formatHexColor_padsAndDropsAlpha() {
        assertEquals("#00000A", formatHexColor(0xFF00000A.toInt()))
        assertEquals("#6BAA75", formatHexColor(0x806BAA75.toInt()))
        assertEquals("6BAA75", hexDigits(0xFF6BAA75.toInt()))
    }

    @Test
    fun sanitizeHexInput_keepsSixHexDigitsUppercased() {
        assertEquals("6BAA75", sanitizeHexInput("#6b-aa75zz9"))
        assertEquals("ABC", sanitizeHexInput("xyzabc"))
        assertEquals("", sanitizeHexInput("#"))
    }

    @Test
    fun isCompleteHex_requiresSixDigits() {
        assertTrue(isCompleteHex("6BAA75"))
        assertFalse(isCompleteHex("6BAA7"))
        assertFalse(isCompleteHex(""))
    }
}
