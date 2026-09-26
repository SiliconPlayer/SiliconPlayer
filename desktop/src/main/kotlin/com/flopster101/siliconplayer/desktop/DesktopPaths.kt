package com.flopster101.siliconplayer.desktop

import java.io.File

/**
 * XDG Base Directory locations for desktop app state.
 *
 * Cache and preferences are confined to `$XDG_CACHE_HOME` / `$XDG_CONFIG_HOME` (falling back to
 * the spec defaults under `$HOME`), so app state never lands in `$HOME` directly.
 */
internal object DesktopPaths {
    private const val APP_DIR_NAME = "siliconplayer"
    private const val LEGACY_APP_DIR_NAME = ".siliconplayer"
    private const val PREFS_PACKAGE_DIR = "com/flopster101/siliconplayer"
    private const val PREFS_SUBPATH = ".java/.userPrefs"

    fun cacheDir(): File = appDir("XDG_CACHE_HOME", ".cache")

    fun configDir(): File = appDir("XDG_CONFIG_HOME", ".config")

    /**
     * Points `java.util.prefs` at the config dir and moves pre-XDG state into place. Must run
     * before the first `Preferences` access; repeating it is harmless.
     */
    fun install() {
        if (installed) return
        val home = System.getProperty("user.home") ?: return
        migrateLegacyCacheDir(
            legacyDir = File(home, "$LEGACY_APP_DIR_NAME/cache"),
            targetDir = cacheDir()
        )
        val configDir = configDir()
        migrateLegacyPreferencesDir(
            legacyNode = File(home, ".java/.userPrefs/$PREFS_PACKAGE_DIR"),
            targetNode = File(configDir, "$PREFS_SUBPATH/$PREFS_PACKAGE_DIR")
        )
        if (System.getProperty("java.util.prefs.userRoot") == null) {
            System.setProperty("java.util.prefs.userRoot", configDir.absolutePath)
        }
        installed = true
    }

    @Volatile
    private var installed = false

    private fun appDir(environmentKey: String, fallbackSegment: String): File {
        val baseDir = System.getenv(environmentKey)?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: File(System.getProperty("user.home") ?: ".", fallbackSegment)
        return File(baseDir, APP_DIR_NAME).also { it.mkdirs() }
    }
}

/**
 * Moves a pre-XDG cache directory into [targetDir]. Items already present at the target are left
 * untouched, and [legacyDir] is only removed once it is empty.
 */
internal fun migrateLegacyCacheDir(legacyDir: File, targetDir: File) {
    if (!legacyDir.isDirectory) return
    targetDir.mkdirs()
    legacyDir.listFiles().orEmpty().forEach { child ->
        val targetChild = File(targetDir, child.name)
        if (targetChild.exists()) return@forEach
        if (!child.renameTo(targetChild)) {
            child.copyRecursively(targetChild, overwrite = false)
            child.deleteRecursively()
        }
    }
    if (legacyDir.list().isNullOrEmpty()) {
        legacyDir.delete()
        legacyDir.parentFile?.takeIf { it.list().isNullOrEmpty() }?.delete()
    }
}

/**
 * Copies a legacy `java.util.prefs` subtree into [targetNode]. Node names are opaque, so the tree
 * moves verbatim; skipped when the target already has state.
 */
internal fun migrateLegacyPreferencesDir(legacyNode: File, targetNode: File) {
    if (!legacyNode.isDirectory || targetNode.exists()) return
    legacyNode.walkTopDown().forEach { source ->
        val target = File(targetNode, source.relativeTo(legacyNode).path)
        if (source.isDirectory) {
            target.mkdirs()
        } else if (source.name != ".lock") {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = false)
        }
    }
}
