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
internal val BalanceIcon: ImageVector
    get() {
        if (_balance != null) {
            return _balance!!
        }
        _balance = ImageVector.Builder(
            name = "BalanceIcon",
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
                moveTo(80.0f, 840.0f)
                lineTo(80.0f, 760.0f)
                lineTo(440.0f, 760.0f)
                lineTo(440.0f, 313.0f)
                quadTo(414.0f, 304.0f, 395.0f, 285.0f)
                reflectiveQuadTo(367.0f, 240.0f)
                lineTo(240.0f, 240.0f)
                lineTo(360.0f, 520.0f)
                quadTo(360.0f, 570.0f, 319.0f, 605.0f)
                reflectiveQuadTo(220.0f, 640.0f)
                quadTo(162.0f, 640.0f, 121.0f, 605.0f)
                reflectiveQuadTo(80.0f, 520.0f)
                lineTo(200.0f, 240.0f)
                lineTo(120.0f, 240.0f)
                lineTo(120.0f, 160.0f)
                lineTo(367.0f, 160.0f)
                quadTo(379.0f, 125.0f, 410.0f, 102.5f)
                reflectiveQuadTo(480.0f, 80.0f)
                quadTo(519.0f, 80.0f, 550.0f, 102.5f)
                reflectiveQuadTo(593.0f, 160.0f)
                lineTo(840.0f, 160.0f)
                lineTo(840.0f, 240.0f)
                lineTo(760.0f, 240.0f)
                lineTo(880.0f, 520.0f)
                quadTo(880.0f, 570.0f, 839.0f, 605.0f)
                reflectiveQuadTo(740.0f, 640.0f)
                quadTo(682.0f, 640.0f, 641.0f, 605.0f)
                reflectiveQuadTo(600.0f, 520.0f)
                lineTo(720.0f, 240.0f)
                lineTo(593.0f, 240.0f)
                quadTo(584.0f, 266.0f, 565.0f, 285.0f)
                reflectiveQuadTo(520.0f, 313.0f)
                lineTo(520.0f, 760.0f)
                lineTo(880.0f, 760.0f)
                lineTo(880.0f, 840.0f)
                lineTo(80.0f, 840.0f)
                close()
                moveTo(665.0f, 520.0f)
                lineTo(815.0f, 520.0f)
                lineTo(740.0f, 346.0f)
                lineTo(665.0f, 520.0f)
                close()
                moveTo(145.0f, 520.0f)
                lineTo(295.0f, 520.0f)
                lineTo(220.0f, 346.0f)
                lineTo(145.0f, 520.0f)
                close()
                moveTo(480.0f, 240.0f)
                quadTo(497.0f, 240.0f, 508.5f, 228.5f)
                reflectiveQuadTo(520.0f, 200.0f)
                quadTo(520.0f, 183.0f, 508.5f, 171.5f)
                reflectiveQuadTo(480.0f, 160.0f)
                quadTo(463.0f, 160.0f, 451.5f, 171.5f)
                reflectiveQuadTo(440.0f, 200.0f)
                quadTo(440.0f, 217.0f, 451.5f, 228.5f)
                reflectiveQuadTo(480.0f, 240.0f)
                close()
            }
        }.build()
        return _balance!!
    }

private var _balance: ImageVector? = null
