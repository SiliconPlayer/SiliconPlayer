package com.flopster101.siliconplayer.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.flopster101.siliconplayer.WatchDialogContainer
import com.flopster101.siliconplayer.adaptiveDialogModifier
import com.flopster101.siliconplayer.adaptiveDialogProperties
import com.flopster101.siliconplayer.isWatchDevice
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ColorPickerMode {
    Rgb,
    Hsv
}

internal data class HsvColor(val hue: Float, val saturation: Float, val value: Float)

internal fun rgbToHsv(r: Int, g: Int, b: Int): HsvColor {
    val rf = r.coerceIn(0, 255) / 255f
    val gf = g.coerceIn(0, 255) / 255f
    val bf = b.coerceIn(0, 255) / 255f
    val max = maxOf(rf, gf, bf)
    val min = minOf(rf, gf, bf)
    val delta = max - min

    val v = max
    val s = if (max == 0f) 0f else delta / max
    val h = when {
        delta == 0f -> 0f
        max == rf -> (((gf - bf) / delta) % 6f)
        max == gf -> (((bf - rf) / delta) + 2f)
        else -> (((rf - gf) / delta) + 4f)
    }
    val hue = ((h * 60f) % 360f + 360f) % 360f
    return HsvColor(hue, s, v)
}

internal fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Int, Int, Int> {
    val hue = ((h % 360f) + 360f) % 360f
    val sat = s.coerceIn(0f, 1f)
    val valVal = v.coerceIn(0f, 1f)

    val c = valVal * sat
    val hPrime = hue / 60f
    val x = c * (1f - abs((hPrime % 2f) - 1f))
    val m = valVal - c

    val (r1, g1, b1) = when {
        hPrime < 1f -> Triple(c, x, 0f)
        hPrime < 2f -> Triple(x, c, 0f)
        hPrime < 3f -> Triple(0f, c, x)
        hPrime < 4f -> Triple(0f, x, c)
        hPrime < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
    return Triple(r, g, b)
}

internal fun parseHexColor(input: String): Int? {
    val clean = input.trim().removePrefix("#").trim()
    return when (clean.length) {
        3 -> {
            val r = clean.substring(0, 1).toIntOrNull(16) ?: return null
            val g = clean.substring(1, 2).toIntOrNull(16) ?: return null
            val b = clean.substring(2, 3).toIntOrNull(16) ?: return null
            (0xFF shl 24) or (r * 0x11 shl 16) or (g * 0x11 shl 8) or (b * 0x11)
        }
        6 -> {
            val num = clean.toLongOrNull(16) ?: return null
            (0xFF shl 24) or num.toInt()
        }
        8 -> {
            val num = clean.toLongOrNull(16) ?: return null
            (0xFF shl 24) or (num.toInt() and 0x00FFFFFF)
        }
        else -> null
    }
}

internal fun formatHexRgb(r: Int, g: Int, b: Int): String {
    return String.format(Locale.US, "#%02X%02X%02X", r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

internal fun asArgbInt(r: Int, g: Int, b: Int): Int {
    return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}

@Composable
fun ColorPickerDialog(
    title: String,
    initialArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(ColorPickerMode.Rgb) }

    var red by remember(initialArgb) { mutableIntStateOf((initialArgb shr 16) and 0xFF) }
    var green by remember(initialArgb) { mutableIntStateOf((initialArgb shr 8) and 0xFF) }
    var blue by remember(initialArgb) { mutableIntStateOf(initialArgb and 0xFF) }

    val initialHsv = remember(initialArgb) {
        rgbToHsv((initialArgb shr 16) and 0xFF, (initialArgb shr 8) and 0xFF, initialArgb and 0xFF)
    }
    var hue by remember(initialArgb) { mutableFloatStateOf(initialHsv.hue) }
    var saturation by remember(initialArgb) { mutableFloatStateOf(initialHsv.saturation) }
    var value by remember(initialArgb) { mutableFloatStateOf(initialHsv.value) }

    var hexText by remember(initialArgb) { mutableStateOf(formatHexRgb(red, green, blue)) }

    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun updateFromRgb(r: Int, g: Int, b: Int) {
        red = r.coerceIn(0, 255)
        green = g.coerceIn(0, 255)
        blue = b.coerceIn(0, 255)
        val hsv = rgbToHsv(red, green, blue)
        if (hsv.saturation > 0.001f) {
            hue = hsv.hue
        }
        saturation = hsv.saturation
        value = hsv.value
        hexText = formatHexRgb(red, green, blue)
    }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = ((h % 360f) + 360f) % 360f
        saturation = s.coerceIn(0f, 1f)
        value = v.coerceIn(0f, 1f)
        val (r, g, b) = hsvToRgb(hue, saturation, value)
        red = r
        green = g
        blue = b
        hexText = formatHexRgb(red, green, blue)
    }

    fun onHexInputChanged(newText: String) {
        hexText = newText
        val parsed = parseHexColor(newText)
        if (parsed != null) {
            val r = (parsed shr 16) and 0xFF
            val g = (parsed shr 8) and 0xFF
            val b = parsed and 0xFF
            red = r
            green = g
            blue = b
            val hsv = rgbToHsv(r, g, b)
            if (hsv.saturation > 0.001f) {
                hue = hsv.hue
            }
            saturation = hsv.saturation
            value = hsv.value
        }
    }

    val previewColor = Color(asArgbInt(red, green, blue))

    if (isWatchDevice()) {
        WatchDialogContainer(
            title = title,
            onDismissRequest = onDismiss
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(previewColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            )
            OutlinedTextField(
                value = hexText,
                onValueChange = ::onHexInputChanged,
                label = { Text("HEX") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = mode == ColorPickerMode.Rgb,
                    onClick = { mode = ColorPickerMode.Rgb },
                    label = { Text("RGB", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = mode == ColorPickerMode.Hsv,
                    onClick = { mode = ColorPickerMode.Hsv },
                    label = { Text("HSV", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f)
                )
            }
            if (mode == ColorPickerMode.Rgb) {
                Text("Red: $red", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = red.toFloat(),
                    onValueChange = { updateFromRgb(it.roundToInt(), green, blue) },
                    valueRange = 0f..255f
                )
                Text("Green: $green", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = green.toFloat(),
                    onValueChange = { updateFromRgb(red, it.roundToInt(), blue) },
                    valueRange = 0f..255f
                )
                Text("Blue: $blue", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = blue.toFloat(),
                    onValueChange = { updateFromRgb(red, green, it.roundToInt()) },
                    valueRange = 0f..255f
                )
            } else {
                Text("Hue: ${hue.roundToInt()}°", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = hue,
                    onValueChange = { updateFromHsv(it, saturation, value) },
                    valueRange = 0f..360f
                )
                Text("Saturation: ${(saturation * 100f).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = saturation,
                    onValueChange = { updateFromHsv(hue, it, value) },
                    valueRange = 0f..1f
                )
                Text("Value: ${(value * 100f).roundToInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = value,
                    onValueChange = { updateFromHsv(hue, saturation, it) },
                    valueRange = 0f..1f
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    onConfirm(asArgbInt(red, green, blue))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    } else {
        AlertDialog(
            modifier = adaptiveDialogModifier(),
            properties = adaptiveDialogProperties(),
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(previewColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    )

                    OutlinedTextField(
                        value = hexText,
                        onValueChange = ::onHexInputChanged,
                        label = { Text("HEX") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(hexText))
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy HEX",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        isError = hexText.isNotBlank() && parseHexColor(hexText) == null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = mode == ColorPickerMode.Rgb,
                            onClick = { mode = ColorPickerMode.Rgb },
                            label = { Text("RGB", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            leadingIcon = if (mode == ColorPickerMode.Rgb) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = mode == ColorPickerMode.Hsv,
                            onClick = { mode = ColorPickerMode.Hsv },
                            label = { Text("HSV", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            leadingIcon = if (mode == ColorPickerMode.Hsv) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (mode == ColorPickerMode.Rgb) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Red", style = MaterialTheme.typography.bodySmall)
                                Text("$red", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = red.toFloat(),
                                onValueChange = { updateFromRgb(it.roundToInt(), green, blue) },
                                valueRange = 0f..255f
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Green", style = MaterialTheme.typography.bodySmall)
                                Text("$green", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = green.toFloat(),
                                onValueChange = { updateFromRgb(red, it.roundToInt(), blue) },
                                valueRange = 0f..255f
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Blue", style = MaterialTheme.typography.bodySmall)
                                Text("$blue", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = blue.toFloat(),
                                onValueChange = { updateFromRgb(red, green, it.roundToInt()) },
                                valueRange = 0f..255f
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Hue", style = MaterialTheme.typography.bodySmall)
                                Text("${hue.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = hue,
                                onValueChange = { updateFromHsv(it, saturation, value) },
                                valueRange = 0f..360f
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Saturation", style = MaterialTheme.typography.bodySmall)
                                Text("${(saturation * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = saturation,
                                onValueChange = { updateFromHsv(hue, it, value) },
                                valueRange = 0f..1f
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Value", style = MaterialTheme.typography.bodySmall)
                                Text("${(value * 100f).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = value,
                                onValueChange = { updateFromHsv(hue, saturation, it) },
                                valueRange = 0f..1f
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(asArgbInt(red, green, blue)) }) {
                    Text("Save")
                }
            }
        )
    }
}
