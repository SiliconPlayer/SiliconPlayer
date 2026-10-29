package com.flopster101.siliconplayer.settings.porting

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire format (v1):
 * {
 *   "format": "siliconplayer.settings",
 *   "configVersion": 1,
 *   "appVersion": "0.8.3",
 *   "appPlatform": "desktop",
 *   "generatedAt": 1730000000000,
 *   "entries": {
 *     "audio.general": {
 *       "audio_master_volume_db": {"t": "b", "v": true}
 *     }
 *   }
 * }
 *
 * Entries carry dense per-entry key objects so a future SiliconPlayer that
 * adds/reorders entries only ever skates over what it does not know.
 */
internal object SettingsPortingJson {
    fun readMeta(text: String): PortingFileMeta? {
        val obj = try { JSONObject(text) } catch (_: Exception) { return null }
        if (obj.optString("format") != SETTINGS_PORTING_FORMAT_ID) return null
        val version = if (obj.has("configVersion")) obj.optInt("configVersion", -1) else -1
        return PortingFileMeta(
            configVersion = version,
            appVersion = obj.optString("appVersion"),
            appPlatform = obj.optString("appPlatform"),
            generatedAt = obj.optLong("generatedAt", 0L)
        )
    }

    sealed class RawValue {
        abstract val type: PrefType
        abstract val value: Any?
        class Known(override val type: PrefType, override val value: Any?) : RawValue()
        class Malformed(val reason: String) : RawValue() {
            override val type: PrefType get() = PrefType.String
            override val value: Any? get() = null
        }
    }

    fun iterateEntries(text: String, action: (entryId: String, key: String, RawValue) -> Unit): Boolean {
        val obj = try { JSONObject(text) } catch (_: Exception) { return false }
        val entries = obj.optJSONObject("entries") ?: return false
        val keyNames = entries.keys()
        while (keyNames.hasNext()) {
            val entryId = keyNames.next()
            val entryKeys = entries.optJSONObject(entryId) ?: continue
            val itKey = entryKeys.keys()
            while (itKey.hasNext()) {
                val key = itKey.next()
                val slot = entryKeys.optJSONObject(key) ?: continue
                val raw = parse(slot)
                if (raw != null) action(entryId, key, raw)
            }
        }
        return true
    }

    private fun parse(slot: JSONObject): RawValue? {
        val tag = slot.optString("t")
        val type = PrefType.entries.firstOrNull { it.tag == tag }
            ?: return RawValue.Malformed("unknown type '$tag'")
        if (!slot.has("v")) return RawValue.Malformed("missing value")
        val value: Any? = when (type) {
            PrefType.Int -> if (slot.opt("v") is Int) slot.getInt("v") else return RawValue.Malformed("not an integer")
            PrefType.Long -> if (slot.opt("v") is Long) slot.getLong("v") else return RawValue.Malformed("not a long")
            PrefType.Float -> {
                val raw = slot.opt("v")
                if (raw is Double || raw is Int || raw is Long) raw.toFloat()
                else return RawValue.Malformed("not a float")
            }
            PrefType.Boolean -> if (slot.opt("v") is Boolean) slot.getBoolean("v") else return RawValue.Malformed("not a boolean")
            PrefType.String -> if (slot.opt("v") is String) slot.getString("v") else return RawValue.Malformed("not a string")
            PrefType.StringSet -> {
                val arr = slot.optJSONArray("v")
                if (arr == null) RawValue.Malformed("not an array")
                else {
                    val set = LinkedHashSet<String>()
                    for (i in 0 until arr.length()) {
                        val item = arr.opt(i) ?: continue
                        if (item is String) set.add(item) else return RawValue.Malformed("array contains a non-string")
                    }
                    set
                }
            }
        }
        return RawValue.Known(type, value)
    }

    fun write(
        meta: PortingFileMeta,
        entryValues: Map<String, Map<String, Pair<PrefType, Any?>>>
    ): String {
        val root = JSONObject()
        root.put("format", SETTINGS_PORTING_FORMAT_ID)
        root.put("configVersion", meta.configVersion)
        root.put("appVersion", meta.appVersion)
        root.put("appPlatform", meta.appPlatform)
        root.put("generatedAt", meta.generatedAt)
        val entriesJson = JSONObject()
        for ((entryId, keys) in entryValues) {
            if (keys.isEmpty()) continue
            val entryJson = JSONObject()
            for ((key, typed) in keys) {
                val v = JSONObject()
                v.put("t", typed.first.tag)
                when (typed.first) {
                    PrefType.StringSet -> {
                        val arr = JSONArray()
                        (typed.second as? Set<*>)?.forEach { arr.put(it) }
                        v.put("v", arr)
                    }
                    else -> v.put("v", typed.second)
                }
                entryJson.put(key, v)
            }
            entriesJson.put(entryId, entryJson)
        }
        root.put("entries", entriesJson)
        return root.toString(2)
    }
}

internal data class PortingFileMeta(
    val configVersion: Int,
    val appVersion: String,
    val appPlatform: String,
    val generatedAt: Long
)
