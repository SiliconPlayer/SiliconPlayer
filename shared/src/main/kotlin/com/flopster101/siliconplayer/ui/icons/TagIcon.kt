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
internal val TagIcon: ImageVector
    get() {
        if (_tag != null) {
            return _tag!!
        }
        _tag = ImageVector.Builder(
            name = "TagIcon",
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
                moveTo(240.0f, 800.0f)
                lineTo(280.0f, 640.0f)
                lineTo(120.0f, 640.0f)
                lineTo(140.0f, 560.0f)
                lineTo(300.0f, 560.0f)
                lineTo(340.0f, 400.0f)
                lineTo(180.0f, 400.0f)
                lineTo(200.0f, 320.0f)
                lineTo(360.0f, 320.0f)
                lineTo(400.0f, 160.0f)
                lineTo(480.0f, 160.0f)
                lineTo(440.0f, 320.0f)
                lineTo(600.0f, 320.0f)
                lineTo(640.0f, 160.0f)
                lineTo(720.0f, 160.0f)
                lineTo(680.0f, 320.0f)
                lineTo(840.0f, 320.0f)
                lineTo(820.0f, 400.0f)
                lineTo(660.0f, 400.0f)
                lineTo(620.0f, 560.0f)
                lineTo(780.0f, 560.0f)
                lineTo(760.0f, 640.0f)
                lineTo(600.0f, 640.0f)
                lineTo(560.0f, 800.0f)
                lineTo(480.0f, 800.0f)
                lineTo(520.0f, 640.0f)
                lineTo(360.0f, 640.0f)
                lineTo(320.0f, 800.0f)
                lineTo(240.0f, 800.0f)
                close()
                moveTo(380.0f, 560.0f)
                lineTo(540.0f, 560.0f)
                lineTo(580.0f, 400.0f)
                lineTo(420.0f, 400.0f)
                lineTo(380.0f, 560.0f)
                close()
            }
        }.build()
        return _tag!!
    }

private var _tag: ImageVector? = null
