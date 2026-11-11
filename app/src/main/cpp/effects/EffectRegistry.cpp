#include "EffectRegistry.h"

#include "nodes/GainEffect.h"
#include "nodes/OpenMptBassEffect.h"
#include "nodes/OpenMptBitCrushEffect.h"
#include "nodes/OpenMptReverbEffect.h"
#include "nodes/OpenMptSurroundEffect.h"

#include <cstdio>
#include <cstring>

namespace siliconplayer::effects {

namespace {

const char* paramTypeToString(ParamType type) {
    switch (type) {
        case ParamType::Bool: return "bool";
        case ParamType::Int: return "int";
        case ParamType::Float: return "float";
        case ParamType::Enum: return "enum";
    }
    return "float";
}

void appendJsonEscaped(std::string& out, const char* s) {
    out.push_back('"');
    if (s) {
        for (const unsigned char* p = reinterpret_cast<const unsigned char*>(s); *p; ++p) {
            switch (*p) {
                case '"': out += "\\\""; break;
                case '\\': out += "\\\\"; break;
                case '\b': out += "\\b"; break;
                case '\f': out += "\\f"; break;
                case '\n': out += "\\n"; break;
                case '\r': out += "\\r"; break;
                case '\t': out += "\\t"; break;
                default:
                    if (*p < 0x20) {
                        char buf[8];
                        std::snprintf(buf, sizeof(buf), "\\u%04x", static_cast<unsigned>(*p));
                        out += buf;
                    } else {
                        out.push_back(static_cast<char>(*p));
                    }
                    break;
            }
        }
    }
    out.push_back('"');
}

void appendJsonFloat(std::string& out, float value) {
    char buf[32];
    std::snprintf(buf, sizeof(buf), "%.6g", static_cast<double>(value));
    out += buf;
}

} // namespace

EffectRegistry& EffectRegistry::getInstance() {
    static EffectRegistry instance;
    return instance;
}

EffectRegistry::EffectRegistry() {
    registerEffect(OpenMptBassEffect::descriptor());
    registerEffect(OpenMptSurroundEffect::descriptor());
    registerEffect(OpenMptReverbEffect::descriptor());
    registerEffect(OpenMptBitCrushEffect::descriptor());
    registerEffect(GainEffect::descriptor());
}

void EffectRegistry::registerEffect(const EffectDescriptor& descriptor) {
    if (!descriptor.id || descriptor.id[0] == '\0' || !descriptor.create) {
        return;
    }
    for (auto& existing : registered) {
        if (std::strcmp(existing.id, descriptor.id) == 0) {
            existing = descriptor;
            return;
        }
    }
    registered.push_back(descriptor);
}

const EffectDescriptor* EffectRegistry::findDescriptor(std::string_view id) const {
    for (const auto& desc : registered) {
        if (id == desc.id) {
            return &desc;
        }
    }
    return nullptr;
}

std::unique_ptr<IAudioEffect> EffectRegistry::create(std::string_view id) const {
    const EffectDescriptor* desc = findDescriptor(id);
    if (!desc || !desc->create) {
        return nullptr;
    }
    return std::unique_ptr<IAudioEffect>(desc->create());
}

std::string EffectRegistry::getEffectDescriptorsJson() const {
    std::string out = "[";
    for (size_t i = 0; i < registered.size(); ++i) {
        if (i > 0) out.push_back(',');
        const auto& desc = registered[i];
        out += "{\"id\":";
        appendJsonEscaped(out, desc.id);
        out += ",\"displayName\":";
        appendJsonEscaped(out, desc.displayName);
        out += ",\"schemaVersion\":" + std::to_string(desc.schemaVersion);
        out += ",\"supportsDryWet\":";
        out += desc.supportsDryWet ? "true" : "false";
        out += ",\"params\":[";
        for (int p = 0; p < desc.paramCount && desc.params; ++p) {
            if (p > 0) out.push_back(',');
            const auto& param = desc.params[p];
            out += "{\"id\":";
            appendJsonEscaped(out, param.id);
            out += ",\"label\":";
            appendJsonEscaped(out, param.label);
            out += ",\"type\":";
            appendJsonEscaped(out, paramTypeToString(param.type));
            out += ",\"min\":";
            appendJsonFloat(out, param.minValue);
            out += ",\"max\":";
            appendJsonFloat(out, param.maxValue);
            out += ",\"default\":";
            appendJsonFloat(out, param.defaultValue);
            out += ",\"step\":";
            appendJsonFloat(out, param.step);
            if (param.type == ParamType::Enum) {
                out += ",\"enumCount\":" + std::to_string(param.enumCount);
            }
            out.push_back('}');
        }
        out += "]}";
    }
    out.push_back(']');
    return out;
}

} // namespace siliconplayer::effects
