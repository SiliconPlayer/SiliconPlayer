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
internal val AirwaveIcon: ImageVector
    get() {
        if (_airwave != null) {
            return _airwave!!
        }
        _airwave = ImageVector.Builder(
            name = "AirwaveIcon",
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
                moveTo(750.0f, 346.0f)
                quadTo(723.0f, 373.0f, 688.0f, 387.0f)
                reflectiveQuadTo(618.0f, 401.0f)
                quadTo(583.0f, 401.0f, 549.0f, 387.5f)
                reflectiveQuadTo(488.0f, 346.0f)
                lineTo(413.0f, 271.0f)
                quadTo(398.0f, 256.0f, 379.0f, 248.5f)
                reflectiveQuadTo(340.0f, 241.0f)
                quadTo(320.0f, 241.0f, 301.0f, 248.5f)
                reflectiveQuadTo(267.0f, 271.0f)
                lineTo(192.0f, 346.0f)
                lineTo(135.0f, 289.0f)
                lineTo(210.0f, 214.0f)
                quadTo(237.0f, 187.0f, 271.0f, 173.5f)
                reflectiveQuadTo(340.0f, 160.0f)
                quadTo(375.0f, 160.0f, 408.5f, 173.5f)
                reflectiveQuadTo(469.0f, 214.0f)
                lineTo(544.0f, 289.0f)
                quadTo(560.0f, 305.0f, 579.0f, 312.5f)
                reflectiveQuadTo(618.0f, 320.0f)
                quadTo(638.0f, 320.0f, 657.5f, 312.5f)
                reflectiveQuadTo(693.0f, 289.0f)
                lineTo(768.0f, 214.0f)
                lineTo(825.0f, 271.0f)
                lineTo(750.0f, 346.0f)
                close()
                moveTo(750.0f, 546.0f)
                quadTo(723.0f, 573.0f, 688.5f, 586.5f)
                reflectiveQuadTo(619.0f, 600.0f)
                quadTo(584.0f, 600.0f, 549.5f, 586.5f)
                reflectiveQuadTo(488.0f, 546.0f)
                lineTo(413.0f, 471.0f)
                quadTo(398.0f, 456.0f, 379.0f, 448.5f)
                reflectiveQuadTo(340.0f, 441.0f)
                quadTo(320.0f, 441.0f, 301.0f, 448.5f)
                reflectiveQuadTo(267.0f, 471.0f)
                lineTo(192.0f, 546.0f)
                lineTo(135.0f, 490.0f)
                lineTo(210.0f, 414.0f)
                quadTo(237.0f, 387.0f, 271.0f, 373.5f)
                reflectiveQuadTo(340.0f, 360.0f)
                quadTo(375.0f, 360.0f, 408.5f, 373.5f)
                reflectiveQuadTo(469.0f, 414.0f)
                lineTo(544.0f, 489.0f)
                quadTo(560.0f, 505.0f, 579.0f, 512.5f)
                reflectiveQuadTo(618.0f, 520.0f)
                quadTo(638.0f, 520.0f, 657.5f, 512.5f)
                reflectiveQuadTo(693.0f, 489.0f)
                lineTo(768.0f, 414.0f)
                lineTo(825.0f, 471.0f)
                lineTo(750.0f, 546.0f)
                close()
                moveTo(749.0f, 746.0f)
                quadTo(722.0f, 773.0f, 688.0f, 786.5f)
                reflectiveQuadTo(619.0f, 800.0f)
                quadTo(584.0f, 800.0f, 549.5f, 786.5f)
                reflectiveQuadTo(488.0f, 746.0f)
                lineTo(412.0f, 671.0f)
                quadTo(397.0f, 656.0f, 378.0f, 648.5f)
                reflectiveQuadTo(339.0f, 641.0f)
                quadTo(319.0f, 641.0f, 300.0f, 648.5f)
                reflectiveQuadTo(266.0f, 671.0f)
                lineTo(191.0f, 746.0f)
                lineTo(135.0f, 690.0f)
                lineTo(210.0f, 614.0f)
                quadTo(237.0f, 587.0f, 271.0f, 573.5f)
                reflectiveQuadTo(340.0f, 560.0f)
                quadTo(375.0f, 560.0f, 408.5f, 573.5f)
                reflectiveQuadTo(469.0f, 614.0f)
                lineTo(544.0f, 689.0f)
                quadTo(560.0f, 705.0f, 579.5f, 712.5f)
                reflectiveQuadTo(619.0f, 720.0f)
                quadTo(639.0f, 720.0f, 658.0f, 712.5f)
                reflectiveQuadTo(693.0f, 689.0f)
                lineTo(768.0f, 614.0f)
                lineTo(824.0f, 671.0f)
                lineTo(749.0f, 746.0f)
                close()
            }
        }.build()
        return _airwave!!
    }

private var _airwave: ImageVector? = null
