package com.lcdcode.moodcairns.ui.scales

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lcdcode.moodcairns.R

private const val HEX_PREFIX = "#"
private const val MAX_HUE = 360f
private const val HUE_STOPS = 6
private val PANEL_HEIGHT = 180.dp
private val HUE_BAR_HEIGHT = 28.dp
private val PREVIEW_SIZE = 56.dp
private val THUMB_RADIUS = 9.dp
private val THUMB_STROKE = 3.dp
private val WARNING_ICON_SIZE = 18.dp
private val CORNER = RoundedCornerShape(8.dp)

/** Red through the spectrum and back to red, for the hue bar's gradient. */
private val HUE_SPECTRUM: List<Color> = (0..HUE_STOPS).map { stop ->
    Color(Hsv(hue = stop * MAX_HUE / HUE_STOPS, saturation = 1f, value = 1f).toArgb())
}

/**
 * Picks any opaque color: a saturation/brightness panel, a hue bar, and a hex field.
 * State is kept as HSV so the hue survives dragging to gray or black.
 */
@Composable
internal fun ColorPickerDialog(
    initialArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var hsv by remember { mutableStateOf(hsvOf(initialArgb)) }
    var hexInput by remember { mutableStateOf(hexDigits(initialArgb)) }
    val hexValid = isCompleteHex(hexInput)

    fun updateFromPicker(updated: Hsv) {
        hsv = updated
        hexInput = hexDigits(updated.toArgb())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scale_edit_color_picker_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SaturationValuePanel(hsv = hsv, onChange = ::updateFromPicker)
                HueBar(
                    hue = hsv.hue,
                    onChange = { hue -> updateFromPicker(hsv.copy(hue = hue)) },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ColorComparison(before = initialArgb, after = hsv.toArgb())
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = sanitizeHexInput(raw)
                            parseHexColor(hexInput)?.let { hsv = hsvOf(it) }
                        },
                        label = { Text(stringResource(R.string.scale_edit_color_hex_label)) },
                        prefix = { Text(HEX_PREFIX) },
                        isError = !hexValid,
                        // Always laid out, so the error never changes the dialog's height.
                        supportingText = {
                            Text(
                                stringResource(R.string.scale_edit_error_hex_incomplete),
                                modifier = Modifier.hiddenWhen(hexValid),
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                LowContrastWarning(hsv.toArgb(), reserveSpace = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hsv.toArgb()) }, enabled = hexValid) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** Saturation runs left to right, brightness bottom to top, at the current hue. */
@Composable
private fun SaturationValuePanel(hsv: Hsv, onChange: (Hsv) -> Unit) {
    val latestHsv by rememberUpdatedState(hsv)
    val latestOnChange by rememberUpdatedState(onChange)
    val pureHue = Color(Hsv(hsv.hue, saturation = 1f, value = 1f).toArgb())
    val description = stringResource(R.string.scale_edit_color_panel_description)
    val hexState = formatHexColor(hsv.toArgb())

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(PANEL_HEIGHT)
            .clip(CORNER)
            .semantics {
                contentDescription = description
                stateDescription = hexState
            }
            .pointerInput(Unit) {
                trackPointer { position ->
                    val saturation = (position.x / size.width).coerceIn(0f, 1f)
                    val value = 1f - (position.y / size.height).coerceIn(0f, 1f)
                    latestOnChange(latestHsv.copy(saturation = saturation, value = value))
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, pureHue)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        drawThumb(Offset(hsv.saturation * size.width, (1f - hsv.value) * size.height))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    val latestOnChange by rememberUpdatedState(onChange)
    val description = stringResource(R.string.scale_edit_color_hue_description)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(HUE_BAR_HEIGHT)
            .clip(CORNER)
            // Slider-like semantics so TalkBack users can adjust the hue too.
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..MAX_HUE)
                setProgress { target ->
                    latestOnChange(target.coerceIn(0f, MAX_HUE))
                    true
                }
            }
            .pointerInput(Unit) {
                trackPointer { position ->
                    latestOnChange((position.x / size.width).coerceIn(0f, 1f) * MAX_HUE)
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(HUE_SPECTRUM))
        drawThumb(Offset(hue / MAX_HUE * size.width, size.height / 2f))
    }
}

/** Reports the press position, then every drag position, until the pointer lifts. */
private suspend fun PointerInputScope.trackPointer(onPosition: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onPosition(down.position)
        down.consume()
        drag(down.id) { change ->
            onPosition(change.position)
            change.consume()
        }
    }
}

/** A white ring with a dark outline, visible on any color. */
private fun DrawScope.drawThumb(center: Offset) {
    val radius = THUMB_RADIUS.toPx()
    val stroke = THUMB_STROKE.toPx()
    val outline = Stroke(1.dp.toPx())
    drawCircle(Color.Black, radius = radius + stroke / 2f, center = center, style = outline)
    drawCircle(Color.White, radius = radius, center = center, style = Stroke(stroke))
}

/**
 * Warns when [argb] would nearly vanish on a theme. With [reserveSpace], every message
 * is laid out (invisibly when inactive) so the slot is always as tall as the tallest
 * translation: showing a warning mid-drag must not resize the dialog and move the
 * panel under the finger. Messages overlap in one Box, which is safe because a color
 * can never be low-contrast on both themes (see ScaleColorTest).
 */
@Composable
internal fun LowContrastWarning(argb: Int, reserveSpace: Boolean = false) {
    val active = lowContrastThemes(argb)
    val announcement = active.firstOrNull()?.let { stringResource(lowContrastMessage(it)) }
    val laidOut = if (reserveSpace) ThemeTone.entries else active

    // One stable live-region node whose text changes, so TalkBack announces the warning
    // when it appears; the visual lines below are hidden from screen readers.
    Box(
        modifier = Modifier.clearAndSetSemantics {
            liveRegion = LiveRegionMode.Polite
            announcement?.let { contentDescription = it }
        },
    ) {
        laidOut.forEach { tone ->
            WarningLine(tone, modifier = Modifier.hiddenWhen(tone !in active))
        }
    }
}

@Composable
private fun WarningLine(tone: ThemeTone, modifier: Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(WARNING_ICON_SIZE),
        )
        Text(
            stringResource(lowContrastMessage(tone)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun lowContrastMessage(tone: ThemeTone): Int = when (tone) {
    ThemeTone.LIGHT -> R.string.scale_edit_color_low_contrast_light
    ThemeTone.DARK -> R.string.scale_edit_color_low_contrast_dark
}

/** Keeps the layout slot but hides it from sight and from screen readers. */
private fun Modifier.hiddenWhen(hidden: Boolean): Modifier =
    if (hidden) alpha(0f).clearAndSetSemantics {} else this

/** The original color on the left, the new one on the right. */
@Composable
private fun ColorComparison(before: Int, after: Int) {
    Row(
        modifier = Modifier
            .size(PREVIEW_SIZE)
            .clip(CORNER)
            .border(1.dp, MaterialTheme.colorScheme.outline, CORNER),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(before)))
        Box(Modifier.weight(1f).fillMaxHeight().background(Color(after)))
    }
}
