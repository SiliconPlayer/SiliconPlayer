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
internal val CableIcon: ImageVector
    get() {
        if (_cable != null) {
            return _cable!!
        }
        _cable = ImageVector.Builder(
            name = "CableIcon",
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
                moveTo(200f, 840f)
                quadTo(183f, 840f, 171.5f, 828.5f)
                quadTo(160f, 817f, 160f, 800f)
                lineTo(160f, 760f)
                lineTo(120f, 760f)
                lineTo(120f, 600f)
                quadTo(120f, 583f, 131.5f, 571.5f)
                quadTo(143f, 560f, 160f, 560f)
                lineTo(200f, 560f)
                lineTo(200f, 280f)
                quadTo(200f, 214f, 247f, 167f)
                quadTo(294f, 120f, 360f, 120f)
                quadTo(426f, 120f, 473f, 167f)
                quadTo(520f, 214f, 520f, 280f)
                lineTo(520f, 680f)
                quadTo(520f, 713f, 543.5f, 736.5f)
                quadTo(567f, 760f, 600f, 760f)
                quadTo(633f, 760f, 656.5f, 736.5f)
                quadTo(680f, 713f, 680f, 680f)
                lineTo(680f, 400f)
                lineTo(640f, 400f)
                quadTo(623f, 400f, 611.5f, 388.5f)
                quadTo(600f, 377f, 600f, 360f)
                lineTo(600f, 200f)
                lineTo(640f, 200f)
                lineTo(640f, 160f)
                quadTo(640f, 143f, 651.5f, 131.5f)
                quadTo(663f, 120f, 680f, 120f)
                lineTo(760f, 120f)
                quadTo(777f, 120f, 788.5f, 131.5f)
                quadTo(800f, 143f, 800f, 160f)
                lineTo(800f, 200f)
                lineTo(840f, 200f)
                lineTo(840f, 360f)
                quadTo(840f, 377f, 828.5f, 388.5f)
                quadTo(817f, 400f, 800f, 400f)
                lineTo(760f, 400f)
                lineTo(760f, 680f)
                quadTo(760f, 746f, 713f, 793f)
                quadTo(666f, 840f, 600f, 840f)
                quadTo(534f, 840f, 487f, 793f)
                quadTo(440f, 746f, 440f, 680f)
                lineTo(440f, 280f)
                quadTo(440f, 247f, 416.5f, 223.5f)
                quadTo(393f, 200f, 360f, 200f)
                quadTo(327f, 200f, 303.5f, 223.5f)
                quadTo(280f, 247f, 280f, 280f)
                lineTo(280f, 560f)
                lineTo(320f, 560f)
                quadTo(337f, 560f, 348.5f, 571.5f)
                quadTo(360f, 583f, 360f, 600f)
                lineTo(360f, 760f)
                lineTo(320f, 760f)
                lineTo(320f, 800f)
                quadTo(320f, 817f, 308.5f, 828.5f)
                quadTo(297f, 840f, 280f, 840f)
                lineTo(200f, 840f)
                close()
            }
        }.build()
        return _cable!!
    }

private var _cable: ImageVector? = null
