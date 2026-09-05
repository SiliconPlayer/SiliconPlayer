package com.flopster101.siliconplayer

import android.content.Context
import com.flopster101.siliconplayer.data.resolveArchiveMountedCompanionPath
import com.flopster101.siliconplayer.playback.PlatformDolbyPlayer

object NativeBridge {
    const val CHANNEL_SCOPE_TEXT_STATE_STRIDE = 10
    const val CHANNEL_SCOPE_TEXT_FLAG_ACTIVE = 1 shl 0
    const val CHANNEL_SCOPE_TEXT_FLAG_AMIGA_LEFT = 1 shl 1
    const val CHANNEL_SCOPE_TEXT_FLAG_AMIGA_RIGHT = 1 shl 2

    init {
        System.loadLibrary("siliconplayer")
    }

    @Volatile
    private var appContext: Context? = null

    fun installContext(context: Context) {
        appContext = context.applicationContext
        val runtimeBaseDir = UadeRuntimeSupport.ensureInstalled(appContext!!)
        val runtimeCorePath = UadeRuntimeSupport.resolveUadeCoreExecutablePath(appContext!!)
        setUadeRuntimePaths(runtimeBaseDir ?: "", runtimeCorePath ?: "")
    }

    internal fun requireAppContext(): Context {
        return checkNotNull(appContext) { "NativeBridge context is not installed" }
    }

    @JvmStatic
    fun resolveArchiveCompanionPathForNative(basePath: String?, requestedPath: String?): String? {
        if (appContext == null) return null
        return resolveArchiveMountedCompanionPath(
            basePath = basePath,
            requestedPath = requestedPath
        )
    }

    @JvmStatic
    fun openSmbAvioHandle(requestUri: String): Long = SmbAvioBridge.openHandle(requestUri)

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

    external fun isAudioBackendSupported(backendId: Int): Boolean
    external fun getAudioSessionId(): Int

    external fun reconfigureStream(resumePlayback: Boolean = true)
    external fun loadAudio(path: String)

    // Path of the track currently loaded in the native decoder; used to
    // re-arm the platform core when transport starts without a fresh load.
    private var lastLoadedPath: String? = null

    fun replaceCurrentAudio(path: String) {
        cancelActiveSmbAvioHandles()
        lastLoadedPath = path
        PlatformDolbyPlayer.onNativeTrackUnloaded()
        loadAudio(path)
        // Probe after the decoder opens the file so the FFmpeg codec name
        // reflects the NEW track.
        PlatformDolbyPlayer.onNativeTrackLoaded(path)
    }

    // Shadow-render state: true while the muted engine runs alongside the
    // platform player to feed the visualization pipeline.
    @Volatile
    private var shadowRenderActive = false
    private var shadowAlignAttempts = 0
    private var lastShadowAlignMs = 0L

    /** Called by PlatformDolbyPlayer when the platform core goes away. */
    @JvmStatic
    fun onPlatformCoreInactive() {
        shadowRenderActive = false
    }

    /**
     * The shadow engine starts from its own decoder position (restored
     * checkpoint, previous stop point) while NuPlayer spins up, so the vis
     * can run ahead of the audible output. Re-align a BOUNDED number of
     * times shortly after start; continuous correction would seek-loop and
     * blank the visualizers (each seek stalls the render worker and clears
     * the scope state).
     */
    private fun alignShadowToPlatform(platformPos: Double) {
        if (!shadowRenderActive || shadowAlignAttempts >= 3) return
        if (isSeekInProgressImpl()) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastShadowAlignMs < 3000) return
        lastShadowAlignMs = now
        val enginePos = getPositionImpl()
        if (kotlin.math.abs(enginePos - platformPos) > 0.5) {
            shadowAlignAttempts++
            seekToImpl(platformPos)
        }
    }

    @JvmStatic
    fun startEngine() {
        if (PlatformDolbyPlayer.isActive()) {
            if (PlatformDolbyPlayer.isParallelVisEnabled()) {
                // Shadow render: the engine keeps running (muted) for visualizers.
                startEngineNative()
                shadowRenderActive = true
                shadowAlignAttempts = 0
                lastShadowAlignMs = 0L
            }
            PlatformDolbyPlayer.redirectPlay()
            return
        }
        if (PlatformDolbyPlayer.redirectPlay()) return
        PlatformDolbyPlayer.activateIfEligibleAndPlay(lastLoadedPath)
        if (PlatformDolbyPlayer.redirectPlay()) return
        startEngineNative()
    }

    @JvmStatic
    fun startEngineWithPauseResumeFade() {
        if (PlatformDolbyPlayer.isActive()) {
            if (PlatformDolbyPlayer.isParallelVisEnabled()) {
                startEngineNative()
                shadowRenderActive = true
                shadowAlignAttempts = 0
                lastShadowAlignMs = 0L
            }
            PlatformDolbyPlayer.redirectPlay()
            return
        }
        if (PlatformDolbyPlayer.redirectPlay()) return
        PlatformDolbyPlayer.activateIfEligibleAndPlay(lastLoadedPath)
        if (PlatformDolbyPlayer.redirectPlay()) return
        startEngineWithPauseResumeFadeNative()
    }

    @JvmStatic
    fun stopEngine() {
        if (PlatformDolbyPlayer.isActive()) {
            // Freeze the shadow engine so visualizers do not free-wheel ahead.
            if (shadowRenderActive) {
                stopEngineNative()
                shadowRenderActive = false
            }
            PlatformDolbyPlayer.redirectPause()
            return
        }
        if (PlatformDolbyPlayer.redirectPause()) return
        stopEngineNative()
    }

    @JvmStatic
    fun stopEngineWithPauseResumeFade() {
        if (PlatformDolbyPlayer.isActive()) {
            if (shadowRenderActive) {
                stopEngineNative()
                shadowRenderActive = false
            }
            PlatformDolbyPlayer.redirectPause()
            return
        }
        if (PlatformDolbyPlayer.redirectPause()) return
        stopEngineWithPauseResumeFadeNative()
    }

    @JvmStatic
    fun releaseCurrentDecoder() {
        shadowRenderActive = false
        PlatformDolbyPlayer.onNativeTrackUnloaded()
        releaseCurrentDecoderNative()
    }

    // Public (not internal): internal externals get Kotlin module-name
    // mangling in JNI ("$app" suffix) and would not bind to their C symbols.
    external fun startEngineNative()
    external fun seekToImpl(seconds: Double)

    @JvmStatic
    fun isEnginePlaying(): Boolean =
        if (PlatformDolbyPlayer.isActive()) PlatformDolbyPlayer.isPlaying() else isEnginePlayingImpl()

    @JvmStatic
    fun getDuration(): Double =
        if (PlatformDolbyPlayer.isActive()) {
            PlatformDolbyPlayer.durationSeconds().takeIf { it > 0.0 } ?: getDurationImpl()
        } else getDurationImpl()

    @JvmStatic
    fun getPosition(): Double =
        if (PlatformDolbyPlayer.isActive()) {
            val platformPos = PlatformDolbyPlayer.positionSeconds()
            alignShadowToPlatform(platformPos)
            platformPos
        } else getPositionImpl()

    @JvmStatic
    fun consumeNaturalEndEvent(): Boolean =
        if (PlatformDolbyPlayer.isActive()) PlatformDolbyPlayer.consumeNaturalEnd()
        else consumeNaturalEndEventImpl()

    @JvmStatic
    fun seekTo(seconds: Double) {
        if (PlatformDolbyPlayer.isActive()) {
            if (shadowRenderActive) {
                // Keep the shadow engine aligned with the platform player.
                seekToImpl(seconds)
            }
            PlatformDolbyPlayer.seekTo(seconds)
            return
        }
        seekToImpl(seconds)
    }

    @JvmStatic
    fun isSeekInProgress(): Boolean =
        if (PlatformDolbyPlayer.isActive()) false else isSeekInProgressImpl()

    external fun startEngineWithPauseResumeFadeNative()
    external fun stopEngineNative()
    external fun stopEngineWithPauseResumeFadeNative()
    external fun getDurationImpl(): Double
    external fun getPositionImpl(): Double
    external fun consumeNaturalEndEventImpl(): Boolean
    external fun isEnginePlayingImpl(): Boolean
    external fun isSeekInProgressImpl(): Boolean
    external fun releaseCurrentDecoderNative()

    external fun setFastTrackSwitchStartupHint(enabled: Boolean)
    external fun getSupportedExtensions(): Array<String>
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
    @JvmStatic
    fun getCurrentDecoderName(): String =
        if (PlatformDolbyPlayer.isActive()) DecoderNames.PLATFORM_DOLBY
        else getCurrentDecoderNameImpl()

    /** Name of the platform codec actually allocated by the Dolby core, if any. */
    @JvmStatic
    fun getPlatformDecoderCodecName(): String = PlatformDolbyPlayer.codecName()

    @JvmStatic
    external fun setOutputShadowMuted(muted: Boolean)

    @JvmStatic
    external fun pushExternalVisualizationSamples(samples: FloatArray, count: Int, vuLevel: Float)

    external fun getCurrentDecoderNameImpl(): String

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
    external fun getOpenMptChannelVuLevels(): FloatArray
    external fun getChannelScopeSamples(samplesPerChannel: Int): FloatArray
    external fun getChannelScopeTextState(maxChannels: Int): IntArray
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
    external fun getStreamBurstFrames(): Int
    external fun setBitPerfectMode(enabled: Boolean)
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
    external fun setDecoderEnabled(decoderName: String, enabled: Boolean)
    external fun isDecoderEnabled(decoderName: String): Boolean
    external fun setDecoderPriority(decoderName: String, priority: Int)
    external fun getDecoderPriority(decoderName: String): Int
    external fun getDecoderDefaultPriority(decoderName: String): Int
    external fun getDecoderSupportedExtensions(decoderName: String): Array<String>
    external fun getDecoderEnabledExtensions(decoderName: String): Array<String>
    external fun setDecoderEnabledExtensions(decoderName: String, extensions: Array<String>)
    external fun setUadeRuntimePaths(baseDir: String, uadeCorePath: String)
}
