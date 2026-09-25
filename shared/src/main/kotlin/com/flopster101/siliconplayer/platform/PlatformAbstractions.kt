package com.flopster101.siliconplayer.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

interface AppPreferences {
    fun getString(key: String, defValue: String?): String?
    fun getInt(key: String, defValue: Int): Int
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun getFloat(key: String, defValue: Float): Float
    fun getLong(key: String, defValue: Long): Long
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?

    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putInt(key: String, value: Int): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun putFloat(key: String, value: Float): Editor
        fun putLong(key: String, value: Long): Editor
        fun putStringSet(key: String, values: Set<String>?): Editor
        fun remove(key: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }

    fun edit(): Editor

    fun interface OnChangeListener {
        fun onPreferenceChanged(prefs: AppPreferences, key: String)
    }

    fun addListener(listener: OnChangeListener)
    fun removeListener(listener: OnChangeListener)
}

val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    error("No AppPreferences provided")
}

interface PreferencesProvider {
    fun getPreferences(name: String): AppPreferences
}

val LocalPreferencesProvider = staticCompositionLocalOf<PreferencesProvider> {
    error("No PreferencesProvider provided")
}

val LocalIsWatchDevice = staticCompositionLocalOf { false }
val LocalIsRoundScreen = staticCompositionLocalOf { false }

enum class AudioOutputRouteType {
    Speaker,
    Headphones,
    Usb,
    Bluetooth
}

data class AudioOutputRouteInfo(
    val type: AudioOutputRouteType,
    val name: String
)

interface AudioRouteManager {
    @Composable
    fun rememberCurrentRoute(): AudioOutputRouteInfo
    fun openAudioOutputSwitcher()
    fun formatUsbAudioName(rawName: String): String
}

val LocalAudioRouteManager = staticCompositionLocalOf<AudioRouteManager> {
    object : AudioRouteManager {
        @Composable
        override fun rememberCurrentRoute(): AudioOutputRouteInfo {
            return AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "Default Output")
        }
        override fun openAudioOutputSwitcher() {}
        override fun formatUsbAudioName(rawName: String): String = rawName
    }
}

fun interface ToastHandler {
    fun showToast(message: String)
}

val LocalToastHandler = staticCompositionLocalOf<ToastHandler> {
    ToastHandler { message -> println("[Toast] $message") }
}

typealias BackHandlerCallback = @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit

val LocalPlatformBackHandler = staticCompositionLocalOf<BackHandlerCallback> {
    { _, _ -> }
}

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    LocalPlatformBackHandler.current(enabled, onBack)
}

data class WindowSizeInfo(
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val smallestScreenWidthDp: Int,
    val isRound: Boolean = false
)

val LocalWindowSizeInfo = staticCompositionLocalOf {
    WindowSizeInfo(screenWidthDp = 360, screenHeightDp = 640, smallestScreenWidthDp = 360, isRound = false)
}

interface ProjectMOptionsProvider {
    fun getEnabledPresetLabels(): Map<String, String>
}

val LocalProjectMOptionsProvider = staticCompositionLocalOf<ProjectMOptionsProvider> {
    object : ProjectMOptionsProvider {
        override fun getEnabledPresetLabels(): Map<String, String> = emptyMap()
    }
}
