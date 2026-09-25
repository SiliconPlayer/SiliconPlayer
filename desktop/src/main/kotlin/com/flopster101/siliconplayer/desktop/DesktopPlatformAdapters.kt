package com.flopster101.siliconplayer.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.flopster101.siliconplayer.platform.AppPreferences
import com.flopster101.siliconplayer.platform.AudioOutputRouteInfo
import com.flopster101.siliconplayer.platform.AudioOutputRouteType
import com.flopster101.siliconplayer.platform.AudioRouteManager
import com.flopster101.siliconplayer.platform.LocalAppPreferences
import com.flopster101.siliconplayer.platform.LocalAudioRouteManager
import com.flopster101.siliconplayer.platform.LocalIsWatchDevice
import com.flopster101.siliconplayer.platform.LocalPlatformBackHandler
import com.flopster101.siliconplayer.platform.LocalPreferencesProvider
import com.flopster101.siliconplayer.platform.LocalProjectMOptionsProvider
import com.flopster101.siliconplayer.platform.LocalToastHandler
import com.flopster101.siliconplayer.platform.LocalWindowSizeInfo
import com.flopster101.siliconplayer.platform.PreferencesProvider
import com.flopster101.siliconplayer.platform.ProjectMOptionsProvider
import com.flopster101.siliconplayer.platform.ToastHandler
import com.flopster101.siliconplayer.platform.WindowSizeInfo
import java.util.concurrent.CopyOnWriteArrayList
import java.util.prefs.Preferences

class DesktopAppPreferences(private val nodeName: String) : AppPreferences {
    private val prefs: Preferences = Preferences.userRoot().node("com/flopster101/siliconplayer/$nodeName")
    private val listeners = CopyOnWriteArrayList<AppPreferences.OnChangeListener>()

    init {
        prefs.addPreferenceChangeListener { evt ->
            val key = evt.key
            if (key != null) {
                listeners.forEach { it.onPreferenceChanged(this, key) }
            }
        }
    }

    override fun getString(key: String, defValue: String?): String? = prefs.get(key, defValue)
    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)
    override fun getFloat(key: String, defValue: Float): Float = prefs.getFloat(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val raw = prefs.get(key, null) ?: return defValues
        return raw.split("\n").filter { it.isNotEmpty() }.toSet()
    }

    override fun edit(): AppPreferences.Editor = Editor()

    override fun addListener(listener: AppPreferences.OnChangeListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: AppPreferences.OnChangeListener) {
        listeners.remove(listener)
    }

    private inner class Editor : AppPreferences.Editor {
        private val modifications = mutableListOf<(Preferences) -> Unit>()

        override fun putString(key: String, value: String?): AppPreferences.Editor {
            modifications.add { p ->
                if (value != null) p.put(key, value) else p.remove(key)
            }
            return this
        }

        override fun putInt(key: String, value: Int): AppPreferences.Editor {
            modifications.add { p -> p.putInt(key, value) }
            return this
        }

        override fun putBoolean(key: String, value: Boolean): AppPreferences.Editor {
            modifications.add { p -> p.putBoolean(key, value) }
            return this
        }

        override fun putFloat(key: String, value: Float): AppPreferences.Editor {
            modifications.add { p -> p.putFloat(key, value) }
            return this
        }

        override fun putLong(key: String, value: Long): AppPreferences.Editor {
            modifications.add { p -> p.putLong(key, value) }
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): AppPreferences.Editor {
            modifications.add { p ->
                if (values != null) p.put(key, values.joinToString("\n")) else p.remove(key)
            }
            return this
        }

        override fun remove(key: String): AppPreferences.Editor {
            modifications.add { p -> p.remove(key) }
            return this
        }

        override fun clear(): AppPreferences.Editor {
            modifications.add { p -> p.clear() }
            return this
        }

        override fun apply() {
            for (mod in modifications) {
                mod(prefs)
            }
            try {
                prefs.flush()
            } catch (_: Throwable) {}
        }

        override fun commit(): Boolean {
            apply()
            return true
        }
    }
}

class DesktopPreferencesProvider : PreferencesProvider {
    private val cache = mutableMapOf<String, AppPreferences>()

    override fun getPreferences(name: String): AppPreferences {
        return synchronized(cache) {
            cache.getOrPut(name) { DesktopAppPreferences(name) }
        }
    }
}

class DesktopAudioRouteManager : AudioRouteManager {
    @Composable
    override fun rememberCurrentRoute(): AudioOutputRouteInfo {
        return AudioOutputRouteInfo(AudioOutputRouteType.Speaker, "System Output")
    }

    override fun openAudioOutputSwitcher() {}

    override fun formatUsbAudioName(rawName: String): String = rawName
}

@Composable
fun ProvideDesktopPlatformAdapters(
    windowWidthDp: Int = 1100,
    windowHeightDp: Int = 750,
    content: @Composable () -> Unit
) {
    val prefsProvider = remember { DesktopPreferencesProvider() }
    val prefs = remember { prefsProvider.getPreferences("main_preferences") }
    val audioRouteManager = remember { DesktopAudioRouteManager() }
    val toastHandler = remember { ToastHandler { msg -> println("[SiliconPlayer] $msg") } }
    val windowSizeInfo = remember(windowWidthDp, windowHeightDp) {
        WindowSizeInfo(
            screenWidthDp = windowWidthDp,
            screenHeightDp = windowHeightDp,
            smallestScreenWidthDp = minOf(windowWidthDp, windowHeightDp),
            isRound = false
        )
    }
    val projectMOptionsProvider = remember {
        object : ProjectMOptionsProvider {
            override fun getEnabledPresetLabels(): Map<String, String> = emptyMap()
        }
    }

    val appVersionInfo = remember {
        com.flopster101.siliconplayer.platform.AppVersionInfo(
            versionName = "1.0.0",
            abiOrArch = System.getProperty("os.arch") ?: "desktop",
            gitSha = "desktop"
        )
    }

    CompositionLocalProvider(
        LocalAppPreferences provides prefs,
        LocalPreferencesProvider provides prefsProvider,
        LocalIsWatchDevice provides false,
        LocalAudioRouteManager provides audioRouteManager,
        LocalToastHandler provides toastHandler,
        LocalPlatformBackHandler provides { _, _ -> },
        LocalWindowSizeInfo provides windowSizeInfo,
        LocalProjectMOptionsProvider provides projectMOptionsProvider,
        com.flopster101.siliconplayer.platform.LocalAppVersionInfo provides appVersionInfo,
        com.flopster101.siliconplayer.platform.LocalSettingsPlatformContent provides object : com.flopster101.siliconplayer.platform.SettingsPlatformContent {},
        content = content
    )
}

