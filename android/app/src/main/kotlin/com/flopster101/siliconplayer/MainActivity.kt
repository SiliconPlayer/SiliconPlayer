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

class MainActivity : FlutterActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeBridge.installContext(applicationContext)
        applyRemoteSourceCachePolicyOnLaunch(this, cacheDir)
        applyArchiveMountCachePolicyOnLaunch(this, cacheDir)
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "com.flopster101.siliconplayer/playback")
            .setMethodCallHandler { call, result ->
                val context: Context = applicationContext
                when (call.method) {
                    "play" -> {
                        val path = call.argument<String>("path")
                        if (path != null) {
                            val intent = Intent(context, PlaybackService::class.java).apply {
                                action = PlaybackService.ACTION_PLAY
                                putExtra("extra_path", path)
                            }
                            context.startService(intent)
                            result.success(null)
                        } else {
                            result.error("INVALID_ARGUMENT", "Path is null", null)
                        }
                    }
                    "pause" -> {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PAUSE
                        }
                        context.startService(intent)
                        result.success(null)
                    }
                    "stop" -> {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_STOP_CLEAR
                        }
                        context.startService(intent)
                        result.success(null)
                    }
                    "next" -> {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_NEXT_TRACK
                        }
                        context.startService(intent)
                        result.success(null)
                    }
                    "prev" -> {
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_PREVIOUS_TRACK
                        }
                        context.startService(intent)
                        result.success(null)
                    }
                    "getMetadata" -> {
                        val metadata = mapOf(
                            "title" to NativeBridge.getTrackTitle(),
                            "artist" to NativeBridge.getTrackArtist(),
                            "duration" to NativeBridge.getDuration(),
                            "position" to NativeBridge.getPosition(),
                            "isPlaying" to NativeBridge.isEnginePlaying(),
                            "decoder" to NativeBridge.getCurrentDecoderName(),
                            "subtuneCount" to NativeBridge.getSubtuneCount(),
                            "currentSubtuneIndex" to NativeBridge.getCurrentSubtuneIndex()
                        )
                        result.success(metadata)
                    }
                    "getTrackMetadata" -> {
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
                            "decoder" to NativeBridge.getCurrentDecoderName(),
                            "backend" to NativeBridge.getAudioBackendLabel()
                        )
                        result.success(metadata)
                    }
                    "seek" -> {
                        val seconds = call.argument<Double>("seconds")
                        if (seconds != null) {
                            NativeBridge.seekTo(seconds)
                            result.success(null)
                        } else {
                            result.error("INVALID_ARGUMENT", "Seconds is null", null)
                        }
                    }
                    "selectSubtune" -> {
                        val index = call.argument<Int>("index")
                        if (index != null) {
                            val success = NativeBridge.selectSubtune(index)
                            result.success(success)
                        } else {
                            result.error("INVALID_ARGUMENT", "Index is null", null)
                        }
                    }
                    "getVisualization" -> {
                        val bars = NativeBridge.getVisualizationBars()
                        val vu = NativeBridge.getVisualizationVuLevels()
                        result.success(mapOf(
                            "bars" to bars,
                            "vu" to vu
                        ))
                    }
                    "setMasterGain" -> {
                        val gain = call.argument<Double>("gain")
                        if (gain != null) {
                            NativeBridge.setMasterGain(gain.toFloat())
                            result.success(null)
                        } else {
                            result.error("INVALID_ARGUMENT", "Gain is null", null)
                        }
                    }
                    "getMasterGain" -> {
                        result.success(NativeBridge.getMasterGain().toDouble())
                    }
                    "getSupportedExtensions" -> {
                        val extensions = NativeBridge.getSupportedExtensions()
                        result.success(extensions.toList())
                    }
                    "checkStoragePermission" -> {
                        result.success(checkStoragePermission(applicationContext))
                    }
                    "requestStoragePermission" -> {
                        requestStoragePermission(this)
                        result.success(null)
                    }
                    "checkNotificationPermission" -> {
                        val hasNotification = if (Build.VERSION.SDK_INT >= 33) {
                            applicationContext.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        } else {
                            true
                        }
                        result.success(hasNotification)
                    }
                    "requestNotificationPermission" -> {
                        if (Build.VERSION.SDK_INT >= 33) {
                            this.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1002)
                        }
                        result.success(null)
                    }
                    else -> result.notImplemented()
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
            val hasRead = context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val hasWrite = context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            hasRead || hasWrite
        }
    }

    private fun requestStoragePermission(activity: android.app.Activity) {
        val context = activity.applicationContext
        if (Build.VERSION.SDK_INT >= 30) {
            val packageUri = Uri.parse("package:${context.packageName}")
            val fallbackIntents = listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = packageUri
                },
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = packageUri
                }
            )
            for (intent in fallbackIntents) {
                val launchIntent = Intent(intent).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(launchIntent)
                    return
                } catch (_: Exception) {}
            }
        }
        
        val permissions = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
        activity.requestPermissions(permissions, 1001)
    }
}
