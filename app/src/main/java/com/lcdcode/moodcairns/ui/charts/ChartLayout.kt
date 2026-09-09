package com.lcdcode.moodcairns.ui.charts

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.data.entity.Scale

/** Plot height used when every plotted series sits on a non-negative range. */
internal val CHART_HEIGHT_BASE: Dp = 360.dp

/**
 * Extra headroom for signed scales: an axis spanning below zero packs the same
 * number of points into twice the value range, so it needs more room to stay
 * legible.
 */
internal const val CHART_HEIGHT_NEGATIVE_FACTOR = 1.25f

/**
 * Plot height for the given scales. Absolute mode normalizes every series onto a
 * shared 0..1 axis, so it never draws below zero and never gets the bump.
 */
internal fun chartHeight(scales: List<Scale>, absoluteY: Boolean): Dp =
    if (!absoluteY && scales.any { it.minValue < 0 }) {
        CHART_HEIGHT_BASE * CHART_HEIGHT_NEGATIVE_FACTOR
    } else {
        CHART_HEIGHT_BASE
    }
