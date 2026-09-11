package com.lcdcode.moodcairns.ui.common

import com.lcdcode.moodcairns.data.entity.Scale

/** Range text that stays unambiguous with negative bounds: "1–10" but "-5 to 5". */
fun rangeLabel(min: Int, max: Int): String =
    if (min < 0) "$min to $max" else "$min–$max"

/** Renders a logged value without a trailing ".0" when it's whole. */
fun formatScaleValue(v: Float): String {
    val rounded = kotlin.math.round(v)
    return if (kotlin.math.abs(v - rounded) < 1e-3f) rounded.toInt().toString()
    else "%.2f".format(v).trimEnd('0').trimEnd('.')
}

/**
 * A value with its scale context: "7 / 10" for non-negative ranges, but
 * "-3 (-5 to 5)" when the range dips negative, since "v / max" hides the floor.
 */
fun formatValueWithRange(value: Float, scale: Scale): String =
    if (scale.minValue < 0) {
        "${formatScaleValue(value)} (${rangeLabel(scale.minValue, scale.maxValue)})"
    } else {
        "${formatScaleValue(value)} / ${scale.maxValue}"
    }
