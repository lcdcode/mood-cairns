package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.data.entity.Scale
import com.lcdcode.moodcairns.i18n.TestResources
import com.lcdcode.moodcairns.ui.common.UiText
import com.lcdcode.moodcairns.ui.common.rangeLabel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins vertical-axis tick labels and the caption that disambiguates them. */
class ChartAxisTest {

    private val en = Locale.US

    private fun scale(name: String, min: Int, max: Int, inverted: Boolean = false) =
        Scale(name = name, minValue = min, maxValue = max, colorArgb = 0, inverted = inverted)

    @Test
    fun autoFit_labelsAreRawValues() {
        assertEquals("7", yAxisLabel(7.0, absoluteY = false, en))
        assertEquals("-3", yAxisLabel(-3.0, absoluteY = false, en))
        assertEquals("2.5", yAxisLabel(2.5, absoluteY = false, en))
    }

    @Test
    fun absolute_labelsArePercentages() {
        assertEquals("0%", yAxisLabel(0.0, absoluteY = true, en))
        assertEquals("50%", yAxisLabel(0.5, absoluteY = true, en))
        assertEquals("100%", yAxisLabel(1.0, absoluteY = true, en))
    }

    @Test
    fun absolute_percentFollowsLocale() {
        // German puts a (non-breaking) space before the percent sign.
        assertEquals("50\u00A0%", yAxisLabel(0.5, absoluteY = true, Locale.GERMANY))
    }

    @Test
    fun singleScale_captionNamesItAndItsRange() {
        assertEquals(
            UiText.Res(R.string.charts_axis_caption_single, listOf("Mood", rangeLabel(1, 10, en))),
            yAxisCaption(listOf(scale("Mood", 1, 10)), absoluteY = false, en),
        )
    }

    @Test
    fun singleNegativeScale_captionSpellsOutTheFloor() {
        val caption = yAxisCaption(listOf(scale("Energy", -5, 5)), absoluteY = false, en)
        assertEquals(
            UiText.Res(
                R.string.charts_axis_caption_single,
                listOf("Energy", rangeLabel(-5, 5, en)),
            ),
            caption,
        )
    }

    @Test
    fun builtInScale_captionUsesItsTranslatedName() {
        val happiness =
            Scale(name = "Happiness", minValue = 1, maxValue = 10, colorArgb = 0, isBuiltIn = true)
        assertEquals(
            UiText.Res(
                R.string.charts_axis_caption_single,
                listOf(UiText.Res(R.string.seed_scale_happiness), rangeLabel(1, 10, en)),
            ),
            yAxisCaption(listOf(happiness), absoluteY = false, en),
        )
    }

    @Test
    fun multipleScales_captionWarnsTheAxisIsShared() {
        val scales = listOf(scale("Mood", 1, 10), scale("Energy", -5, 5))
        assertEquals(
            UiText.Plural(R.plurals.charts_axis_caption_shared, 2),
            yAxisCaption(scales, absoluteY = false, en),
        )
    }

    @Test
    fun absolute_captionIgnoresScalesAndExplainsPercent() {
        val scales = listOf(scale("Mood", 1, 10), scale("Pain", -5, 5, inverted = true))
        assertEquals(
            UiText.Res(R.string.charts_axis_caption_absolute),
            yAxisCaption(scales, absoluteY = true, en),
        )
        val caption = TestResources.englishString("charts_axis_caption_absolute")
        assertTrue(caption, caption.contains("100% = top of range"))
        // "best" would be wrong for a high-is-bad scale left unflagged.
        assertFalse(caption, caption.contains("best"))
    }

    @Test
    fun noScales_captionStaysGeneric() {
        assertEquals(
            UiText.Res(R.string.charts_axis_caption_values),
            yAxisCaption(emptyList(), absoluteY = false, en),
        )
    }
}
