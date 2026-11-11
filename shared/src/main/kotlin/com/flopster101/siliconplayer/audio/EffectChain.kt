package com.flopster101.siliconplayer.audio

import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.NativeBridge
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

internal object EffectNodeIds {
    const val OPENMPT_BASS = "openmpt.bass"
    const val OPENMPT_SURROUND = "openmpt.surround"
    const val OPENMPT_REVERB = "openmpt.reverb"
    const val OPENMPT_BITCRUSH = "openmpt.bitcrush"
    const val GAIN = "gain"

    val legacyOpenMptOrder: List<String> = listOf(
        OPENMPT_BASS,
        OPENMPT_SURROUND,
        OPENMPT_REVERB,
        OPENMPT_BITCRUSH
    )
}

internal enum class EffectParamType {
    Bool,
    Int,
    Float,
    Enum;

    companion object {
        fun fromJsonKey(raw: String?): EffectParamType = when (raw?.trim()?.lowercase()) {
            "bool" -> Bool
            "int" -> Int
            "enum" -> Enum
            else -> Float
        }
    }
}

internal data class EffectParamDescriptor(
    val id: String,
    val label: String,
    val type: EffectParamType,
    val minValue: Float,
    val maxValue: Float,
    val defaultValue: Float,
    val step: Float = 0f,
    val enumCount: Int = 0
) {
    val isInteger: Boolean
        get() = type == EffectParamType.Int || type == EffectParamType.Enum || type == EffectParamType.Bool
}

internal data class EffectDescriptor(
    val id: String,
    val displayName: String,
    val schemaVersion: Int = 1,
    val supportsDryWet: Boolean = false,
    val params: List<EffectParamDescriptor> = emptyList()
)

internal val DefaultEffectDescriptors: List<EffectDescriptor> = listOf(
    EffectDescriptor(
        id = EffectNodeIds.OPENMPT_BASS,
        displayName = "OpenMPT Bass Expansion",
        schemaVersion = 1,
        supportsDryWet = false,
        params = listOf(
            EffectParamDescriptor(
                id = "depth",
                label = "Depth",
                type = EffectParamType.Int,
                minValue = 0f,
                maxValue = 4f,
                defaultValue = AppDefaults.AudioProcessing.Dsp.bassDepth.toFloat(),
                step = 1f
            ),
            EffectParamDescriptor(
                id = "range",
                label = "Range",
                type = EffectParamType.Int,
                minValue = 0f,
                maxValue = 4f,
                defaultValue = AppDefaults.AudioProcessing.Dsp.bassRange.toFloat(),
                step = 1f
            )
        )
    ),
    EffectDescriptor(
        id = EffectNodeIds.OPENMPT_SURROUND,
        displayName = "OpenMPT Surround",
        schemaVersion = 1,
        supportsDryWet = false,
        params = listOf(
            EffectParamDescriptor(
                id = "depth",
                label = "Depth",
                type = EffectParamType.Int,
                minValue = 1f,
                maxValue = 16f,
                defaultValue = AppDefaults.AudioProcessing.Dsp.surroundDepth.toFloat(),
                step = 1f
            ),
            EffectParamDescriptor(
                id = "delayMs",
                label = "Delay (ms)",
                type = EffectParamType.Int,
                minValue = 5f,
                maxValue = 45f,
                defaultValue = AppDefaults.AudioProcessing.Dsp.surroundDelayMs.toFloat(),
                step = 5f
            )
        )
    ),
    EffectDescriptor(
        id = EffectNodeIds.OPENMPT_REVERB,
        displayName = "OpenMPT Reverb",
        schemaVersion = 1,
        supportsDryWet = false,
        params = listOf(
            EffectParamDescriptor(
                id = "depth",
                label = "Depth",
                type = EffectParamType.Int,
                minValue = 1f,
                maxValue = 16f,
                defaultValue = AppDefaults.AudioProcessing.Dsp.reverbDepth.toFloat(),
                step = 1f
            ),
            EffectParamDescriptor(
                id = "preset",
                label = "Preset",
                type = EffectParamType.Enum,
                minValue = 0f,
                maxValue = 28f,
                defaultValue = AppDefaults.AudioProcessing.Dsp.reverbPreset.toFloat(),
                step = 1f,
                enumCount = 29
            )
        )
    ),
    EffectDescriptor(
        id = EffectNodeIds.OPENMPT_BITCRUSH,
        displayName = "OpenMPT Bit Crush",
        schemaVersion = 1,
        supportsDryWet = false,
        params = listOf(
            EffectParamDescriptor(
                id = "bits",
                label = "Bit Depth",
                type = EffectParamType.Int,
                minValue = 1f,
                maxValue = 24f,
                defaultValue = AppDefaults.AudioProcessing.Dsp.bitCrushBits.toFloat(),
                step = 1f
            )
        )
    ),
    EffectDescriptor(
        id = EffectNodeIds.GAIN,
        displayName = "Gain",
        schemaVersion = 1,
        supportsDryWet = false,
        params = listOf(
            EffectParamDescriptor(
                id = "gainDb",
                label = "Gain (dB)",
                type = EffectParamType.Float,
                minValue = -24f,
                maxValue = 24f,
                defaultValue = 0f,
                step = 0f
            )
        )
    )
)

private val KnownEffectSchemas: Map<String, EffectDescriptor> =
    DefaultEffectDescriptors.associateBy { it.id }

internal fun parseEffectDescriptorsJson(json: String?): List<EffectDescriptor> {
    if (json.isNullOrBlank()) return emptyList()
    val array = try {
        JSONArray(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val out = ArrayList<EffectDescriptor>(array.length())
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val id = obj.optString("id", "").trim().takeIf { it.isNotEmpty() } ?: continue
        val displayName = obj.optString("displayName", id).trim().ifEmpty { id }
        val schemaVersion = obj.optInt("schemaVersion", 1).coerceAtLeast(1)
        val supportsDryWet = obj.optBoolean("supportsDryWet", false)
        val paramsArray = obj.optJSONArray("params")
        val params = ArrayList<EffectParamDescriptor>()
        if (paramsArray != null) {
            for (p in 0 until paramsArray.length()) {
                val pObj = paramsArray.optJSONObject(p) ?: continue
                val pId = pObj.optString("id", "").trim().takeIf { it.isNotEmpty() } ?: continue
                val pLabel = pObj.optString("label", pId).trim().ifEmpty { pId }
                val pType = EffectParamType.fromJsonKey(pObj.optString("type", "float"))
                val minVal = pObj.optDouble("min", 0.0).toFloat().takeIf { it.isFinite() } ?: 0f
                val maxVal = pObj.optDouble("max", 1.0).toFloat().takeIf { it.isFinite() } ?: 1f
                val defVal = pObj.optDouble("default", minVal.toDouble()).toFloat().takeIf { it.isFinite() } ?: minVal
                val stepVal = pObj.optDouble("step", 0.0).toFloat().takeIf { it.isFinite() && it >= 0f } ?: 0f
                val enumCount = pObj.optInt("enumCount", 0).coerceAtLeast(0)
                params.add(
                    EffectParamDescriptor(
                        id = pId,
                        label = pLabel,
                        type = pType,
                        minValue = minVal,
                        maxValue = maxVal,
                        defaultValue = defVal,
                        step = stepVal,
                        enumCount = enumCount
                    )
                )
            }
        }
        out.add(
            EffectDescriptor(
                id = id,
                displayName = displayName,
                schemaVersion = schemaVersion,
                supportsDryWet = supportsDryWet,
                params = params
            )
        )
    }
    return out
}

internal fun queryEffectDescriptors(): List<EffectDescriptor> {
    val raw = try {
        NativeBridge.getEffectDescriptorsJson()
    } catch (_: Throwable) {
        null
    }
    val parsed = parseEffectDescriptorsJson(raw)
    return parsed.ifEmpty { DefaultEffectDescriptors }
}

internal fun findEffectDescriptor(
    id: String,
    descriptors: List<EffectDescriptor> = DefaultEffectDescriptors
): EffectDescriptor? =
    descriptors.firstOrNull { it.id == id } ?: KnownEffectSchemas[id]

internal fun defaultParamsForDescriptor(descriptor: EffectDescriptor): Map<String, Float> {
    val map = LinkedHashMap<String, Float>(descriptor.params.size)
    for (p in descriptor.params) {
        map[p.id] = p.defaultValue
    }
    return normalizeParamsForSchema(descriptor, map)
}

internal fun createDefaultEffectNode(
    id: String,
    descriptors: List<EffectDescriptor> = DefaultEffectDescriptors
): EffectNode? {
    val descriptor = findEffectDescriptor(id, descriptors) ?: return null
    return EffectNode(
        id = descriptor.id,
        enabled = true,
        options = NodeOptions(),
        params = defaultParamsForDescriptor(descriptor)
    )
}

private val effectNodeUidCounter = AtomicLong(1L)

internal fun nextEffectNodeUid(): Long = effectNodeUidCounter.getAndIncrement()

internal data class NodeOptions(
    val mono: Boolean = false,
    val gainDb: Float = 0f
)

internal data class EffectNode(
    val id: String,
    val enabled: Boolean = true,
    val options: NodeOptions = NodeOptions(),
    val params: Map<String, Float> = emptyMap(),
    val uid: Long = nextEffectNodeUid()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EffectNode) return false
        return id == other.id &&
            enabled == other.enabled &&
            options == other.options &&
            params == other.params
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + options.hashCode()
        result = 31 * result + params.hashCode()
        return result
    }
}

internal data class EffectChain(
    val input: NodeOptions = NodeOptions(),
    val master: NodeOptions = NodeOptions(),
    val nodes: List<EffectNode> = emptyList()
) {
    fun withMasterMono(mono: Boolean): EffectChain = copy(master = master.copy(mono = mono))

    fun withMasterGainDb(gainDb: Float): EffectChain =
        copy(master = master.copy(gainDb = gainDb.coerceIn(-24f, 24f)))

    fun withInputMono(mono: Boolean): EffectChain = copy(input = input.copy(mono = mono))

    fun withInputGainDb(gainDb: Float): EffectChain =
        copy(input = input.copy(gainDb = gainDb.coerceIn(-24f, 24f)))

    fun findNode(id: String): EffectNode? = nodes.firstOrNull { it.id == id }

    fun isNodeActive(id: String): Boolean = nodes.any { it.id == id && it.enabled }

    fun addNode(
        id: String,
        descriptors: List<EffectDescriptor> = DefaultEffectDescriptors
    ): EffectChain {
        val node = createDefaultEffectNode(id, descriptors) ?: return this
        return copy(nodes = nodes + node)
    }

    fun removeNodeAt(index: Int): EffectChain {
        if (index !in nodes.indices) return this
        val updated = nodes.toMutableList()
        updated.removeAt(index)
        return copy(nodes = updated)
    }

    fun moveNode(fromIndex: Int, toIndex: Int): EffectChain {
        if (fromIndex !in nodes.indices || toIndex !in nodes.indices || fromIndex == toIndex) {
            return this
        }
        val updated = nodes.toMutableList()
        val item = updated.removeAt(fromIndex)
        updated.add(toIndex, item)
        return copy(nodes = updated)
    }

    fun setNodeEnabledAt(index: Int, enabled: Boolean): EffectChain {
        if (index !in nodes.indices) return this
        val current = nodes[index]
        if (current.enabled == enabled) return this
        val updated = nodes.toMutableList()
        updated[index] = current.copy(enabled = enabled)
        return copy(nodes = updated)
    }

    fun setNodeMonoAt(index: Int, mono: Boolean): EffectChain {
        if (index !in nodes.indices) return this
        val current = nodes[index]
        if (current.options.mono == mono) return this
        val updated = nodes.toMutableList()
        updated[index] = current.copy(options = current.options.copy(mono = mono))
        return copy(nodes = updated)
    }

    fun setNodeParamAt(
        index: Int,
        paramId: String,
        value: Float,
        descriptors: List<EffectDescriptor> = DefaultEffectDescriptors
    ): EffectChain {
        if (index !in nodes.indices) return this
        val current = nodes[index]
        val descriptor = findEffectDescriptor(current.id, descriptors) ?: return this
        val mergedParams = LinkedHashMap(current.params)
        mergedParams[paramId] = value
        val normalized = normalizeParamsForSchema(descriptor, mergedParams)
        if (current.params == normalized) return this
        val updated = nodes.toMutableList()
        updated[index] = current.copy(params = normalized)
        return copy(nodes = updated)
    }

    fun withFreshNodeUids(): EffectChain = copy(
        nodes = nodes.map { it.copy(uid = nextEffectNodeUid()) }
    )

    fun withLegacyNodeState(
        id: String,
        enabled: Boolean,
        params: Map<String, Float>
    ): EffectChain {
        val spec = KnownEffectSchemas[id] ?: return this
        val normalizedParams = normalizeParamsForSchema(spec, params)
        val existingIdx = nodes.indexOfFirst { it.id == id }
        if (existingIdx >= 0) {
            val updated = nodes.toMutableList()
            if (!enabled) {
                updated.removeAt(existingIdx)
            } else {
                val prev = updated[existingIdx]
                updated[existingIdx] = prev.copy(enabled = true, params = normalizedParams)
            }
            return copy(nodes = updated)
        }
        if (!enabled) {
            return this
        }
        val targetRank = EffectNodeIds.legacyOpenMptOrder.indexOf(id)
        val updated = nodes.toMutableList()
        val insertIdx = if (targetRank >= 0) {
            val idx = updated.indexOfFirst { existing ->
                val rank = EffectNodeIds.legacyOpenMptOrder.indexOf(existing.id)
                rank > targetRank
            }
            if (idx >= 0) idx else updated.size
        } else {
            updated.size
        }
        updated.add(insertIdx, EffectNode(id = id, enabled = true, params = normalizedParams))
        return copy(nodes = updated)
    }

    fun withoutOpenMptNodes(): EffectChain = copy(
        nodes = nodes.filterNot { it.id in EffectNodeIds.legacyOpenMptOrder }
    )

    fun toJson(): String {
        val root = JSONObject()
        root.put("version", SCHEMA_VERSION)
        root.put("input", serializeNodeOptions(input))
        root.put("master", serializeNodeOptions(master))
        val nodesArray = JSONArray()
        for (node in nodes) {
            val schema = KnownEffectSchemas[node.id] ?: continue
            val normalizedParams = normalizeParamsForSchema(schema, node.params)
            val paramsObj = JSONObject()
            for (paramSpec in schema.params) {
                val value = normalizedParams[paramSpec.id] ?: paramSpec.defaultValue
                if (paramSpec.isInteger) {
                    paramsObj.put(paramSpec.id, value.roundToInt())
                } else {
                    paramsObj.put(paramSpec.id, value.toDouble())
                }
            }
            val nodeObj = JSONObject()
                .put("id", node.id)
                .put("enabled", node.enabled)
                .put("options", JSONObject().put("mono", node.options.mono))
                .put("params", paramsObj)
            nodesArray.put(nodeObj)
        }
        root.put("nodes", nodesArray)
        return root.toString()
    }

    companion object {
        const val SCHEMA_VERSION = 1
        val Empty = EffectChain()

        fun fromJson(json: String?): EffectChain {
            if (json.isNullOrBlank()) return Empty
            val root = try {
                JSONObject(json)
            } catch (_: Exception) {
                return Empty
            }
            if (root.has("version")) {
                val rawVersion = root.opt("version")
                val version = when (rawVersion) {
                    is Number -> {
                        val d = rawVersion.toDouble()
                        if (!d.isFinite() || d != d.roundToInt().toDouble()) return Empty
                        rawVersion.toInt()
                    }
                    else -> return Empty
                }
                if (version != SCHEMA_VERSION) return Empty
            }

            val input = parseNodeOptions(root.optJSONObject("input"))
            val master = parseNodeOptions(root.optJSONObject("master"))
            val nodesArray = root.optJSONArray("nodes")
            val parsedNodes = ArrayList<EffectNode>()
            if (nodesArray != null) {
                val limit = nodesArray.length().coerceAtMost(64)
                for (i in 0 until limit) {
                    val nodeObj = nodesArray.optJSONObject(i) ?: continue
                    val id = nodeObj.optString("id", "").takeIf { it.isNotEmpty() } ?: continue
                    val schema = KnownEffectSchemas[id] ?: continue
                    val enabled = if (nodeObj.has("enabled") && nodeObj.opt("enabled") is Boolean) {
                        nodeObj.optBoolean("enabled", true)
                    } else {
                        true
                    }
                    val options = parseNodeOptions(nodeObj.optJSONObject("options"))
                    val rawParamsObj = nodeObj.optJSONObject("params")
                    val rawParams = LinkedHashMap<String, Float>()
                    if (rawParamsObj != null) {
                        for (pSpec in schema.params) {
                            if (!rawParamsObj.has(pSpec.id)) continue
                            when (val rawVal = rawParamsObj.opt(pSpec.id)) {
                                is Number -> {
                                    val f = rawVal.toFloat()
                                    if (f.isFinite()) rawParams[pSpec.id] = f
                                }
                                is Boolean -> rawParams[pSpec.id] = if (rawVal) 1f else 0f
                            }
                        }
                    }
                    parsedNodes.add(
                        EffectNode(
                            id = id,
                            enabled = enabled,
                            options = options,
                            params = normalizeParamsForSchema(schema, rawParams)
                        )
                    )
                }
            }
            return EffectChain(input = input, master = master, nodes = parsedNodes)
        }
    }
}

private fun serializeNodeOptions(options: NodeOptions): JSONObject {
    val obj = JSONObject().put("mono", options.mono)
    if (options.gainDb.isFinite() && kotlin.math.abs(options.gainDb) >= 1e-4f) {
        obj.put("gainDb", options.gainDb.coerceIn(-24f, 24f).toDouble())
    }
    return obj
}

private fun parseNodeOptions(obj: JSONObject?): NodeOptions {
    if (obj == null) return NodeOptions()
    val mono = if (obj.has("mono") && obj.opt("mono") is Boolean) {
        obj.optBoolean("mono", false)
    } else {
        false
    }
    val gainDb = when (val rawGain = obj.opt("gainDb")) {
        is Number -> rawGain.toFloat().takeIf { it.isFinite() }?.coerceIn(-24f, 24f) ?: 0f
        else -> 0f
    }
    return NodeOptions(mono = mono, gainDb = gainDb)
}

private fun normalizeParamsForSchema(
    schema: EffectDescriptor,
    raw: Map<String, Float>
): Map<String, Float> {
    val result = LinkedHashMap<String, Float>(schema.params.size)
    for (p in schema.params) {
        val candidate = raw[p.id]?.takeIf { it.isFinite() } ?: p.defaultValue
        if (p.type == EffectParamType.Bool) {
            result[p.id] = if (candidate >= 0.5f) 1f else 0f
            continue
        }
        var clamped = candidate.coerceIn(p.minValue, p.maxValue)
        if (p.isInteger) {
            if (p.step > 1f) {
                val steps = ((clamped - p.minValue) / p.step).roundToInt()
                clamped = (p.minValue + steps * p.step).coerceIn(p.minValue, p.maxValue)
            }
            clamped = clamped.roundToInt().toFloat()
        }
        result[p.id] = clamped
    }
    return result
}

internal data class LegacyDialogChainDraft(
    val chain: EffectChain = EffectChain.Empty,
    val bassDepth: Int = AppDefaults.AudioProcessing.Dsp.bassDepth,
    val bassRange: Int = AppDefaults.AudioProcessing.Dsp.bassRange,
    val surroundDepth: Int = AppDefaults.AudioProcessing.Dsp.surroundDepth,
    val surroundDelayMs: Int = AppDefaults.AudioProcessing.Dsp.surroundDelayMs,
    val reverbDepth: Int = AppDefaults.AudioProcessing.Dsp.reverbDepth,
    val reverbPreset: Int = AppDefaults.AudioProcessing.Dsp.reverbPreset,
    val bitCrushBits: Int = AppDefaults.AudioProcessing.Dsp.bitCrushBits
) {
    val bassEnabled: Boolean get() = chain.isNodeActive(EffectNodeIds.OPENMPT_BASS)
    val surroundEnabled: Boolean get() = chain.isNodeActive(EffectNodeIds.OPENMPT_SURROUND)
    val reverbEnabled: Boolean get() = chain.isNodeActive(EffectNodeIds.OPENMPT_REVERB)
    val bitCrushEnabled: Boolean get() = chain.isNodeActive(EffectNodeIds.OPENMPT_BITCRUSH)

    fun withMasterMono(mono: Boolean): LegacyDialogChainDraft =
        copy(chain = chain.withMasterMono(mono))

    fun withBass(enabled: Boolean = bassEnabled, depth: Int = bassDepth, range: Int = bassRange): LegacyDialogChainDraft {
        val d = depth.coerceIn(0, 4)
        val r = range.coerceIn(0, 4)
        return copy(
            chain = chain.withLegacyNodeState(
                EffectNodeIds.OPENMPT_BASS,
                enabled,
                mapOf("depth" to d.toFloat(), "range" to r.toFloat())
            ),
            bassDepth = d,
            bassRange = r
        )
    }

    fun withSurround(
        enabled: Boolean = surroundEnabled,
        depth: Int = surroundDepth,
        delayMs: Int = surroundDelayMs
    ): LegacyDialogChainDraft {
        val d = depth.coerceIn(1, 16)
        val ms = normalizeSurroundDelayMsPref(delayMs)
        return copy(
            chain = chain.withLegacyNodeState(
                EffectNodeIds.OPENMPT_SURROUND,
                enabled,
                mapOf("depth" to d.toFloat(), "delayMs" to ms.toFloat())
            ),
            surroundDepth = d,
            surroundDelayMs = ms
        )
    }

    fun withReverb(
        enabled: Boolean = reverbEnabled,
        depth: Int = reverbDepth,
        preset: Int = reverbPreset
    ): LegacyDialogChainDraft {
        val d = depth.coerceIn(1, 16)
        val p = preset.coerceIn(0, 28)
        return copy(
            chain = chain.withLegacyNodeState(
                EffectNodeIds.OPENMPT_REVERB,
                enabled,
                mapOf("depth" to d.toFloat(), "preset" to p.toFloat())
            ),
            reverbDepth = d,
            reverbPreset = p
        )
    }

    fun withBitCrush(
        enabled: Boolean = bitCrushEnabled,
        bits: Int = bitCrushBits
    ): LegacyDialogChainDraft {
        val b = bits.coerceIn(1, 24)
        return copy(
            chain = chain.withLegacyNodeState(
                EffectNodeIds.OPENMPT_BITCRUSH,
                enabled,
                mapOf("bits" to b.toFloat())
            ),
            bitCrushBits = b
        )
    }

    fun resetDspNodes(): LegacyDialogChainDraft = LegacyDialogChainDraft(
        chain = chain.withoutOpenMptNodes()
    )

    companion object {
        fun fromChain(chain: EffectChain, fallbackDsp: DspSettings = defaultDspSettings()): LegacyDialogChainDraft {
            val bass = chain.findNode(EffectNodeIds.OPENMPT_BASS)
            val surround = chain.findNode(EffectNodeIds.OPENMPT_SURROUND)
            val reverb = chain.findNode(EffectNodeIds.OPENMPT_REVERB)
            val bitCrush = chain.findNode(EffectNodeIds.OPENMPT_BITCRUSH)
            return LegacyDialogChainDraft(
                chain = chain,
                bassDepth = bass?.params?.get("depth")?.roundToInt()?.coerceIn(0, 4) ?: fallbackDsp.bassDepth,
                bassRange = bass?.params?.get("range")?.roundToInt()?.coerceIn(0, 4) ?: fallbackDsp.bassRange,
                surroundDepth = surround?.params?.get("depth")?.roundToInt()?.coerceIn(1, 16) ?: fallbackDsp.surroundDepth,
                surroundDelayMs = surround?.params?.get("delayMs")?.roundToInt()?.let(::normalizeSurroundDelayMsPref)
                    ?: fallbackDsp.surroundDelayMs,
                reverbDepth = reverb?.params?.get("depth")?.roundToInt()?.coerceIn(1, 16) ?: fallbackDsp.reverbDepth,
                reverbPreset = reverb?.params?.get("preset")?.roundToInt()?.coerceIn(0, 28) ?: fallbackDsp.reverbPreset,
                bitCrushBits = bitCrush?.params?.get("bits")?.roundToInt()?.coerceIn(1, 24) ?: fallbackDsp.bitCrushBits
            )
        }
    }
}

internal fun applyEffectChainToNative(chain: EffectChain) {
    NativeBridge.setEffectChain(chain.toJson())
}
