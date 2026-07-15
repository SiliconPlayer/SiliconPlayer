package com.flopster101.siliconplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
                            "decoder" to NativeBridge.getCurrentDecoderName()
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
                    else -> result.notImplemented()
                }
            }
    }
}
