package com.flopster101.siliconplayer

import com.flopster101.siliconplayer.desktop.DesktopPaths
import java.io.File

object NativeBridge {

    const val CHANNEL_SCOPE_TEXT_STATE_STRIDE = 10
    const val CHANNEL_SCOPE_TEXT_FLAG_ACTIVE = 1 shl 0
    const val CHANNEL_SCOPE_TEXT_FLAG_AMIGA_LEFT = 1 shl 1
    const val CHANNEL_SCOPE_TEXT_FLAG_AMIGA_RIGHT = 1 shl 2

    init {
        loadNativeLibraries()
        initUadeRuntimePaths()
    }

    // Directory holding our own jars: build/libs in dev, lib/app inside a
    // packaged distributable. Probing it keeps native lookup CWD-independent.
    private fun packagedNativeDir(): File? = runCatching {
        val location = NativeBridge::class.java.protectionDomain?.codeSource?.location
            ?.toURI()?.let(::File) ?: return null
        (if (location.isFile) location.parentFile else location).takeIf { it.isDirectory }
    }.getOrNull()

    private fun initUadeRuntimePaths() {
        val userDir = System.getProperty("user.dir") ?: "."
        val packagedDir = packagedNativeDir()
        val baseCandidates = listOfNotNull(
            packagedDir?.let { File(it, "uade") },
            File("desktop/prebuilt/x86_64/share/uade"),
            File("../desktop/prebuilt/x86_64/share/uade"),
            File(userDir, "desktop/prebuilt/x86_64/share/uade"),
            File(userDir, "SiliconPlayer/desktop/prebuilt/x86_64/share/uade"),
            File("/usr/share/uade"),
            File("/usr/local/share/uade")
        )
        val coreCandidates = listOfNotNull(
            packagedDir?.let { File(it, "uade/uadecore") },
            File("desktop/prebuilt/x86_64/lib/uade/uadecore"),
            File("../desktop/prebuilt/x86_64/lib/uade/uadecore"),
            File(userDir, "desktop/prebuilt/x86_64/lib/uade/uadecore"),
            File(userDir, "SiliconPlayer/desktop/prebuilt/x86_64/lib/uade/uadecore"),
            File("/usr/lib/uade/uadecore"),
            File("/usr/local/lib/uade/uadecore")
        )
        val baseDir = baseCandidates.firstOrNull { it.exists() && it.isDirectory }
        val coreFile = coreCandidates.firstOrNull { it.exists() && it.isFile }
        if (baseDir != null && coreFile != null) {
            try {
                if (!coreFile.canExecute()) {
                    coreFile.setExecutable(true)
                }
                setUadeRuntimePaths(baseDir.absolutePath, coreFile.absolutePath)
            } catch (t: Throwable) {
                System.err.println("Failed to initialize UADE runtime paths: ${t.message}")
            }
        }
    }

    private fun loadNativeLibraries() {
        try {
            System.loadLibrary("projectM-4")
        } catch (_: Throwable) {
            val packagedDir = packagedNativeDir()
            val candidates = listOfNotNull(
                packagedDir?.let { File(it, "libprojectM-4.so") },
                File("desktop/prebuilt/x86_64/lib/libprojectM-4.so"),
                File("../desktop/prebuilt/x86_64/lib/libprojectM-4.so"),
                File("external/projectm/build_host/src/libprojectM/libprojectM-4.so"),
                File("../external/projectm/build_host/src/libprojectM/libprojectM-4.so"),
                File("desktop/build/native/projectm/src/libprojectM/libprojectM-4.so")
            )
            val found = candidates.firstOrNull { it.exists() && it.isFile }
            if (found != null) {
                try { System.load(found.absolutePath) } catch (_: Throwable) {}
            }
        }

        try {
            System.loadLibrary("siliconplayer_desktop")
            return
        } catch (e: UnsatisfiedLinkError) {
            // Try explicit candidate file locations
            val packagedDir = packagedNativeDir()
            val candidates = listOfNotNull(
                packagedDir?.let { File(it, "libsiliconplayer_desktop.so") },
                File("desktop/build/native/libsiliconplayer_desktop.so"),
                File("build/native/libsiliconplayer_desktop.so"),
                File("../desktop/build/native/libsiliconplayer_desktop.so"),
                File("libsiliconplayer_desktop.so")
            )
            val found = candidates.firstOrNull { it.exists() && it.isFile }
            if (found != null) {
                System.load(found.absolutePath)
                return
            }
            throw e
        }
    }

    @JvmStatic
    fun resolveArchiveCompanionPathForNative(basePath: String?, requestedPath: String?): String? {
        if (basePath == null || requestedPath == null) return null
        val baseFile = File(basePath)
        val parent = baseFile.parentFile ?: return null
        val direct = File(parent, requestedPath)
        if (direct.exists() && direct.isFile) return direct.absolutePath
        val byName = File(parent, File(requestedPath).name)
        if (byName.exists() && byName.isFile) return byName.absolutePath
        return null
    }

    @JvmStatic
    fun openSmbAvioHandle(requestUri: String): Long =
        SmbAvioBridge.openHandle(requestUri, DesktopPaths.cacheDir())

    @JvmStatic
    fun readSmbAvioHandle(handleId: Long, offset: Long, buffer: ByteArray, length: Int): Int {
        return SmbAvioBridge.readHandle(
            handleId = handleId,
            offset = offset,
            buffer = buffer,
            length = length
        )
    }

    @JvmStatic
    fun getSmbAvioHandleSize(handleId: Long): Long = SmbAvioBridge.getHandleSize(handleId)

    @JvmStatic
    fun closeSmbAvioHandle(handleId: Long) {
        SmbAvioBridge.closeHandle(handleId)
    }

    @JvmStatic
    fun cancelActiveSmbAvioHandles() {
        SmbAvioBridge.cancelAllHandles()
    }

    @Volatile
    private var forcedDecoderOneShot: String? = null

    fun prioritizeNextOpenWith(decoderName: String) {
        forcedDecoderOneShot = decoderName
    }

    fun consumeForcedDecoderOneShot(): String? {
        val decoder = forcedDecoderOneShot
        forcedDecoderOneShot = null
        return decoder
    }

    external fun isAudioBackendSupported(backendId: Int): Boolean
    external fun getAudioSessionId(): Int

    external fun reconfigureStream(resumePlayback: Boolean = true)
    external fun loadAudio(path: String)
    external fun loadAudioWithDecoder(path: String, decoderName: String)

    external fun startEngineNative()
    external fun stopEngineNative()
    external fun stopEngineSyncNative()
    external fun teardownOutputStream()
    external fun startEngineWithPauseResumeFadeNative()
    external fun stopEngineWithPauseResumeFadeNative()
    external fun seekToImpl(seconds: Double)
    external fun getDurationImpl(): Double
    external fun getPositionImpl(): Double
    external fun consumeNaturalEndEventImpl(): Boolean
    external fun consumeTransportErrorEventImpl(): Boolean
    external fun isEnginePlayingImpl(): Boolean
    external fun isSeekInProgressImpl(): Boolean
    external fun releaseCurrentDecoderNative()

    external fun setFastTrackSwitchStartupHint(enabled: Boolean)
    external fun getSupportedExtensions(): Array<String>
    external fun probeTrackMetadata(path: String, subtuneIndex: Int): Array<String?>?

    external fun setLooping(enabled: Boolean)
    external fun setRepeatMode(mode: Int)
    external fun getTrackTitle(): String
    external fun getTrackArtist(): String
    external fun getTrackComposer(): String
    external fun getTrackGenre(): String
    external fun getTrackAlbum(): String
    external fun getTrackYear(): String
    external fun getTrackDate(): String
    external fun getTrackCopyright(): String
    external fun getTrackComment(): String
    external fun getTrackSampleRate(): Int
    external fun hasNativeSampleRate(): Boolean
    external fun getTrackChannelCount(): Int
    external fun getTrackBitDepth(): Int
    external fun getTrackBitDepthLabel(): String
    external fun getRepeatModeCapabilities(): Int
    external fun getPlaybackCapabilities(): Int
    external fun getTimelineMode(): Int
    external fun getCurrentDecoderNameImpl(): String

    external fun setOutputShadowMuted(muted: Boolean)
    external fun pushExternalVisualizationSamples(samples: FloatArray, count: Int, vuLevel: Float)

    external fun getSubtuneCount(): Int
    external fun getCurrentSubtuneIndex(): Int
    external fun selectSubtune(index: Int): Boolean
    external fun getSubtuneTitle(index: Int): String
    external fun getSubtuneArtist(index: Int): String
    external fun getSubtuneDurationSeconds(index: Int): Double
    external fun getDecoderRenderSampleRateHz(): Int
    external fun getOutputStreamSampleRateHz(): Int
    external fun getOpenMptModuleTypeLong(): String
    external fun getOpenMptTracker(): String
    external fun getOpenMptSongMessage(): String
    external fun getOpenMptOrderCount(): Int
    external fun getOpenMptPatternCount(): Int
    external fun getOpenMptInstrumentCount(): Int
    external fun getOpenMptSampleCount(): Int
    external fun getOpenMptInstrumentNames(): String
    external fun getOpenMptSampleNames(): String
    external fun getXmpInstrumentNames(): String
    external fun getXmpSampleNames(): String
    external fun getXmpFormatName(): String
    external fun getXmpSongMessage(): String
    external fun getXmpModuleMd5(): String
    external fun getXmpMixerName(): String
    external fun getXmpChannelCount(): Int
    external fun getXmpOrderCount(): Int
    external fun getXmpPatternCount(): Int
    external fun getXmpTrackCount(): Int
    external fun getXmpInstrumentCount(): Int
    external fun getXmpSampleCount(): Int
    external fun getXmpInitialSpeed(): Int
    external fun getXmpInitialBpm(): Int
    external fun getXmpRestartPosition(): Int
    external fun getXmpCurrentOrder(): Int
    external fun getXmpCurrentPattern(): Int
    external fun getXmpCurrentRow(): Int
    external fun getXmpCurrentTick(): Int
    external fun getXmpCurrentSpeed(): Int
    external fun getXmpCurrentBpm(): Int
    external fun getXmpLoopCount(): Int
    external fun getUfmodInfo(name: String): Int
    external fun getAyflyFormatName(): String
    external fun getAyflyChipName(): String
    external fun getAyflyPlayerName(): String
    external fun getAyflyMixerName(): String
    external fun getAyflyChannelCount(): Int
    external fun getAyflyLoopPointMs(): Int
    external fun getAyflySubsongCount(): Int
    external fun getAyflyCurrentSubsong(): Int
    external fun getAyflyInterruptHz(): Int
    external fun getOpenMptChannelVuLevels(): FloatArray
    external fun getChannelScopeSamples(samplesPerChannel: Int): FloatArray
    external fun getChannelScopeTextState(maxChannels: Int): IntArray
    external fun getChannelScopeDataSerial(): Long
    external fun computeChannelScopeTriggers(
        flatScopeData: FloatArray,
        samplesPerChannel: Int,
        numChannels: Int,
        triggerModeNative: Int,
        algorithmMode: Int
    ): IntArray
    external fun resetChannelScopeTriggers()
    external fun getVgmGameName(): String
    external fun getVgmSystemName(): String
    external fun getVgmReleaseDate(): String
    external fun getVgmEncodedBy(): String
    external fun getVgmNotes(): String
    external fun getVgmFileVersion(): String
    external fun getVgmDeviceCount(): Int
    external fun getVgmUsedChipList(): String
    external fun getVgmHasLoopPoint(): Boolean
    external fun getFfmpegCodecName(): String
    external fun getFfmpegContainerName(): String
    external fun getFfmpegSampleFormatName(): String
    external fun getFfmpegChannelLayoutName(): String
    external fun getFfmpegEncoderName(): String
    external fun getGmeSystemName(): String
    external fun getGmeGameName(): String
    external fun getGmeCopyright(): String
    external fun getGmeComment(): String
    external fun getGmeDumper(): String
    external fun getGmeTrackCount(): Int
    external fun getGmeVoiceCount(): Int
    external fun getGmeHasLoopPoint(): Boolean
    external fun getGmeLoopStartMs(): Int
    external fun getGmeLoopLengthMs(): Int
    external fun getLazyUsf2GameName(): String
    external fun getLazyUsf2Copyright(): String
    external fun getLazyUsf2Year(): String
    external fun getLazyUsf2UsfBy(): String
    external fun getLazyUsf2LengthTag(): String
    external fun getLazyUsf2FadeTag(): String
    external fun getLazyUsf2EnableCompare(): Boolean
    external fun getLazyUsf2EnableFifoFull(): Boolean
    external fun getVio2sfGameName(): String
    external fun getVio2sfCopyright(): String
    external fun getVio2sfYear(): String
    external fun getVio2sfComment(): String
    external fun getVio2sfLengthTag(): String
    external fun getVio2sfFadeTag(): String
    external fun getLibupseGameName(): String
    external fun getLibupseCopyright(): String
    external fun getLibupseYear(): String
    external fun getLibupseComment(): String
    external fun getLibupseLengthTag(): String
    external fun getLibupseFadeTag(): String
    external fun getViogsfGameName(): String
    external fun getViogsfCopyright(): String
    external fun getViogsfYear(): String
    external fun getViogsfComment(): String
    external fun getViogsfLengthTag(): String
    external fun getViogsfFadeTag(): String
    external fun getNezplugppCopyright(): String
    external fun getNezplugppDetail(): String
    external fun getNezplugppFormat(): String
    external fun getNezplugppSongVoices(): String
    external fun getNezplugppSubtuneInfo(): String
    external fun getSidFormatName(): String
    external fun getSidClockName(): String
    external fun getSidSpeedName(): String
    external fun getSidCompatibilityName(): String
    external fun getSidBackendName(): String
    external fun getSidChipCount(): Int
    external fun getSidModelSummary(): String
    external fun getSidCurrentModelSummary(): String
    external fun getSidBaseAddressSummary(): String
    external fun getSidCommentSummary(): String
    external fun getSc68FormatName(): String
    external fun getSc68HardwareName(): String
    external fun getSc68PlatformName(): String
    external fun getSc68ReplayName(): String
    external fun getSc68ReplayRateHz(): Int
    external fun getSc68TrackCount(): Int
    external fun getSc68AlbumName(): String
    external fun getSc68Year(): String
    external fun getSc68Ripper(): String
    external fun getSc68Converter(): String
    external fun getSc68Timer(): String
    external fun getSc68CanAsid(): Boolean
    external fun getSc68UsesYm(): Boolean
    external fun getSc68UsesSte(): Boolean
    external fun getSc68UsesAmiga(): Boolean
    external fun getAdplugDescription(): String
    external fun getAdplugPatternCount(): Int
    external fun getAdplugCurrentPattern(): Int
    external fun getAdplugOrderCount(): Int
    external fun getAdplugCurrentOrder(): Int
    external fun getAdplugCurrentRow(): Int
    external fun getAdplugCurrentSpeed(): Int
    external fun getAdplugInstrumentCount(): Int
    external fun getAdplugInstrumentNames(): String
    external fun getHivelyFormatName(): String
    external fun getHivelyFormatVersion(): Int
    external fun getHivelyPositionCount(): Int
    external fun getHivelyRestartPosition(): Int
    external fun getHivelyTrackLengthRows(): Int
    external fun getHivelyTrackCount(): Int
    external fun getHivelyInstrumentCount(): Int
    external fun getHivelySpeedMultiplier(): Int
    external fun getHivelyCurrentPosition(): Int
    external fun getHivelyCurrentRow(): Int
    external fun getHivelyCurrentTempo(): Int
    external fun getHivelyMixGainPercent(): Int
    external fun getHivelyInstrumentNames(): String
    external fun getKlystrackFormatName(): String
    external fun getKlystrackTrackCount(): Int
    external fun getKlystrackInstrumentCount(): Int
    external fun getKlystrackSongLengthRows(): Int
    external fun getKlystrackCurrentRow(): Int
    external fun getKlystrackInstrumentNames(): String
    external fun getFurnaceInstrumentNames(): String
    external fun getDnfamitrackerInstrumentNames(): String
    external fun getDnfamitrackerSampleNames(): String
    external fun getDnfamitrackerFormatName(): String
    external fun getDnfamitrackerSystemName(): String
    external fun getDnfamitrackerExpansionChips(): String
    external fun getDnfamitrackerCurrentSongTitle(): String
    external fun getDnfamitrackerSongChannelCount(): Int
    external fun getDnfamitrackerSongCount(): Int
    external fun getDnfamitrackerFrameCount(): Int
    external fun getDnfamitrackerRowsPerPattern(): Int
    external fun getDnfamitrackerSongSpeed(): Int
    external fun getDnfamitrackerSongTempo(): Int
    external fun getDnfamitrackerCurrentFrame(): Int
    external fun getDnfamitrackerCurrentRow(): Int
    external fun getFurnaceSampleNames(): String
    external fun getFurnaceFormatName(): String
    external fun getFurnaceSongVersion(): Int
    external fun getFurnaceSystemName(): String
    external fun getFurnaceSystemNames(): String
    external fun getFurnaceSystemCount(): Int
    external fun getFurnaceSongChannelCount(): Int
    external fun getFurnaceInstrumentCount(): Int
    external fun getFurnaceWavetableCount(): Int
    external fun getFurnaceSampleCount(): Int
    external fun getFurnaceOrderCount(): Int
    external fun getFurnaceRowsPerPattern(): Int
    external fun getFurnaceCurrentOrder(): Int
    external fun getFurnaceCurrentRow(): Int
    external fun getFurnaceCurrentTick(): Int
    external fun getFurnaceCurrentSpeed(): Int
    external fun getFurnaceGrooveLength(): Int
    external fun getFurnaceCurrentHz(): Float
    external fun getUadeFormatName(): String
    external fun getUadeModuleName(): String
    external fun getUadePlayerName(): String
    external fun getUadeModuleFileName(): String
    external fun getUadePlayerFileName(): String
    external fun getUadeModuleMd5(): String
    external fun getUadeDetectionExtension(): String
    external fun getUadeDetectedFormatName(): String
    external fun getUadeDetectedFormatVersion(): String
    external fun getUadeDetectionByContent(): Boolean
    external fun getUadeDetectionIsCustom(): Boolean
    external fun getUadeSubsongMin(): Int
    external fun getUadeSubsongMax(): Int
    external fun getUadeSubsongDefault(): Int
    external fun getUadeCurrentSubsong(): Int
    external fun getUadeModuleBytes(): Long
    external fun getUadeSongBytes(): Long
    external fun getUadeSubsongBytes(): Long
    external fun getTrackBitrate(): Long
    external fun isTrackVBR(): Boolean
    external fun getAudioBackendLabel(): String
    external fun getAudioOutputRouteName(): String
    external fun getAudioOutputRouteClass(): Int
    external fun getStreamBurstFrames(): Int
    external fun setBitPerfectMode(enabled: Boolean)
    external fun setUacSettlePilotTone(enabled: Boolean)
    external fun isBitPerfectActive(): Boolean
    external fun setCoreOutputSampleRate(coreName: String, sampleRateHz: Int)
    external fun setCoreOption(coreName: String, optionName: String, optionValue: String)
    external fun getCoreCapabilities(coreName: String): Int
    external fun getCoreRepeatModeCapabilities(coreName: String): Int
    external fun getCoreTimelineMode(coreName: String): Int
    external fun getCoreOptionApplyPolicy(coreName: String, optionName: String): Int
    external fun getCoreFixedSampleRateHz(coreName: String): Int
    external fun setAudioPipelineConfig(
        backendPreference: Int,
        performanceMode: Int,
        bufferPreset: Int,
        resamplerPreference: Int,
        allowFallback: Boolean
    )
    external fun setBackgroundPlaybackMode(enabled: Boolean)
    external fun setEndFadeApplyToAllTracks(enabled: Boolean)
    external fun setEndFadeDurationMs(durationMs: Int)
    external fun setEndFadeCurve(curve: Int)
    external fun getVisualizationWaveformScope(channelIndex: Int, windowMs: Int, triggerMode: Int): FloatArray
    external fun getVisualizationBars(): FloatArray
    external fun getVisualizationVuLevels(): FloatArray
    external fun getVisualizationChannelCount(): Int
    external fun attachAudioEngineToVisualizer(visHandle: Long)
    external fun setChannelScopeVisualizerActive(active: Boolean)

    // Gain control methods
    external fun setMasterGain(gainDb: Float)
    external fun setPluginGain(gainDb: Float)
    external fun setSongGain(gainDb: Float)
    external fun setForceMono(enabled: Boolean)
    external fun setOutputLimiterEnabled(enabled: Boolean)
    external fun setLookaheadClipperMode(mode: Int)
    external fun setMultiChannelOutputMode(mode: Int)
    external fun setDspBassEnabled(enabled: Boolean)
    external fun setDspBassDepth(depth: Int)
    external fun setDspBassRange(range: Int)
    external fun setDspSurroundEnabled(enabled: Boolean)
    external fun setDspSurroundDepth(depth: Int)
    external fun setDspSurroundDelayMs(delayMs: Int)
    external fun setDspReverbEnabled(enabled: Boolean)
    external fun setDspReverbDepth(depth: Int)
    external fun setDspReverbPreset(preset: Int)
    external fun setDspBitCrushEnabled(enabled: Boolean)
    external fun setDspBitCrushBits(bits: Int)
    external fun getMasterGain(): Float
    external fun getPluginGain(): Float
    external fun getSongGain(): Float
    external fun getForceMono(): Boolean
    external fun getDspBassEnabled(): Boolean
    external fun getDspBassDepth(): Int
    external fun getDspBassRange(): Int
    external fun getDspSurroundEnabled(): Boolean
    external fun getDspSurroundDepth(): Int
    external fun getDspSurroundDelayMs(): Int
    external fun getDspReverbEnabled(): Boolean
    external fun getDspReverbDepth(): Int
    external fun getDspReverbPreset(): Int
    external fun getDspBitCrushEnabled(): Boolean
    external fun getDspBitCrushBits(): Int
    external fun setMasterChannelMute(channelIndex: Int, enabled: Boolean)
    external fun setMasterChannelSolo(channelIndex: Int, enabled: Boolean)
    external fun getMasterChannelMute(channelIndex: Int): Boolean
    external fun getMasterChannelSolo(channelIndex: Int): Boolean
    external fun getDecoderToggleChannelNames(): Array<String>
    external fun getDecoderToggleChannelAvailability(): BooleanArray
    external fun setDecoderToggleChannelMuted(channelIndex: Int, enabled: Boolean)
    external fun getDecoderToggleChannelMuted(channelIndex: Int): Boolean
    external fun clearDecoderToggleChannelMutes()

    // Decoder Registry management methods
    external fun getRegisteredDecoderNames(): Array<String>
    external fun getDecoderClaimantsForFile(path: String): Array<String>
    external fun getDecoderExtensionSupportersForFile(path: String): Array<String>
    external fun setDecoderEnabled(decoderName: String, enabled: Boolean)
    external fun isDecoderEnabled(decoderName: String): Boolean
    external fun setDecoderPriority(decoderName: String, priority: Int)
    external fun getDecoderPriority(decoderName: String): Int
    external fun getDecoderDefaultPriority(decoderName: String): Int
    external fun getDecoderSupportedExtensions(decoderName: String): Array<String>
    external fun getDecoderEnabledExtensions(decoderName: String): Array<String>
    external fun setDecoderEnabledExtensions(decoderName: String, extensions: Array<String>)
    external fun hasDecoderExtensionOverride(decoderName: String): Boolean
    external fun setUadeRuntimePaths(baseDir: String, uadeCorePath: String)

    @JvmStatic
    fun isEnginePlaying(): Boolean = isEnginePlayingImpl()

    @JvmStatic
    fun getDuration(): Double = getDurationImpl()

    @JvmStatic
    fun getPosition(): Double = getPositionImpl()

    @JvmStatic
    fun consumeNaturalEndEvent(): Boolean = consumeNaturalEndEventImpl()

    @JvmStatic
    fun consumeTransportErrorEvent(): Boolean = consumeTransportErrorEventImpl()

    @JvmStatic
    fun seekTo(seconds: Double) {
        seekToImpl(seconds)
    }

    @JvmStatic
    fun isSeekInProgress(): Boolean = isSeekInProgressImpl()

    @JvmStatic
    fun getCurrentDecoderName(): String = getCurrentDecoderNameImpl()

    @JvmStatic
    fun getPlatformDecoderCodecName(): String = ""

    @JvmStatic
    fun releaseCurrentDecoder() {
        releaseCurrentDecoderNative()
    }

    data class TrackMetadataProbeResult(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationSeconds: Double?
    )

    fun probeMetadata(path: String, subtuneIndex: Int = -1): TrackMetadataProbeResult? {
        val result = runCatching { probeTrackMetadata(path, subtuneIndex) }.getOrNull() ?: return null
        val title = result.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }
        val artist = result.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        val album = result.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
        val durationSeconds = result.getOrNull(3)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        return TrackMetadataProbeResult(title, artist, album, durationSeconds)
    }
}

fun supportsProjectM(): Boolean = true

