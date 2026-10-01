package com.flopster101.siliconplayer.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
internal val HdmiIcon: ImageVector
    get() {
        if (_hdmi != null) {
            return _hdmi!!
        }
        _hdmi = ImageVector.Builder(
            name = "HdmiIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960.0f,
            viewportHeight = 960.0f
        ).apply {
            path(
                fill = SolidColor(Color(0xFF000000)),
                fillAlpha = 1.0f,
                stroke = null,
                strokeAlpha = 1.0f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 4f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(320f, 880f)
                lineTo(320f, 760f)
                lineTo(200f, 520f)
                lineTo(200f, 280f)
                lineTo(240f, 280f)
                lineTo(240f, 160f)
                quadTo(240f, 127f, 263.5f, 103.5f)
                quadTo(287f, 80f, 320f, 80f)
                lineTo(640f, 80f)
                quadTo(673f, 80f, 696.5f, 103.5f)
                quadTo(720f, 127f, 720f, 160f)
                lineTo(720f, 280f)
                lineTo(760f, 280f)
                lineTo(760f, 520f)
                lineTo(640f, 760f)
                lineTo(640f, 880f)
                lineTo(320f, 880f)
                close()
                moveTo(320f, 280f)
                lineTo(400f, 280f)
                lineTo(400f, 200f)
                lineTo(440f, 200f)
                lineTo(440f, 280f)
                lineTo(520f, 280f)
                lineTo(520f, 200f)
                lineTo(560f, 200f)
                lineTo(560f, 280f)
                lineTo(640f, 280f)
                lineTo(640f, 160f)
                lineTo(320f, 160f)
                lineTo(320f, 280f)
                close()
                moveTo(400f, 800f)
                lineTo(560f, 800f)
                lineTo(560f, 740f)
                lineTo(680f, 500f)
                lineTo(680f, 360f)
                lineTo(280f, 360f)
                lineTo(280f, 500f)
                lineTo(400f, 740f)
                lineTo(400f, 800f)
                close()
                moveTo(480f, 500f)
                close()
            }
        }.build()
        return _hdmi!!
    }

private var _hdmi: ImageVector? = null
