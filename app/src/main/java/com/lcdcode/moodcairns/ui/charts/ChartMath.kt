package com.lcdcode.moodcairns.ui.charts

import com.lcdcode.moodcairns.data.entity.Scale

/**
 * Maps a raw value onto the Absolute chart's shared 0..1 axis using the scale's
 * full range. Inverse scales are flipped so "better" always points up; raw
 * values elsewhere (auto-fit mode, tapped-point card) are never flipped.
 */
internal fun normalize(value: Float, scale: Scale): Float {
    val span = (scale.maxValue - scale.minValue).toFloat()
    if (span <= 0f) return 0.5f
    val n = ((value - scale.minValue) / span).coerceIn(0f, 1f)
    return if (scale.inverted) 1f - n else n
}
