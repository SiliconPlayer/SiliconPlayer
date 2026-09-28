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
internal val FileGameIcon: ImageVector
    get() {
        if (_fileGame != null) {
            return _fileGame!!
        }
        _fileGame = ImageVector.Builder(
            name = "FileGameIcon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24.0f,
            viewportHeight = 24.0f
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
                moveTo(6.0f, 2.0f)
                curveTo(4.9f, 2.0f, 4.0097656f, 2.9f, 4.0097656f, 4.0f)
                lineTo(4.0f, 20.0f)
                curveTo(4.0f, 21.1f, 4.8902344f, 22.0f, 5.9902344f, 22.0f)
                lineTo(18.0f, 22.0f)
                curveTo(19.1f, 22.0f, 20.0f, 21.1f, 20.0f, 20.0f)
                lineTo(20.0f, 8.0f)
                lineTo(14.0f, 2.0f)
                lineTo(6.0f, 2.0f)
                close()
                moveTo(13.0f, 3.5f)
                lineTo(18.5f, 9.0f)
                lineTo(13.0f, 9.0f)
                lineTo(13.0f, 3.5f)
                close()
                moveTo(7.9609375f, 3.6933594f)
                lineTo(9.7265625f, 3.6933594f)
                lineTo(9.7265625f, 4.8710938f)
                lineTo(8.5488281f, 4.8710938f)
                lineTo(8.5488281f, 7.8125f)
                curveTo(8.5488281f, 8.4629383f, 8.0234853f, 8.9902344f, 7.3730469f, 8.9902344f)
                curveTo(6.7226086f, 8.9902344f, 6.1953125f, 8.4629383f, 6.1953125f, 7.8125f)
                curveTo(6.1953125f, 7.1620617f, 6.7226086f, 6.6367188f, 7.3730469f, 6.6367188f)
                curveTo(7.5878976f, 6.6367187f, 7.7872911f, 6.6987607f, 7.9609375f, 6.7988281f)
                lineTo(7.9609375f, 3.6933594f)
                close()
                moveTo(8.3964844f, 11.65625f)
                lineTo(15.603516f, 11.65625f)
                arcTo(1.3516794f, 1.3516794f, 0.0f, false, true, 16.957031f, 13.007812f)
                lineTo(16.957031f, 16.611328f)
                arcTo(1.3516794f, 1.3516794f, 0.0f, false, true, 15.603516f, 17.962891f)
                lineTo(8.3964844f, 17.962891f)
                arcTo(1.3516794f, 1.3516794f, 0.0f, false, true, 7.0429688f, 16.611328f)
                lineTo(7.0429688f, 13.007812f)
                arcTo(1.3516794f, 1.3516794f, 0.0f, false, true, 8.3964844f, 11.65625f)
                close()
                moveTo(10.197266f, 13.458984f)
                lineTo(10.144531f, 13.460938f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 9.7480469f, 13.908203f)
                lineTo(9.7480469f, 14.359375f)
                lineTo(9.296875f, 14.359375f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 8.8457031f, 14.810547f)
                lineTo(8.8496094f, 14.863281f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 9.296875f, 15.259766f)
                lineTo(9.7480469f, 15.259766f)
                lineTo(9.7480469f, 15.710938f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 10.197266f, 16.162109f)
                lineTo(10.25f, 16.158203f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 10.648438f, 15.710938f)
                lineTo(10.648438f, 15.259766f)
                lineTo(11.099609f, 15.259766f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 11.548828f, 14.810547f)
                lineTo(11.546875f, 14.757812f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 11.099609f, 14.359375f)
                lineTo(10.648438f, 14.359375f)
                lineTo(10.648438f, 13.908203f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 10.197266f, 13.458984f)
                close()
                moveTo(13.351562f, 13.908203f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 12.900391f, 14.359375f)
                lineTo(12.900391f, 14.363281f)
                arcTo(0.45117188f, 0.45117188f, 0.0f, false, false, 13.802734f, 14.363281f)
                lineTo(13.802734f, 14.359375f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 13.351562f, 13.908203f)
                close()
                moveTo(14.703125f, 14.810547f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 14.251953f, 15.259766f)
                lineTo(14.251953f, 15.265625f)
                arcTo(0.451172f, 0.451172f, 0.0f, false, false, 15.154297f, 15.265625f)
                lineTo(15.154297f, 15.259766f)
                arcTo(0.45055979f, 0.45055979f, 0.0f, false, false, 14.703125f, 14.810547f)
                close()
            }
        }.build()
        return _fileGame!!
    }

private var _fileGame: ImageVector? = null
