package com.flopster101.siliconplayer.desktop

import com.flopster101.siliconplayer.AppPreferenceKeys
import com.flopster101.siliconplayer.platform.AppPreferences
import java.io.File
import java.util.UUID

data class DesktopProjectMSet(
    val id: String,
    val label: String,
    val dir: String
)

object DesktopProjectMPresetSets {
    private const val KEY_SEPARATOR = "\u001F"
    private const val INTERNAL_TEST_ID = "internal_projectm_tests"

    private fun findInternalTestDir(): String? {
        val home = System.getProperty("user.home") ?: ""
        val candidates = listOfNotNull(
            File("external/projectm/presets/tests"),
            File("../external/projectm/presets/tests"),
            File("SiliconPlayer/external/projectm/presets/tests"),
            File("/mnt/nvme_build/projects/drdsnd_alt/SiliconPlayer/external/projectm/presets/tests"),
            if (home.isNotEmpty()) File(home, "projects/drdsnd_alt/SiliconPlayer/external/projectm/presets/tests") else null
        )
        return candidates.firstOrNull { it.isDirectory }?.absolutePath
    }

    private fun findSystemSets(): List<DesktopProjectMSet> {
        val home = System.getProperty("user.home") ?: ""
        val candidateRoots = listOfNotNull(
            File("/usr/share/projectM/presets"),
            File("/usr/share/projectM"),
            File("/usr/local/share/projectM/presets"),
            File("/usr/local/share/projectM"),
            if (home.isNotEmpty()) File(home, ".projectM/presets") else null,
            if (home.isNotEmpty()) File(home, ".projectM") else null
        )
        val discovered = mutableListOf<DesktopProjectMSet>()
        val seenDirs = mutableSetOf<String>()
        for (root in candidateRoots) {
            if (!root.isDirectory) continue
            val children = root.listFiles { f -> f.isDirectory } ?: continue
            for (child in children) {
                val canon = try { child.canonicalPath } catch (_: Throwable) { child.absolutePath }
                if (seenDirs.add(canon)) {
                    val hasMilk = child.walkTopDown().maxDepth(3).any { it.isFile && it.name.endsWith(".milk", ignoreCase = true) }
                    if (hasMilk) {
                        val prettyName = child.name.removePrefix("presets_").replace("_", " ").replaceFirstChar { it.uppercase() }
                        discovered.add(
                            DesktopProjectMSet(
                                id = "system_${child.name}",
                                label = "System — $prettyName",
                                dir = canon
                            )
                        )
                    }
                }
            }
            val hasDirectMilk = root.listFiles { f -> f.isFile && f.name.endsWith(".milk", ignoreCase = true) }?.isNotEmpty() == true
            val rootCanon = try { root.canonicalPath } catch (_: Throwable) { root.absolutePath }
            if (hasDirectMilk && seenDirs.add(rootCanon)) {
                discovered.add(
                    DesktopProjectMSet(
                        id = "system_${root.name}",
                        label = "System — ${root.name.replaceFirstChar { it.uppercase() }}",
                        dir = rootCanon
                    )
                )
            }
        }
        return discovered
    }

    fun userSets(prefs: AppPreferences): List<DesktopProjectMSet> {
        val raw = prefs.getString(AppPreferenceKeys.VISUALIZATION_PROJECTM_USER_PRESET_PATHS, null)
            ?: return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val path = line.trim()
            if (path.isEmpty()) return@mapNotNull null
            val dir = File(path)
            if (!dir.isDirectory) return@mapNotNull null
            val label = dir.name.ifBlank { path }
            DesktopProjectMSet(
                id = "user_${UUID.nameUUIDFromBytes(path.toByteArray())}",
                label = label,
                dir = dir.absolutePath
            )
        }
    }

    fun allSets(prefs: AppPreferences): List<DesktopProjectMSet> {
        val list = mutableListOf<DesktopProjectMSet>()
        val testDir = findInternalTestDir()
        if (testDir != null) {
            list.add(DesktopProjectMSet(INTERNAL_TEST_ID, "Built-in — projectM tests", testDir))
        }
        list.addAll(findSystemSets())
        list.addAll(userSets(prefs))
        return list
    }

    fun enabledSetIds(prefs: AppPreferences): Set<String> {
        val stored = prefs.getStringSet(AppPreferenceKeys.VISUALIZATION_PROJECTM_ENABLED_SET_IDS, null)
        if (stored != null && stored.isNotEmpty()) return stored
        // Default to all known sets
        return allSets(prefs).map { it.id }.toSet()
    }

    fun enabledSets(prefs: AppPreferences): List<DesktopProjectMSet> {
        val enabled = enabledSetIds(prefs)
        return allSets(prefs).filter { it.id in enabled }
    }

    private fun scanRelativeMilk(root: File): List<String> {
        if (!root.isDirectory) return emptyList()
        val out = mutableListOf<String>()
        val stack = ArrayDeque<Pair<File, String>>()
        stack.add(root to "")
        while (stack.isNotEmpty()) {
            val (dir, prefix) = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                val rel = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                if (child.isDirectory) {
                    stack.add(child to rel)
                } else if (child.isFile && child.name.endsWith(".milk", ignoreCase = true)) {
                    out.add(rel)
                }
            }
        }
        out.sort()
        return out
    }

    fun indexedPresetKeys(prefs: AppPreferences): Pair<List<String>, List<String>> {
        val sets = enabledSets(prefs).sortedBy { it.id }
        if (sets.isEmpty()) return emptyList<String>() to emptyList()
        val allKeys = ArrayList<String>(4096)
        val allSetIds = ArrayList<String>(4096)
        for (set in sets) {
            val presets = scanRelativeMilk(File(set.dir))
            for (rel in presets) {
                allKeys.add("${set.id}$KEY_SEPARATOR$rel")
                allSetIds.add(set.id)
            }
        }
        return allKeys to allSetIds
    }

    fun splitKey(key: String): Pair<String, String> {
        val idx = key.indexOf(KEY_SEPARATOR)
        if (idx <= 0) return key to ""
        return key.substring(0, idx) to key.substring(idx + KEY_SEPARATOR.length)
    }
}
