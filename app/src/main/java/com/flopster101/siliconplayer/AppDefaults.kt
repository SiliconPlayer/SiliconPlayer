package com.flopster101.siliconplayer

object AppDefaults {
    object Home {
        const val pressBackTwiceToExit = false
    }

    object Browser {
        val nameSortMode = BrowserNameSortMode.Natural
        const val showParentDirectoryEntry = true
        const val showFileIconChipBackground = true
        const val showLocalThumbnailPreviews = true
        const val showUnsupportedFiles = false
        const val showPreviewFiles = true
        const val showHiddenFilesAndFolders = false
    }

    object Player {
        val filenameDisplayMode = FilenameDisplayMode.TrackerOnly
        const val keepScreenOn = false
        const val fadePauseResume = true
        const val preloadNextCachedRemoteTrack = true
        const val artworkCornerRadiusDp = 16
        const val showAudioOutputRouteChip = true
        const val canvasTapToSeekSeconds = 10
        const val endFadeApplyToAllTracks = false
        const val endFadeDurationMs = 10_000
        val endFadeCurve = EndFadeCurve.Linear
    }

    object OutputPipeline {
        const val bitPerfectUsbAudio = false
        const val platformDolbyDecoder = true
        val directUacVolumeMode = com.flopster101.siliconplayer.usb.DirectUacVolumeMode.System
        const val directUacManualVolume = 1.0f
        val multiChannelOutputMode = MultiChannelOutputMode.FfmpegOnly
    }

    object AudioProcessing {
        const val outputLimiterEnabled = false
        val lookaheadClipperMode = LookaheadClipperMode.Soft

        object Dsp {
            const val bassEnabled = false
            const val bassDepth = 2
            const val bassRange = 2
            const val surroundEnabled = false
            const val surroundDepth = 8
            const val surroundDelayMs = 20
            const val reverbEnabled = false
            const val reverbDepth = 8
            const val reverbPreset = 0
            const val bitCrushEnabled = false
            const val bitCrushBits = 16
        }
    }

    object Visualization {
        const val keepScreenOn = false
        val performanceMode = VisualizationPerformanceMode.Auto
        const val showDebugInfo = false
        val fullscreenMode = VisualizationFullscreenMode.Complete

        object Bars {
            const val count = 40
            const val smoothingPercent = 60
            const val roundnessDp = 6
            const val frequencyGridEnabled = false
            const val contrastBackdropEnabled = true
            val fpsMode = VisualizationOscFpsMode.Fps60
            const val overlayArtwork = true
            const val useThemeColor = true
            val renderBackend = VisualizationRenderBackend.OpenGlTexture
            const val customColorArgb = 0xFF6BD8FF.toInt()
            val colorModeNoArtwork = VisualizationOscColorMode.Monet
            val colorModeWithArtwork = VisualizationOscColorMode.Artwork
            val countRange = 8..96
            val smoothingRange = 0..95
            val roundnessRange = 0..24
        }

        object Oscilloscope {
            const val stereo = true
            const val windowMs = 30
            const val contrastBackdropEnabled = true
            val triggerMode = VisualizationOscTriggerMode.Rising
            val fpsMode = VisualizationOscFpsMode.Default
            val renderBackend = VisualizationRenderBackend.OpenGlTexture
            const val lineWidthDp = 3
            const val gridWidthDp = 2
            const val verticalGridEnabled = false
            const val centerLineEnabled = false
            val lineColorModeNoArtwork = VisualizationOscColorMode.Monet
            val gridColorModeNoArtwork = VisualizationOscColorMode.Monet
            val lineColorModeWithArtwork = VisualizationOscColorMode.Artwork
            val gridColorModeWithArtwork = VisualizationOscColorMode.Artwork
            const val customLineColorArgb = 0xFF6BD8FF.toInt()
            const val customGridColorArgb = 0x66FFFFFF
            val windowRangeMs = 5..200
            val lineWidthRangeDp = 1..12
            val gridWidthRangeDp = 1..8
        }

        object Vu {
            val anchor = VisualizationVuAnchor.Bottom
            const val useThemeColor = true
            const val smoothingPercent = 40
            const val contrastBackdropEnabled = true
            val fpsMode = VisualizationOscFpsMode.Fps60
            val renderBackend = VisualizationRenderBackend.OpenGlTexture
            val colorModeNoArtwork = VisualizationOscColorMode.Monet
            val colorModeWithArtwork = VisualizationOscColorMode.Artwork
            const val customColorArgb = 0xFF6BD8FF.toInt()
            val smoothingRange = 0..95
        }

        object ChannelScope {
            const val windowMs = 30
            val renderBackend = VisualizationRenderBackend.OpenGlSurface
            const val dcRemovalEnabled = true
            const val gainPercent = 240
            const val contrastBackdropEnabled = true
            val triggerMode = VisualizationOscTriggerMode.Rising
            val triggerAlgorithm = VisualizationChannelScopeTriggerAlgorithm.Fast
            val waveRenderMode = VisualizationChannelScopeWaveRenderMode.Off
            val trackTransition = VisualizationChannelScopeTrackTransition.Crossfade
            val fpsMode = VisualizationOscFpsMode.Default
            const val lineWidthDp = 3
            const val gridWidthDp = 2
            const val verticalGridEnabled = false
            const val centerLineEnabled = false
            const val showArtworkBackground = true
            val backgroundMode = VisualizationChannelScopeBackgroundMode.AutoDarkAccent
            const val customBackgroundColorArgb = 0xFF101418.toInt()
            val layout = VisualizationChannelScopeLayout.ColumnFirst
            val lineColorModeNoArtwork = VisualizationOscColorMode.Monet
            val gridColorModeNoArtwork = VisualizationOscColorMode.Monet
            val lineColorModeWithArtwork = VisualizationOscColorMode.Artwork
            val gridColorModeWithArtwork = VisualizationOscColorMode.Artwork
            const val customLineColorArgb = 0xFF6BD8FF.toInt()
            const val customGridColorArgb = 0x66FFFFFF
            const val textEnabled = true
            val textAnchor = VisualizationChannelScopeTextAnchor.TopLeft
            const val textPaddingDp = 6
            const val textSizeSp = 8
            const val textHideWhenOverflow = true
            const val textShadowEnabled = true
            val textFont = VisualizationChannelScopeTextFont.RetroCuteMono
            val textColorMode = VisualizationChannelScopeTextColorMode.OpenMptInspired
            const val customTextColorArgb = 0xFFFFFFFF.toInt()
            val textNoteFormat = VisualizationNoteNameFormat.American
            const val textShowChannel = true
            const val textShowNote = true
            const val textShowVolume = true
            const val textShowEffect = true
            const val textShowInstrument = true
            const val textShowSample = true
            const val textVuEnabled = false
            val textVuAnchor = VisualizationVuAnchor.Bottom
            val textVuColorMode = VisualizationChannelScopeTextColorMode.OpenMptInspired
            const val textVuCustomColorArgb = 0xFFFFFFFF.toInt()

            val windowRangeMs = 5..200
            val gainRangePercent = 25..1000
            val lineWidthRangeDp = 1..12
            val gridWidthRangeDp = 1..8
            val textPaddingRangeDp = 0..24
            val textSizeRangeSp = 6..22
        }

        object ProjectM {
            const val presetDurationSeconds = 25.0
            const val hardCutEnabled = true
            const val hardCutSensitivity = 1.0f
            const val rotationRandom = true
            const val meshSize = 48
            const val aspectCorrection = true
            val renderBackend = VisualizationRenderBackend.OpenGlTexture
            val fpsMode = VisualizationOscFpsMode.Default
            val renderResolution = VisualizationProjectMResolutionMode.P720

            fun defaultMeshSize(context: android.content.Context): Int {
                val isWatch = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WATCH)
                return if (isWatch || CpuHardwareDetector.info.isLegacyOrConstrained) 32 else meshSize
            }
        }

        data class StarfieldPresetTune(
            val starCount: Int,
            val speedCenti: Int,
            val fovCenti: Int,
            val nearMilli: Int,
            val starColorArgb: Int,
            val baseSizeDeci: Int,
            val sizeGrowthCenti: Int,
            val farDimPercent: Int,
            val softnessPercent: Int,
            val beatGlowPercent: Int,
            val glowSizeDeci: Int,
            val trailPercent: Int,
            val streaksEnabled: Boolean,
            val streakLengthCenti: Int,
            val centerXCenti: Int,
            val centerYCenti: Int,
            val autoDriftEnabled: Boolean,
            val reactSpeedCenti: Int,
            val flashPercent: Int,
            val contrastBackdropEnabled: Boolean,
            val squarePixelsEnabled: Boolean
        )

        object Starfield {
            // SurfaceView keeps the vis out of the app window's display list;
            // the cost is one extra composition layer.
            val renderBackend = VisualizationRenderBackend.OpenGlSurface
            val starCountRange = 10..2000
            val speedRangeCenti = 2..150
            val fovRangeCenti = 40..250
            val nearRangeMilli = 10..300
            val baseSizeRangeDeci = 5..100
            val sizeGrowthRangeCenti = 0..400
            val percentRange = 0..100
            val glowSizeRangeDeci = 10..60
            val trailRangePercent = 0..98
            val streakLengthRangeCenti = 10..300
            val centerRangeCenti = -50..50
            val reactSpeedRangeCenti = 0..500
            const val beatFollowEnabled = true
            const val monochromeBackdropEnabled = true

            val classic = StarfieldPresetTune(
                starCount = 70, speedCenti = 30, fovCenti = 100, nearMilli = 60,
                starColorArgb = 0xFFFFFFFF.toInt(), baseSizeDeci = 45, sizeGrowthCenti = 120,
                farDimPercent = 60, softnessPercent = 25, beatGlowPercent = 25, glowSizeDeci = 25,
                trailPercent = 55, streaksEnabled = false, streakLengthCenti = 100,
                centerXCenti = 0, centerYCenti = 0, autoDriftEnabled = false,
                reactSpeedCenti = 150, flashPercent = 50, contrastBackdropEnabled = true,
                squarePixelsEnabled = true
            )
            val warp = StarfieldPresetTune(
                starCount = 50, speedCenti = 50, fovCenti = 130, nearMilli = 30,
                starColorArgb = 0xFFFFFFFF.toInt(), baseSizeDeci = 70, sizeGrowthCenti = 220,
                farDimPercent = 45, softnessPercent = 35, beatGlowPercent = 20, glowSizeDeci = 30,
                trailPercent = 75, streaksEnabled = true, streakLengthCenti = 100,
                centerXCenti = 0, centerYCenti = 0, autoDriftEnabled = false,
                reactSpeedCenti = 140, flashPercent = 20, contrastBackdropEnabled = true,
                squarePixelsEnabled = false
            )
            val snow = StarfieldPresetTune(
                starCount = 120, speedCenti = 15, fovCenti = 70, nearMilli = 100,
                starColorArgb = 0xFFCFE4FF.toInt(), baseSizeDeci = 80, sizeGrowthCenti = 40,
                farDimPercent = 30, softnessPercent = 80, beatGlowPercent = 50, glowSizeDeci = 40,
                trailPercent = 70, streaksEnabled = false, streakLengthCenti = 100,
                centerXCenti = 0, centerYCenti = 0, autoDriftEnabled = true,
                reactSpeedCenti = 80, flashPercent = 20, contrastBackdropEnabled = true,
                squarePixelsEnabled = false
            )
            val beatRider = StarfieldPresetTune(
                starCount = 120, speedCenti = 20, fovCenti = 110, nearMilli = 60,
                starColorArgb = 0xFFFFFFFF.toInt(), baseSizeDeci = 65, sizeGrowthCenti = 160,
                farDimPercent = 55, softnessPercent = 35, beatGlowPercent = 100, glowSizeDeci = 40,
                trailPercent = 50, streaksEnabled = false, streakLengthCenti = 100,
                centerXCenti = 0, centerYCenti = 0, autoDriftEnabled = false,
                reactSpeedCenti = 300, flashPercent = 85, contrastBackdropEnabled = true,
                squarePixelsEnabled = false
            )
        }
    }
}
