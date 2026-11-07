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
        val palette = listOf(
            0xFFF6C453, 0xFF7D99D1, 0xFFD17D7D, 0xFF9AA39A, 0xFFB5651D,
            0xFF6BAA75, 0xFFA46CBF, 0xFF4DB6AC, 0xFFE57373, 0xFF5C6BC0,
        ).map(Long::toInt)
        palette.forEach { assertEquals(formatHexColor(it), it, hsvOf(it).toArgb()) }
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
