package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.firstParsableJson
import com.flopster101.siliconplayer.readCandidateTexts
import com.flopster101.siliconplayer.writeTextAtomic
import org.json.JSONObject
import java.io.File

internal const val DesktopWindowStateFileName = "window.json"
internal const val DesktopWindowDefaultWidthDp = 1100f
internal const val DesktopWindowDefaultHeightDp = 750f
internal const val DesktopWindowMinWidthDp = 480f
internal const val DesktopWindowMinHeightDp = 320f
internal const val DesktopWindowMaxWidthDp = 7680f
internal const val DesktopWindowMaxHeightDp = 4320f

internal data class DesktopWindowGeometry(
    val widthDp: Float,
    val heightDp: Float,
    val xDp: Float?,
    val yDp: Float?
)

// Best-effort restore of the last window size and position. A missing or
// corrupt file falls back to the defaults; out-of-range sizes are clamped so
// a stale file can never strand an unusable window.
internal fun loadDesktopWindowGeometry(configDir: File = DesktopPaths.configDir()): DesktopWindowGeometry? {
    val file = File(configDir, DesktopWindowStateFileName)
    val raw = firstParsableJson(readCandidateTexts(file), isObject = true) ?: return null
    return runCatching {
        val root = JSONObject(raw)
        val width = root.optDouble("widthDp", Double.NaN).toFloat()
        val height = root.optDouble("heightDp", Double.NaN).toFloat()
        if (!width.isFinite() || !height.isFinite()) return@runCatching null
        DesktopWindowGeometry(
            widthDp = width.coerceIn(DesktopWindowMinWidthDp, DesktopWindowMaxWidthDp),
            heightDp = height.coerceIn(DesktopWindowMinHeightDp, DesktopWindowMaxHeightDp),
            xDp = root.optDouble("xDp", Double.NaN).toFloat().takeIf { it.isFinite() },
            yDp = root.optDouble("yDp", Double.NaN).toFloat().takeIf { it.isFinite() }
        )
    }.getOrNull()
}

internal fun saveDesktopWindowGeometry(
    configDir: File = DesktopPaths.configDir(),
    geometry: DesktopWindowGeometry
) {
    if (!geometry.widthDp.isFinite() || !geometry.heightDp.isFinite()) return
    val root = JSONObject()
    root.put("widthDp", geometry.widthDp.coerceIn(DesktopWindowMinWidthDp, DesktopWindowMaxWidthDp).toDouble())
    root.put("heightDp", geometry.heightDp.coerceIn(DesktopWindowMinHeightDp, DesktopWindowMaxHeightDp).toDouble())
    if (geometry.xDp != null && geometry.yDp != null && geometry.xDp.isFinite() && geometry.yDp.isFinite()) {
        root.put("xDp", geometry.xDp.toDouble())
        root.put("yDp", geometry.yDp.toDouble())
    }
    writeTextAtomic(File(configDir, DesktopWindowStateFileName), root.toString())
}
