#include "SpatializerConfigJson.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <map>
#include <string>

namespace siliconplayer::spatial {

namespace {

struct JsonValue {
    enum class Kind { String, Number, Bool };
    Kind kind = Kind::String;
    std::string text;
    double number = 0.0;
    bool boolean = false;
};

void skipWhitespace(const std::string_view& src, size_t& pos) {
    while (pos < src.size()) {
        const char c = src[pos];
        if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
            ++pos;
        } else {
            break;
        }
    }
}

bool parseString(const std::string_view& src, size_t& pos, std::string& out) {
    if (pos >= src.size() || src[pos] != '"') {
        return false;
    }
    ++pos;
    out.clear();
    while (pos < src.size()) {
        const char c = src[pos++];
        if (c == '"') {
            return true;
        }
        if (c != '\\') {
            out.push_back(c);
            continue;
        }
        if (pos >= src.size()) {
            return false;
        }
        const char escaped = src[pos++];
        switch (escaped) {
            case 'n': out.push_back('\n'); break;
            case 't': out.push_back('\t'); break;
            case 'r': out.push_back('\r'); break;
            case 'b': out.push_back('\b'); break;
            case 'f': out.push_back('\f'); break;
            case 'u': {
                if (pos + 4 > src.size()) {
                    return false;
                }
                const std::string hex(src.substr(pos, 4));
                pos += 4;
                const unsigned code = static_cast<unsigned>(std::strtoul(hex.c_str(), nullptr, 16));
                if (code < 0x80) {
                    out.push_back(static_cast<char>(code));
                } else if (code < 0x800) {
                    out.push_back(static_cast<char>(0xC0 | (code >> 6)));
                    out.push_back(static_cast<char>(0x80 | (code & 0x3F)));
                } else {
                    out.push_back(static_cast<char>(0xE0 | (code >> 12)));
                    out.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
                    out.push_back(static_cast<char>(0x80 | (code & 0x3F)));
                }
                break;
            }
            default: out.push_back(escaped); break;
        }
    }
    return false;
}

// Parses a flat JSON object into key/value pairs; nested values are skipped.
bool parseFlatObject(const std::string_view& src, std::map<std::string, JsonValue>& out) {
    size_t pos = 0;
    skipWhitespace(src, pos);
    if (pos >= src.size() || src[pos] != '{') {
        return false;
    }
    ++pos;
    skipWhitespace(src, pos);
    if (pos < src.size() && src[pos] == '}') {
        return true;
    }
    while (pos < src.size()) {
        skipWhitespace(src, pos);
        std::string key;
        if (!parseString(src, pos, key)) {
            return false;
        }
        skipWhitespace(src, pos);
        if (pos >= src.size() || src[pos] != ':') {
            return false;
        }
        ++pos;
        skipWhitespace(src, pos);
        if (pos >= src.size()) {
            return false;
        }
        JsonValue value;
        if (src[pos] == '"') {
            value.kind = JsonValue::Kind::String;
            if (!parseString(src, pos, value.text)) {
                return false;
            }
        } else if (src[pos] == 't' || src[pos] == 'f') {
            value.kind = JsonValue::Kind::Bool;
            value.boolean = (src[pos] == 't');
            while (pos < src.size() && src[pos] != ',' && src[pos] != '}') {
                ++pos;
            }
        } else if (src[pos] == 'n') {
            value.kind = JsonValue::Kind::Number;
            value.number = 0.0;
            while (pos < src.size() && src[pos] != ',' && src[pos] != '}') {
                ++pos;
            }
        } else {
            value.kind = JsonValue::Kind::Number;
            const size_t start = pos;
            while (pos < src.size() && src[pos] != ',' && src[pos] != '}') {
                ++pos;
            }
            value.text.assign(src.substr(start, pos - start));
            value.number = std::strtod(value.text.c_str(), nullptr);
        }
        out[key] = value;
        skipWhitespace(src, pos);
        if (pos < src.size() && src[pos] == ',') {
            ++pos;
            continue;
        }
        if (pos < src.size() && src[pos] == '}') {
            return true;
        }
        return false;
    }
    return false;
}

float numberOr(const std::map<std::string, JsonValue>& values, const char* key, float fallback) {
    const auto it = values.find(key);
    if (it == values.end() || it->second.kind != JsonValue::Kind::Number) {
        return fallback;
    }
    if (!std::isfinite(it->second.number)) {
        return fallback;
    }
    return static_cast<float>(it->second.number);
}

int intOr(const std::map<std::string, JsonValue>& values, const char* key, int fallback) {
    return static_cast<int>(std::lround(numberOr(values, key, static_cast<float>(fallback))));
}

bool boolOr(const std::map<std::string, JsonValue>& values, const char* key, bool fallback) {
    const auto it = values.find(key);
    if (it == values.end()) {
        return fallback;
    }
    if (it->second.kind == JsonValue::Kind::Bool) {
        return it->second.boolean;
    }
    if (it->second.kind == JsonValue::Kind::Number) {
        return it->second.number != 0.0;
    }
    return it->second.text == "true";
}

std::string stringOr(const std::map<std::string, JsonValue>& values, const char* key, const std::string& fallback) {
    const auto it = values.find(key);
    if (it == values.end() || it->second.kind != JsonValue::Kind::String) {
        return fallback;
    }
    return it->second.text;
}

std::string escapeJson(const std::string& text) {
    std::string out;
    out.reserve(text.size() + 2);
    for (const char c : text) {
        switch (c) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: out.push_back(c); break;
        }
    }
    return out;
}

const char* backendName(SpatializerBackendId backend) {
    switch (backend) {
        case SpatializerBackendId::LibSpatialAudio: return "libspatialaudio";
        case SpatializerBackendId::SteamAudio: return "steam_audio";
        case SpatializerBackendId::None: break;
    }
    return "off";
}

SpatializerBackendId backendFromName(const std::string& name) {
    if (name == "libspatialaudio") {
        return SpatializerBackendId::LibSpatialAudio;
    }
    if (name == "steam_audio") {
        return SpatializerBackendId::SteamAudio;
    }
    return SpatializerBackendId::None;
}

} // namespace

SpatializerConfig parseSpatializerConfigJson(std::string_view json) {
    SpatializerConfig config;
    std::map<std::string, JsonValue> values;
    if (!parseFlatObject(json, values)) {
        return config;
    }
    config.backend = backendFromName(stringOr(values, "backend", "off"));
    config.enabled = boolOr(values, "enabled", true);
    config.spatializeStereo = boolOr(values, "spatializeStereo", false);
    config.centerGainDb = numberOr(values, "centerGainDb", config.centerGainDb);
    config.surroundGainDb = numberOr(values, "surroundGainDb", config.surroundGainDb);
    config.heightGainDb = numberOr(values, "heightGainDb", config.heightGainDb);
    config.lfeGainDb = numberOr(values, "lfeGainDb", config.lfeGainDb);
    config.lfeLowPassEnabled = boolOr(values, "lfeLowPassEnabled", config.lfeLowPassEnabled);
    config.wetMix = std::clamp(numberOr(values, "wetMix", config.wetMix), 0.0f, 1.0f);
    config.bassGainDb = std::clamp(numberOr(values, "bassGainDb", config.bassGainDb), -12.0f, 12.0f);
    config.trebleGainDb = std::clamp(numberOr(values, "trebleGainDb", config.trebleGainDb), -12.0f, 12.0f);
    config.surroundTrebleGainDb = std::clamp(
            numberOr(values, "surroundTrebleGainDb", config.surroundTrebleGainDb), -12.0f, 12.0f);
    config.wideSurround = boolOr(values, "wideSurround", config.wideSurround);
    config.frontAmbience = boolOr(values, "frontAmbience", config.frontAmbience);
    config.virtualizerWidth = std::clamp(numberOr(values, "virtualizerWidth", config.virtualizerWidth), 0.0f, 2.0f);
    config.frontDiffuse = boolOr(values, "frontDiffuse", config.frontDiffuse);
    config.compressorMode = std::clamp(intOr(values, "compressorMode", config.compressorMode), 0, 2);
    config.outputGainDb = std::clamp(numberOr(values, "outputGainDb", config.outputGainDb), -24.0f, 18.0f);
    config.ambisonicOrder = std::clamp(intOr(values, "ambisonicOrder", config.ambisonicOrder), 1, 3);
    config.renderMethod = std::clamp(intOr(values, "renderMethod", config.renderMethod), 0, 1);
    config.yawDegrees = numberOr(values, "yawDegrees", config.yawDegrees);
    config.customSofaPath = stringOr(values, "customSofaPath", config.customSofaPath);
    return config;
}

std::string serializeSpatializerConfigJson(const SpatializerConfig& config) {
    std::string out = "{";
    out += "\"backend\":\"" + std::string(backendName(config.backend)) + "\",";
    out += "\"enabled\":" + std::string(config.enabled ? "true" : "false") + ",";
    out += "\"spatializeStereo\":" + std::string(config.spatializeStereo ? "true" : "false") + ",";
    out += "\"centerGainDb\":" + std::to_string(config.centerGainDb) + ",";
    out += "\"surroundGainDb\":" + std::to_string(config.surroundGainDb) + ",";
    out += "\"heightGainDb\":" + std::to_string(config.heightGainDb) + ",";
    out += "\"lfeGainDb\":" + std::to_string(config.lfeGainDb) + ",";
    out += "\"lfeLowPassEnabled\":" + std::string(config.lfeLowPassEnabled ? "true" : "false") + ",";
    out += "\"wetMix\":" + std::to_string(config.wetMix) + ",";
    out += "\"bassGainDb\":" + std::to_string(config.bassGainDb) + ",";
    out += "\"trebleGainDb\":" + std::to_string(config.trebleGainDb) + ",";
    out += "\"surroundTrebleGainDb\":" + std::to_string(config.surroundTrebleGainDb) + ",";
    out += "\"wideSurround\":" + std::string(config.wideSurround ? "true" : "false") + ",";
    out += "\"frontAmbience\":" + std::string(config.frontAmbience ? "true" : "false") + ",";
    out += "\"virtualizerWidth\":" + std::to_string(config.virtualizerWidth) + ",";
    out += "\"frontDiffuse\":" + std::string(config.frontDiffuse ? "true" : "false") + ",";
    out += "\"compressorMode\":" + std::to_string(config.compressorMode) + ",";
    out += "\"outputGainDb\":" + std::to_string(config.outputGainDb) + ",";
    out += "\"ambisonicOrder\":" + std::to_string(config.ambisonicOrder) + ",";
    out += "\"renderMethod\":" + std::to_string(config.renderMethod) + ",";
    out += "\"yawDegrees\":" + std::to_string(config.yawDegrees) + ",";
    out += "\"customSofaPath\":\"" + escapeJson(config.customSofaPath) + "\"";
    out += "}";
    return out;
}

} // namespace siliconplayer::spatial
