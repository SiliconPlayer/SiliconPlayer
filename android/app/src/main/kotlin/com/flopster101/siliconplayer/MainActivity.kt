package com.flopster101.siliconplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.content.pm.PackageManager
import android.provider.Settings
import android.net.Uri
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FlutterActivity() {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeBridge.installContext(applicationContext)
        applyRemoteSourceCachePolicyOnLaunch(this, cacheDir)
        applyArchiveMountCachePolicyOnLaunch(this, cacheDir)
    }

    override fun onDestroy() {
        mainScope.cancel()
        super.onDestroy()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.flopster101.siliconplayer/playback")
            .setMethodCallHandler { call, result ->
                val context: Context = applicationContext
                
                mainScope.launch(Dispatchers.IO) {
                    try {
                        when (call.method) {
                            "play" -> {
                                val path = call.argument<String>("path")
                                if (path != null) {
                                    NativeBridge.replaceCurrentAudio(path)
                                    NativeBridge.startEngine()
                                    
                                    val title = NativeBridge.getTrackTitle().ifBlank { java.io.File(path).name }
                                    val artist = NativeBridge.getTrackArtist().ifBlank { "Unknown Artist" }
                                    val duration = NativeBridge.getDuration()
                                    val position = NativeBridge.getPosition()
                                    val repeatCaps = NativeBridge.getRepeatModeCapabilities()
                                    val playbackCaps = NativeBridge.getPlaybackCapabilities()
                                                                   val decoder = NativeBridge.getCurrentDecoderName()
                                    val decoderDescription = readCurrentFormatName(decoder) ?: decoder
                                    val subtuneCount = NativeBridge.getSubtuneCount()
                                    val currentSubtuneIndex = NativeBridge.getCurrentSubtuneIndex()
                                    val sampleRate = NativeBridge.getTrackSampleRate()
                                    val channels = NativeBridge.getTrackChannelCount()
                                    val bitDepth = NativeBridge.getTrackBitDepth()
                                    val bitDepthLabel = NativeBridge.getTrackBitDepthLabel()
                                    val backend = NativeBridge.getAudioBackendLabel()

                                    withContext(Dispatchers.Main) {
                                        PlaybackService.syncFromUi(
                                            context = context,
                                            path = path,
                                            requestUrl = null,
                                            title = title,
                                            artist = artist,
                                            durationSeconds = duration,
                                            positionSeconds = position,
                                            isPlaying = true,
                                            preferredRepeatMode = RepeatMode.None,
                                            activeRepeatMode = RepeatMode.None,
                                            repeatModeCapabilitiesFlags = repeatCaps,
                                            playbackCapabilitiesFlags = playbackCaps
                                        )
                                        
                                        result.success(mapOf(
                                            "title" to title,
                                            "artist" to artist,
                                            "duration" to duration,
                                            "position" to position,
                                            "decoder" to decoder,
                                            "decoderDescription" to decoderDescription,
                                            "subtuneCount" to subtuneCount,
                                            "currentSubtuneIndex" to currentSubtuneIndex,
                                            "sampleRate" to sampleRate,
                                            "channels" to channels,
                                            "bitDepth" to bitDepth,
                                            "bitDepthLabel" to bitDepthLabel,
                                            "backend" to backend
                                        ))
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        result.error("INVALID_ARGUMENT", "Path is null", null)
                                    }
                                }
                            }
                            "pause" -> {
                                val intent = Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_PAUSE
                                }
                                context.startService(intent)
                                withContext(Dispatchers.Main) {
                                    result.success(null)
                                }
                            }
                            "stop" -> {
                                val intent = Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_STOP_CLEAR
                                }
                                context.startService(intent)
                                withContext(Dispatchers.Main) {
                                    result.success(null)
                                }
                            }
                            "next" -> {
                                val intent = Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_NEXT_TRACK
                                }
                                context.startService(intent)
                                withContext(Dispatchers.Main) {
                                    result.success(null)
                                }
                            }
                            "prev" -> {
                                val intent = Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_PREVIOUS_TRACK
                                }
                                context.startService(intent)
                                withContext(Dispatchers.Main) {
                                    result.success(null)
                                }
                            }
                            "getMetadata" -> {
                                val decoder = NativeBridge.getCurrentDecoderName()
                                val decoderDescription = readCurrentFormatName(decoder) ?: decoder
                                val metadata = mapOf(
                                    "title" to NativeBridge.getTrackTitle(),
                                    "artist" to NativeBridge.getTrackArtist(),
                                    "duration" to NativeBridge.getDuration(),
                                    "position" to NativeBridge.getPosition(),
                                    "isPlaying" to NativeBridge.isEnginePlaying(),
                                    "decoder" to decoder,
                                    "decoderDescription" to decoderDescription,
                                    "subtuneCount" to NativeBridge.getSubtuneCount(),
                                    "currentSubtuneIndex" to NativeBridge.getCurrentSubtuneIndex()
                                )
                                withContext(Dispatchers.Main) {
                                    result.success(metadata)
                                }
                            }
                            "getTrackMetadata" -> {
                                val decoder = NativeBridge.getCurrentDecoderName()
                                val decoderDescription = readCurrentFormatName(decoder) ?: decoder
                                val metadata = mapOf(
                                    "title" to NativeBridge.getTrackTitle(),
                                    "artist" to NativeBridge.getTrackArtist(),
                                    "composer" to NativeBridge.getTrackComposer(),
                                    "genre" to NativeBridge.getTrackGenre(),
                                    "album" to NativeBridge.getTrackAlbum(),
                                    "year" to NativeBridge.getTrackYear(),
                                    "sampleRate" to NativeBridge.getTrackSampleRate(),
                                    "channels" to NativeBridge.getTrackChannelCount(),
                                    "bitDepth" to NativeBridge.getTrackBitDepth(),
                                    "bitDepthLabel" to NativeBridge.getTrackBitDepthLabel(),
                                    "decoder" to decoder,
                                    "decoderDescription" to decoderDescription,
                                    "backend" to NativeBridge.getAudioBackendLabel()
                                )
                                withContext(Dispatchers.Main) {
                                    result.success(metadata)
                                }
                            }
                            "seek" -> {
                                val seconds = call.argument<Double>("seconds")
                                if (seconds != null) {
                                    NativeBridge.seekTo(seconds)
                                    withContext(Dispatchers.Main) {
                                        result.success(null)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        result.error("INVALID_ARGUMENT", "Seconds is null", null)
                                    }
                                }
                            }
                            "selectSubtune" -> {
                                val index = call.argument<Int>("index")
                                if (index != null) {
                                    val success = NativeBridge.selectSubtune(index)
                                    withContext(Dispatchers.Main) {
                                        result.success(success)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        result.error("INVALID_ARGUMENT", "Index is null", null)
                                    }
                                }
                            }
                            "getVisualization" -> {
                                val bars = NativeBridge.getVisualizationBars()
                                val vu = NativeBridge.getVisualizationVuLevels()
                                withContext(Dispatchers.Main) {
                                    result.success(mapOf(
                                        "bars" to bars,
                                        "vu" to vu
                                    ))
                                }
                            }
                            "setMasterGain" -> {
                                val gain = call.argument<Double>("gain")
                                if (gain != null) {
                                    NativeBridge.setMasterGain(gain.toFloat())
                                    withContext(Dispatchers.Main) {
                                        result.success(null)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        result.error("INVALID_ARGUMENT", "Gain is null", null)
                                    }
                                }
                            }
                            "getMasterGain" -> {
                                val gain = NativeBridge.getMasterGain().toDouble()
                                withContext(Dispatchers.Main) {
                                    result.success(gain)
                                }
                            }
                            "getSupportedExtensions" -> {
                                val extensions = NativeBridge.getSupportedExtensions()
                                withContext(Dispatchers.Main) {
                                    result.success(extensions.toList())
                                }
                            }
                            "checkStoragePermission" -> {
                                val granted = checkStoragePermission(context)
                                withContext(Dispatchers.Main) {
                                    result.success(granted)
                                }
                            }
                            "requestStoragePermission" -> {
                                requestStoragePermission(this@MainActivity)
                                withContext(Dispatchers.Main) {
                                    result.success(null)
                                }
                            }
                            "checkNotificationPermission" -> {
                                val hasNotification = if (Build.VERSION.SDK_INT >= 33) {
                                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                } else {
                                    true
                                }
                                withContext(Dispatchers.Main) {
                                    result.success(hasNotification)
                                }
                            }
                            "requestNotificationPermission" -> {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    this@MainActivity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                                }
                                withContext(Dispatchers.Main) {
                                    result.success(null)
                                }
                            }
                            else -> {
                                withContext(Dispatchers.Main) {
                                    result.notImplemented()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            result.error("PLAYBACK_ERROR", e.message, null)
                        }
                    }
                }
            }
    }

    private fun checkStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager() || hasRuntimeStorageReadPermission(context)
        } else {
            hasRuntimeStorageReadPermission(context)
        }
    }

    private fun hasRuntimeStorageReadPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission(activity: FlutterActivity) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivity(intent)
            }
        } else {
            activity.requestPermissions(
                arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                1001
            )
        }
    }
}
