package com.flopster101.siliconplayer.audio

import com.flopster101.siliconplayer.AppDefaults
import com.flopster101.siliconplayer.NativeBridge
import org.json.JSONArray
import org.json.JSONObject
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

internal data class NodeOptions(
    val mono: Boolean = false
)

internal data class EffectNode(
    val id: String,
    val enabled: Boolean = true,
    val options: NodeOptions = NodeOptions(),
    val params: Map<String, Float> = emptyMap()
)

internal data class EffectChain(
    val input: NodeOptions = NodeOptions(),
    val master: NodeOptions = NodeOptions(),
    val nodes: List<EffectNode> = emptyList()
) {
    fun withMasterMono(mono: Boolean): EffectChain = copy(master = master.copy(mono = mono))

    fun withInputMono(mono: Boolean): EffectChain = copy(input = input.copy(mono = mono))

    fun findNode(id: String): EffectNode? = nodes.firstOrNull { it.id == id }

    fun isNodeActive(id: String): Boolean = nodes.any { it.id == id && it.enabled }

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
        root.put("input", JSONObject().put("mono", input.mono))
        root.put("master", JSONObject().put("mono", master.mono))
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

private data class ParamSchema(
    val id: String,
    val minValue: Float,
    val maxValue: Float,
    val defaultValue: Float,
    val step: Float = 0f,
    val isInteger: Boolean = false
)

private data class EffectSchema(
    val id: String,
    val params: List<ParamSchema>
)

private val KnownEffectSchemas: Map<String, EffectSchema> = listOf(
    EffectSchema(
        id = EffectNodeIds.OPENMPT_BASS,
        params = listOf(
            ParamSchema("depth", 0f, 4f, AppDefaults.AudioProcessing.Dsp.bassDepth.toFloat(), 1f, isInteger = true),
            ParamSchema("range", 0f, 4f, AppDefaults.AudioProcessing.Dsp.bassRange.toFloat(), 1f, isInteger = true)
        )
    ),
    EffectSchema(
        id = EffectNodeIds.OPENMPT_SURROUND,
        params = listOf(
            ParamSchema("depth", 1f, 16f, AppDefaults.AudioProcessing.Dsp.surroundDepth.toFloat(), 1f, isInteger = true),
            ParamSchema("delayMs", 5f, 45f, AppDefaults.AudioProcessing.Dsp.surroundDelayMs.toFloat(), 5f, isInteger = true)
        )
    ),
    EffectSchema(
        id = EffectNodeIds.OPENMPT_REVERB,
        params = listOf(
            ParamSchema("depth", 1f, 16f, AppDefaults.AudioProcessing.Dsp.reverbDepth.toFloat(), 1f, isInteger = true),
            ParamSchema("preset", 0f, 28f, AppDefaults.AudioProcessing.Dsp.reverbPreset.toFloat(), 1f, isInteger = true)
        )
    ),
    EffectSchema(
        id = EffectNodeIds.OPENMPT_BITCRUSH,
        params = listOf(
            ParamSchema("bits", 1f, 24f, AppDefaults.AudioProcessing.Dsp.bitCrushBits.toFloat(), 1f, isInteger = true)
        )
    ),
    EffectSchema(
        id = EffectNodeIds.GAIN,
        params = listOf(
            ParamSchema("gainDb", -24f, 24f, 0f, 0f, isInteger = false)
        )
    )
).associateBy { it.id }

private fun parseNodeOptions(obj: JSONObject?): NodeOptions {
    if (obj == null) return NodeOptions()
    val mono = if (obj.has("mono") && obj.opt("mono") is Boolean) {
        obj.optBoolean("mono", false)
    } else {
        false
    }
    return NodeOptions(mono = mono)
}

private fun normalizeParamsForSchema(
    schema: EffectSchema,
    raw: Map<String, Float>
): Map<String, Float> {
    val result = LinkedHashMap<String, Float>(schema.params.size)
    for (p in schema.params) {
        val candidate = raw[p.id]?.takeIf { it.isFinite() } ?: p.defaultValue
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
