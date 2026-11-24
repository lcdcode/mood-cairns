package com.lcdcode.moodcairns.ui.scales

import com.lcdcode.moodcairns.data.db.Seed
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
    fun primaries_have_expected_hsv() {
        assertHsv(Hsv(0f, 1f, 1f), hsvOf(0xFFFF0000.toInt()))
        assertHsv(Hsv(120f, 1f, 1f), hsvOf(0xFF00FF00.toInt()))
        assertHsv(Hsv(240f, 1f, 1f), hsvOf(0xFF0000FF.toInt()))
        assertHsv(Hsv(60f, 1f, 1f), hsvOf(0xFFFFFF00.toInt()))
        assertHsv(Hsv(300f, 1f, 1f), hsvOf(0xFFFF00FF.toInt()))
    }

    @Test
    fun grays_have_zero_hue_and_saturation() {
        assertHsv(Hsv(0f, 0f, 1f), hsvOf(0xFFFFFFFF.toInt()))
        assertHsv(Hsv(0f, 0f, 0f), hsvOf(0xFF000000.toInt()))
        assertHsv(Hsv(0f, 0f, 0x80 / 255f), hsvOf(0xFF808080.toInt()))
    }

    @Test
    fun hsv_to_argb_primaries() {
        assertEquals(0xFFFF0000.toInt(), Hsv(0f, 1f, 1f).toArgb())
        assertEquals(0xFF00FF00.toInt(), Hsv(120f, 1f, 1f).toArgb())
        assertEquals(0xFF0000FF.toInt(), Hsv(240f, 1f, 1f).toArgb())
    }

    @Test
    fun hue360_wraps_to_red() {
        assertEquals(Hsv(0f, 1f, 1f).toArgb(), Hsv(360f, 1f, 1f).toArgb())
    }

    @Test
    fun out_of_range_components_are_clamped() {
        assertEquals(0xFFFFFFFF.toInt(), Hsv(0f, -1f, 2f).toArgb())
        assertEquals(0xFF000000.toInt(), Hsv(0f, 1f, -1f).toArgb())
    }

    @Test
    fun hsv_to_argb_is_always_opaque() {
        assertEquals(0xFF, Hsv(200f, 0.5f, 0.5f).toArgb() ushr 24)
    }

    /** Every color the hex field can produce must survive the trip through HSV state. */
    @Test
    fun argb_to_hsv_to_argb_round_trips_exactly() {
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
    fun seed_and_palette_colors_round_trip() {
        PRESETS.forEach { assertEquals(formatHexColor(it), it, hsvOf(it).toArgb()) }
    }

    @Test
    fun contrast_ratio_matches_wcag_endpoints() {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        assertEquals(21.0, contrastRatio(black, white), 1e-9)
        assertEquals(1.0, contrastRatio(white, white), 1e-9)
        assertEquals(contrastRatio(black, white), contrastRatio(white, black), 1e-9)
    }

    @Test
    fun relative_luminance_uses_linear_segment_for_dark_channels() {
        // 10/255 is under the 0.04045 cutoff, so it is divided by 12.92, not gamma-expanded.
        assertEquals(10 / 255.0 / 12.92, relativeLuminance(0xFF0A0A0A.toInt()), 1e-9)
    }

    @Test
    fun happiness_yellow_on_light_background_is_pinned() {
        val yellow = 0xFFF6C453.toInt()
        assertEquals(1.52, contrastRatio(yellow, ThemeTone.LIGHT.backgroundArgb), 0.01)
    }

    /** The warning flags vanishing colors only; the curated presets must never trip it. */
    @Test
    fun presets_never_warn() {
        val warned = PRESETS.filter { lowContrastThemes(it).isNotEmpty() }.map(::formatHexColor)
        assertEquals(emptyList<String>(), warned)
    }

    @Test
    fun pale_color_warns_for_light_only() {
        assertEquals(listOf(ThemeTone.LIGHT), lowContrastThemes(0xFFFFFFF0.toInt()))
    }

    @Test
    fun near_black_warns_for_dark_only() {
        assertEquals(listOf(ThemeTone.DARK), lowContrastThemes(0xFF1A1A1A.toInt()))
    }

    /** At 1.5:1 the light and dark danger zones cannot overlap, so at most one line shows. */
    @Test
    fun no_color_warns_for_both_themes() {
        val levels = 0..255 step 5
        for (r in levels) for (g in levels) for (b in levels) {
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            assertTrue(formatHexColor(argb), lowContrastThemes(argb).size <= 1)
        }
    }

    private companion object {
        val PRESETS = ScaleEditUiState.PALETTE

        /** WCAG 2.1 SC 1.4.11 minimum for meaningful graphics. */
        const val WCAG_GRAPHICS_CONTRAST = 3.0
    }

    @Test
    fun hsv_of_ignores_alpha() {
        assertEquals(hsvOf(0xFF6BAA75.toInt()), hsvOf(0x006BAA75))
    }

    @Test
    fun parse_hex_color_accepts_with_or_without_hash_any_case() {
        val expected = 0xFF6BAA75.toInt()
        assertEquals(expected, parseHexColor("#6BAA75"))
        assertEquals(expected, parseHexColor("6baa75"))
        assertEquals(expected, parseHexColor("  #6bAA75 "))
    }

    @Test
    fun parse_hex_color_rejects_malformed() {
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
    fun format_hex_color_pads_and_drops_alpha() {
        assertEquals("#00000A", formatHexColor(0xFF00000A.toInt()))
        assertEquals("#6BAA75", formatHexColor(0x806BAA75.toInt()))
        assertEquals("6BAA75", hexDigits(0xFF6BAA75.toInt()))
    }

    @Test
    fun sanitize_hex_input_keeps_six_hex_digits_uppercased() {
        assertEquals("6BAA75", sanitizeHexInput("#6b-aa75zz9"))
        assertEquals("ABC", sanitizeHexInput("xyzabc"))
        assertEquals("", sanitizeHexInput("#"))
    }

    @Test
    fun is_complete_hex_requires_six_digits() {
        assertTrue(isCompleteHex("6BAA75"))
        assertFalse(isCompleteHex("6BAA7"))
        assertFalse(isCompleteHex(""))
    }

    @Test
    fun sleep_seed_color_is_readable_on_both_themes() {
        for (tone in ThemeTone.entries) {
            val ratio = contrastRatio(Seed.SLEEP_COLOR_ARGB, tone.backgroundArgb)
            assertTrue(
                "$tone contrast $ratio below $WCAG_GRAPHICS_CONTRAST",
                ratio >= WCAG_GRAPHICS_CONTRAST,
            )
        }
        assertEquals(emptyList<ThemeTone>(), lowContrastThemes(Seed.SLEEP_COLOR_ARGB))
    }
}
