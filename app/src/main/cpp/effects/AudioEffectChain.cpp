#include "AudioEffectChain.h"
#include "EffectRegistry.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <string_view>
#include <unordered_map>

namespace siliconplayer::effects {

namespace {

void applyGainDbIfNonZero(float* buffer, int frames, int channels, float gainDb) {
    if (!buffer || frames <= 0 || channels <= 0 || !std::isfinite(gainDb) || std::abs(gainDb) < 1e-4f) {
        return;
    }
    const float clampedDb = std::clamp(gainDb, -24.0f, 24.0f);
    const float linear = std::pow(10.0f, clampedDb / 20.0f);
    const int totalSamples = frames * channels;
    for (int i = 0; i < totalSamples; ++i) {
        buffer[i] *= linear;
    }
}

void applyMonoIfEnabled(float* buffer, int frames, int channels, bool enabled) {
    if (!enabled || channels != 2 || !buffer || frames <= 0) {
        return;
    }
    for (int i = 0; i < frames; ++i) {
        const float mono = (buffer[i * 2] + buffer[i * 2 + 1]) * 0.5f;
        buffer[i * 2] = mono;
        buffer[i * 2 + 1] = mono;
    }
}

float normalizeParamValue(const ParamDescriptor& desc, float raw) {
    if (!std::isfinite(raw)) {
        return desc.defaultValue;
    }
    if (desc.type == ParamType::Bool) {
        return raw != 0.0f ? 1.0f : 0.0f;
    }
    float clamped = std::clamp(raw, desc.minValue, desc.maxValue);
    if (desc.type == ParamType::Int || desc.type == ParamType::Enum) {
        if (desc.step > 1.0f) {
            const float steps = std::round((clamped - desc.minValue) / desc.step);
            clamped = std::clamp(desc.minValue + (steps * desc.step), desc.minValue, desc.maxValue);
        }
        return static_cast<float>(std::lrint(clamped));
    }
    return clamped;
}

std::vector<float> normalizeNodeParams(
        const EffectDescriptor& desc,
        const std::vector<float>& params) {
    std::vector<float> out(static_cast<size_t>(std::max(0, desc.paramCount)));
    for (int p = 0; p < desc.paramCount && desc.params; ++p) {
        const float raw = (static_cast<size_t>(p) < params.size())
                ? params[static_cast<size_t>(p)]
                : desc.params[p].defaultValue;
        out[static_cast<size_t>(p)] = normalizeParamValue(desc.params[p], raw);
    }
    return out;
}

void applyNodeParams(
        IAudioEffect& effect,
        const EffectDescriptor& desc,
        const std::vector<float>& params) {
    for (int p = 0; p < desc.paramCount && desc.params; ++p) {
        const float value = (static_cast<size_t>(p) < params.size())
                ? normalizeParamValue(desc.params[p], params[static_cast<size_t>(p)])
                : desc.params[p].defaultValue;
        effect.setParam(p, value);
    }
}

class JsonCursor {
public:
    explicit JsonCursor(std::string_view src) : s(src) {}

    void skipWs() {
        while (pos < s.size()) {
            const char c = s[pos];
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                ++pos;
            } else {
                break;
            }
        }
    }

    bool eof() {
        skipWs();
        return pos >= s.size();
    }

    char peek() {
        skipWs();
        return pos < s.size() ? s[pos] : '\0';
    }

    bool consume(char expected) {
        skipWs();
        if (pos < s.size() && s[pos] == expected) {
            ++pos;
            return true;
        }
        return false;
    }

    bool consumeLiteral(std::string_view lit) {
        skipWs();
        if (pos + lit.size() > s.size()) return false;
        if (s.substr(pos, lit.size()) != lit) return false;
        pos += lit.size();
        return true;
    }

    bool parseString(std::string& out) {
        skipWs();
        if (pos >= s.size() || s[pos] != '"') return false;
        ++pos;
        out.clear();
        while (pos < s.size()) {
            const char c = s[pos++];
            if (c == '"') return true;
            if (static_cast<unsigned char>(c) < 0x20) return false;
            if (c != '\\') {
                out.push_back(c);
                continue;
            }
            if (pos >= s.size()) return false;
            const char esc = s[pos++];
            switch (esc) {
                case '"': out.push_back('"'); break;
                case '\\': out.push_back('\\'); break;
                case '/': out.push_back('/'); break;
                case 'b': out.push_back('\b'); break;
                case 'f': out.push_back('\f'); break;
                case 'n': out.push_back('\n'); break;
                case 'r': out.push_back('\r'); break;
                case 't': out.push_back('\t'); break;
                case 'u': {
                    if (pos + 4 > s.size()) return false;
                    uint32_t cp = 0;
                    for (int i = 0; i < 4; ++i) {
                        const char h = s[pos++];
                        cp <<= 4;
                        if (h >= '0' && h <= '9') cp |= static_cast<uint32_t>(h - '0');
                        else if (h >= 'a' && h <= 'f') cp |= static_cast<uint32_t>(10 + h - 'a');
                        else if (h >= 'A' && h <= 'F') cp |= static_cast<uint32_t>(10 + h - 'A');
                        else return false;
                    }
                    if (cp < 0x80) {
                        out.push_back(static_cast<char>(cp));
                    } else if (cp < 0x800) {
                        out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
                        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                    } else {
                        out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
                        out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                        out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                    }
                    break;
                }
                default:
                    return false;
            }
        }
        return false;
    }

    bool parseBool(bool& out) {
        if (consumeLiteral("true")) {
            out = true;
            return true;
        }
        if (consumeLiteral("false")) {
            out = false;
            return true;
        }
        return false;
    }

    // Locale-independent ASCII JSON number parser.
    bool parseNumber(double& out) {
        skipWs();
        if (pos >= s.size()) return false;
        size_t p = pos;
        bool neg = false;
        if (s[p] == '-') {
            neg = true;
            ++p;
            if (p >= s.size()) return false;
        }
        if (s[p] < '0' || s[p] > '9') return false;
        double intPart = 0.0;
        if (s[p] == '0') {
            ++p;
        } else {
            while (p < s.size() && s[p] >= '0' && s[p] <= '9') {
                intPart = intPart * 10.0 + static_cast<double>(s[p] - '0');
                ++p;
            }
        }
        double fracPart = 0.0;
        double fracScale = 1.0;
        if (p < s.size() && s[p] == '.') {
            ++p;
            if (p >= s.size() || s[p] < '0' || s[p] > '9') return false;
            while (p < s.size() && s[p] >= '0' && s[p] <= '9') {
                fracPart = fracPart * 10.0 + static_cast<double>(s[p] - '0');
                fracScale *= 10.0;
                ++p;
            }
        }
        int expSign = 1;
        int expVal = 0;
        if (p < s.size() && (s[p] == 'e' || s[p] == 'E')) {
            ++p;
            if (p < s.size() && (s[p] == '+' || s[p] == '-')) {
                expSign = (s[p] == '-') ? -1 : 1;
                ++p;
            }
            if (p >= s.size() || s[p] < '0' || s[p] > '9') return false;
            while (p < s.size() && s[p] >= '0' && s[p] <= '9') {
                if (expVal < 10000) {
                    expVal = expVal * 10 + (s[p] - '0');
                }
                ++p;
            }
        }
        double val = intPart + (fracPart / fracScale);
        if (expVal != 0) {
            val *= std::pow(10.0, static_cast<double>(expSign * expVal));
        }
        if (neg) val = -val;
        if (!std::isfinite(val)) return false;
        pos = p;
        out = val;
        return true;
    }

    bool skipValue(int depth = 0) {
        if (depth > 16) return false;
        const char c = peek();
        if (c == '"') {
            std::string tmp;
            return parseString(tmp);
        }
        if (c == '{') {
            consume('{');
            if (consume('}')) return true;
            for (;;) {
                std::string key;
                if (!parseString(key) || !consume(':') || !skipValue(depth + 1)) return false;
                if (consume('}')) return true;
                if (!consume(',')) return false;
            }
        }
        if (c == '[') {
            consume('[');
            if (consume(']')) return true;
            for (;;) {
                if (!skipValue(depth + 1)) return false;
                if (consume(']')) return true;
                if (!consume(',')) return false;
            }
        }
        bool b = false;
        if (parseBool(b)) return true;
        if (consumeLiteral("null")) return true;
        double d = 0.0;
        return parseNumber(d);
    }

private:
    std::string_view s;
    size_t pos = 0;
};

bool parseNodeOptionsObject(JsonCursor& cur, NodeOptions& out) {
    if (!cur.consume('{')) return false;
    out = {};
    if (cur.consume('}')) return true;
    for (;;) {
        std::string key;
        if (!cur.parseString(key) || !cur.consume(':')) return false;
        if (key == "mono") {
            bool mono = false;
            if (cur.parseBool(mono)) {
                out.mono = mono;
            } else if (!cur.skipValue()) {
                return false;
            }
        } else if (key == "gainDb") {
            double gain = 0.0;
            if (cur.parseNumber(gain)) {
                out.gainDb = std::clamp(static_cast<float>(gain), -24.0f, 24.0f);
            } else if (!cur.skipValue()) {
                return false;
            }
        } else {
            if (!cur.skipValue()) return false;
        }
        if (cur.consume('}')) return true;
        if (!cur.consume(',')) return false;
    }
}

bool parseNodeObject(JsonCursor& cur, EffectChainSpec& outSpec) {
    if (!cur.consume('{')) return false;
    std::string id;
    bool enabled = true;
    NodeOptions options;
    std::unordered_map<std::string, float> rawParams;

    if (!cur.consume('}')) {
        for (;;) {
            std::string key;
            if (!cur.parseString(key) || !cur.consume(':')) return false;
            if (key == "id") {
                if (!cur.parseString(id) && !cur.skipValue()) return false;
            } else if (key == "enabled") {
                bool b = true;
                if (cur.parseBool(b)) enabled = b;
                else if (!cur.skipValue()) return false;
            } else if (key == "options") {
                if (cur.peek() == '{') {
                    if (!parseNodeOptionsObject(cur, options)) return false;
                } else if (!cur.skipValue()) {
                    return false;
                }
            } else if (key == "params") {
                if (cur.consume('{')) {
                    if (!cur.consume('}')) {
                        for (;;) {
                            std::string pKey;
                            if (!cur.parseString(pKey) || !cur.consume(':')) return false;
                            double num = 0.0;
                            bool b = false;
                            if (cur.parseNumber(num)) {
                                rawParams[pKey] = static_cast<float>(num);
                            } else if (cur.parseBool(b)) {
                                rawParams[pKey] = b ? 1.0f : 0.0f;
                            } else if (!cur.skipValue()) {
                                return false;
                            }
                            if (cur.consume('}')) break;
                            if (!cur.consume(',')) return false;
                        }
                    }
                } else if (!cur.skipValue()) {
                    return false;
                }
            } else {
                if (!cur.skipValue()) return false;
            }
            if (cur.consume('}')) break;
            if (!cur.consume(',')) return false;
        }
    }

    if (id.empty()) {
        return true;
    }
    const EffectDescriptor* desc = EffectRegistry::getInstance().findDescriptor(id);
    if (!desc) {
        return true;
    }

    EffectNodeSpec node;
    node.id = id;
    node.enabled = enabled;
    node.options = options;
    node.params.resize(static_cast<size_t>(std::max(0, desc->paramCount)));
    for (int p = 0; p < desc->paramCount && desc->params; ++p) {
        const auto& pDesc = desc->params[p];
        const auto it = rawParams.find(pDesc.id ? pDesc.id : "");
        const float rawVal = (it != rawParams.end()) ? it->second : pDesc.defaultValue;
        node.params[static_cast<size_t>(p)] = normalizeParamValue(pDesc, rawVal);
    }
    outSpec.nodes.push_back(std::move(node));
    return true;
}

void appendJsonString(std::string& out, std::string_view s) {
    out.push_back('"');
    for (unsigned char c : s) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b"; break;
            case '\f': out += "\\f"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default:
                if (c < 0x20) {
                    constexpr char kHex[] = "0123456789abcdef";
                    out += "\\u00";
                    out.push_back(kHex[(c >> 4) & 0xF]);
                    out.push_back(kHex[c & 0xF]);
                } else {
                    out.push_back(static_cast<char>(c));
                }
                break;
        }
    }
    out.push_back('"');
}

// Locale-independent fixed-point formatting for serialized float parameters.
void appendJsonParamValue(std::string& out, const ParamDescriptor& desc, float value) {
    const float norm = normalizeParamValue(desc, value);
    if (desc.type == ParamType::Bool) {
        out += (norm != 0.0f) ? "true" : "false";
        return;
    }
    if (desc.type == ParamType::Int || desc.type == ParamType::Enum) {
        out += std::to_string(static_cast<long long>(std::llround(norm)));
        return;
    }
    const bool neg = norm < 0.0f;
    const double absVal = std::abs(static_cast<double>(norm));
    const uint64_t scaled = static_cast<uint64_t>(std::llround(absVal * 10000.0));
    const uint64_t intPart = scaled / 10000u;
    uint64_t fracPart = scaled % 10000u;
    if (neg && (intPart > 0 || fracPart > 0)) {
        out.push_back('-');
    }
    out += std::to_string(intPart);
    if (fracPart == 0) {
        out += ".0";
        return;
    }
    out.push_back('.');
    char digits[4] = {
            static_cast<char>('0' + ((fracPart / 1000u) % 10u)),
            static_cast<char>('0' + ((fracPart / 100u) % 10u)),
            static_cast<char>('0' + ((fracPart / 10u) % 10u)),
            static_cast<char>('0' + (fracPart % 10u)),
    };
    int len = 4;
    while (len > 1 && digits[len - 1] == '0') {
        --len;
    }
    out.append(digits, static_cast<size_t>(len));
}

} // namespace

EffectChainSpec parseEffectChainJson(std::string_view json) {
    constexpr size_t kMaxChainJsonBytes = 64 * 1024;
    if (json.empty() || json.size() > kMaxChainJsonBytes) {
        return {};
    }

    JsonCursor cur(json);
    if (!cur.consume('{')) {
        return {};
    }

    EffectChainSpec parsed;
    int version = 1;
    bool versionValid = true;

    if (!cur.consume('}')) {
        for (;;) {
            std::string key;
            if (!cur.parseString(key) || !cur.consume(':')) {
                return {};
            }
            if (key == "version") {
                double v = 0.0;
                if (!cur.parseNumber(v)) {
                    return {};
                }
                const double rounded = std::round(v);
                if (std::abs(v - rounded) > 1e-6 || static_cast<int>(rounded) != 1) {
                    versionValid = false;
                } else {
                    version = static_cast<int>(rounded);
                }
            } else if (key == "input") {
                if (cur.peek() == '{') {
                    if (!parseNodeOptionsObject(cur, parsed.inputOptions)) return {};
                } else if (!cur.skipValue()) {
                    return {};
                }
            } else if (key == "master") {
                if (cur.peek() == '{') {
                    if (!parseNodeOptionsObject(cur, parsed.masterOptions)) return {};
                } else if (!cur.skipValue()) {
                    return {};
                }
            } else if (key == "nodes") {
                if (cur.consume('[')) {
                    if (!cur.consume(']')) {
                        for (;;) {
                            if (parsed.nodes.size() >= 64) {
                                if (!cur.skipValue()) return {};
                            } else if (!parseNodeObject(cur, parsed)) {
                                return {};
                            }
                            if (cur.consume(']')) break;
                            if (!cur.consume(',')) return {};
                        }
                    }
                } else if (!cur.skipValue()) {
                    return {};
                }
            } else {
                if (!cur.skipValue()) {
                    return {};
                }
            }
            if (cur.consume('}')) break;
            if (!cur.consume(',')) return {};
        }
    }

    if (!cur.eof() || !versionValid || version != 1) {
        return {};
    }
    return parsed;
}

std::string serializeEffectChainJson(const EffectChainSpec& spec) {
    auto& registry = EffectRegistry::getInstance();
    constexpr ParamDescriptor kGainOptDesc { "gainDb", "Gain", ParamType::Float, -24.0f, 24.0f, 0.0f, 0.5f, 0 };
    std::string out = "{\"version\":1,\"input\":{\"mono\":";
    out += spec.inputOptions.mono ? "true" : "false";
    if (std::isfinite(spec.inputOptions.gainDb) && std::abs(spec.inputOptions.gainDb) >= 1e-4f) {
        out += ",\"gainDb\":";
        appendJsonParamValue(out, kGainOptDesc, spec.inputOptions.gainDb);
    }
    out += "},\"master\":{\"mono\":";
    out += spec.masterOptions.mono ? "true" : "false";
    if (std::isfinite(spec.masterOptions.gainDb) && std::abs(spec.masterOptions.gainDb) >= 1e-4f) {
        out += ",\"gainDb\":";
        appendJsonParamValue(out, kGainOptDesc, spec.masterOptions.gainDb);
    }
    out += "},\"nodes\":[";

    bool firstNode = true;
    for (const auto& node : spec.nodes) {
        const EffectDescriptor* desc = registry.findDescriptor(node.id);
        if (!desc) {
            continue;
        }
        if (!firstNode) out.push_back(',');
        firstNode = false;

        out += "{\"id\":";
        appendJsonString(out, node.id);
        out += ",\"enabled\":";
        out += node.enabled ? "true" : "false";
        out += ",\"options\":{\"mono\":";
        out += node.options.mono ? "true" : "false";
        out += "},\"params\":{";
        for (int p = 0; p < desc->paramCount && desc->params; ++p) {
            if (p > 0) out.push_back(',');
            const auto& pDesc = desc->params[p];
            appendJsonString(out, pDesc.id ? pDesc.id : "");
            out.push_back(':');
            const float val = (static_cast<size_t>(p) < node.params.size())
                    ? node.params[static_cast<size_t>(p)]
                    : pDesc.defaultValue;
            appendJsonParamValue(out, pDesc, val);
        }
        out += "}}";
    }
    out += "]}";
    return out;
}

void AudioEffectChain::prepare(int sampleRate, int channels) {
    if (sampleRate <= 0 || channels <= 0) {
        return;
    }
    preparedSampleRate = sampleRate;
    preparedChannels = channels;
    for (auto& node : liveNodes) {
        if (node.effect) {
            node.effect->prepare(sampleRate, channels);
        }
    }
    refreshLatencyLocked();
}

void AudioEffectChain::reset() {
    preparedSampleRate = 0;
    preparedChannels = 0;
    for (auto& node : liveNodes) {
        if (node.effect) {
            node.effect->reset();
        }
    }
    refreshLatencyLocked();
}

void AudioEffectChain::setSpec(const EffectChainSpec& spec, int sampleRate, int channels) {
    auto& registry = EffectRegistry::getInstance();

    EffectChainSpec filteredSpec;
    filteredSpec.inputOptions = spec.inputOptions;
    filteredSpec.masterOptions = spec.masterOptions;

    std::vector<LiveNode> builtNodes;
    builtNodes.reserve(spec.nodes.size());

    for (const auto& nodeSpec : spec.nodes) {
        const EffectDescriptor* desc = registry.findDescriptor(nodeSpec.id);
        if (!desc) {
            continue;
        }
        auto instance = registry.create(nodeSpec.id);
        if (!instance) {
            continue;
        }
        EffectNodeSpec normalizedSpec = nodeSpec;
        normalizedSpec.params = normalizeNodeParams(*desc, nodeSpec.params);

        instance->reset();
        applyNodeParams(*instance, *desc, normalizedSpec.params);
        if (sampleRate > 0 && channels > 0) {
            instance->prepare(sampleRate, channels);
        }

        LiveNode live;
        live.id = normalizedSpec.id;
        live.enabled = normalizedSpec.enabled;
        live.mono.store(normalizedSpec.options.mono, std::memory_order_relaxed);
        live.effect = std::move(instance);
        builtNodes.push_back(std::move(live));
        filteredSpec.nodes.push_back(std::move(normalizedSpec));
    }

    liveNodes = std::move(builtNodes);
    currentSpec = std::move(filteredSpec);
    inputMono.store(currentSpec.inputOptions.mono, std::memory_order_relaxed);
    inputGainDb.store(currentSpec.inputOptions.gainDb, std::memory_order_relaxed);
    masterMono.store(currentSpec.masterOptions.mono, std::memory_order_relaxed);
    masterGainDb.store(currentSpec.masterOptions.gainDb, std::memory_order_relaxed);
    if (sampleRate > 0 && channels > 0) {
        preparedSampleRate = sampleRate;
        preparedChannels = channels;
    } else {
        preparedSampleRate = 0;
        preparedChannels = 0;
    }
    refreshLatencyLocked();
}

bool AudioEffectChain::updateIfStructurallyEqual(const EffectChainSpec& spec) {
    auto& registry = EffectRegistry::getInstance();

    size_t liveIndex = 0;
    for (const auto& nodeSpec : spec.nodes) {
        if (!registry.findDescriptor(nodeSpec.id)) {
            continue;
        }
        if (liveIndex >= liveNodes.size()) {
            return false;
        }
        const auto& existing = liveNodes[liveIndex];
        if (existing.id != nodeSpec.id || existing.enabled != nodeSpec.enabled) {
            return false;
        }
        ++liveIndex;
    }
    if (liveIndex != liveNodes.size()) {
        return false;
    }

    inputMono.store(spec.inputOptions.mono, std::memory_order_relaxed);
    inputGainDb.store(spec.inputOptions.gainDb, std::memory_order_relaxed);
    masterMono.store(spec.masterOptions.mono, std::memory_order_relaxed);
    masterGainDb.store(spec.masterOptions.gainDb, std::memory_order_relaxed);
    currentSpec.inputOptions = spec.inputOptions;
    currentSpec.masterOptions = spec.masterOptions;

    liveIndex = 0;
    for (const auto& nodeSpec : spec.nodes) {
        const EffectDescriptor* desc = registry.findDescriptor(nodeSpec.id);
        if (!desc) {
            continue;
        }
        EffectNodeSpec normalizedSpec = nodeSpec;
        normalizedSpec.params = normalizeNodeParams(*desc, nodeSpec.params);

        auto& live = liveNodes[liveIndex];
        live.mono.store(normalizedSpec.options.mono, std::memory_order_relaxed);
        if (live.effect) {
            applyNodeParams(*live.effect, *desc, normalizedSpec.params);
        }
        currentSpec.nodes[liveIndex] = std::move(normalizedSpec);
        ++liveIndex;
    }
    refreshLatencyLocked();
    return true;
}

void AudioEffectChain::processInputStage(float* buffer, int frames, int channels) {
    if (!buffer || frames <= 0 || channels <= 0) {
        return;
    }
    applyGainDbIfNonZero(buffer, frames, channels, inputGainDb.load(std::memory_order_relaxed));
    if (channels == 2) {
        applyMonoIfEnabled(buffer, frames, channels, inputMono.load(std::memory_order_relaxed));
    }
}

void AudioEffectChain::processNodesAndMasterStage(
        float* buffer,
        int frames,
        int channels,
        int sampleRate,
        bool applyDeferredInputMono) {
    if (!buffer || frames <= 0 || channels <= 0) {
        return;
    }
    if (sampleRate > 0 && (sampleRate != preparedSampleRate || channels != preparedChannels)) {
        prepare(sampleRate, channels);
    }
    if (applyDeferredInputMono && channels == 2) {
        applyMonoIfEnabled(buffer, frames, channels, inputMono.load(std::memory_order_relaxed));
    }

    for (auto& node : liveNodes) {
        if (!node.enabled || !node.effect) {
            continue;
        }
        node.effect->process(buffer, frames, channels);
        applyMonoIfEnabled(buffer, frames, channels, node.mono.load(std::memory_order_relaxed));
    }

    applyGainDbIfNonZero(buffer, frames, channels, masterGainDb.load(std::memory_order_relaxed));
    applyMonoIfEnabled(buffer, frames, channels, masterMono.load(std::memory_order_relaxed));
}

void AudioEffectChain::process(float* buffer, int frames, int channels, int sampleRate) {
    if (!buffer || frames <= 0 || channels <= 0) {
        return;
    }
    processInputStage(buffer, frames, channels);
    processNodesAndMasterStage(buffer, frames, channels, sampleRate, channels != 2);
}

void AudioEffectChain::process(float* buffer, int frames, int channels) {
    process(buffer, frames, channels, preparedSampleRate);
}

void AudioEffectChain::refreshLatencyLocked() {
    double total = 0.0;
    for (const auto& node : liveNodes) {
        if (node.enabled && node.effect) {
            total += std::max(0.0, node.effect->latencyFrames());
        }
    }
    cachedLatencyFrames.store(total, std::memory_order_relaxed);
}

} // namespace siliconplayer::effects
