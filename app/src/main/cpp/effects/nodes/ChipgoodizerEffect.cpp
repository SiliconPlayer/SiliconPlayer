#include "ChipgoodizerEffect.h"

#include "chipgoodizer/Params.hpp"

#include <vector>

namespace siliconplayer::effects {

namespace {

// Preset knob values, read from the engine's own preset table so the UI never
// has to keep a second copy of them.
const std::vector<ParamOptionValue>& presetValues() {
    static const std::vector<ParamOptionValue> values = [] {
        std::vector<ParamOptionValue> list;
        list.reserve(static_cast<size_t>(chipgoodizer::kParamCount) * chipgoodizer::kPresetCount);
        for (int p = 0; p < chipgoodizer::kPresetCount; ++p) {
            const chipgoodizer::ChipgoodizerConfig preset =
                    chipgoodizer::ChipgoodizerConfig::getPreset(chipgoodizer::kPresetSpecs[p].preset);
            for (int i = 0; i < chipgoodizer::kParamCount; ++i) {
                list.push_back(ParamOptionValue {
                        chipgoodizer::kParamSpecs[i].id,
                        chipgoodizer::getParam(preset, static_cast<chipgoodizer::ParamId>(i))
                });
            }
        }
        return list;
    }();
    return values;
}

// The C API owns the parameter table; surface it verbatim so the descriptor
// stays in sync with the engine. The preset selector is prepended separately.
const std::vector<ParamDescriptor>& effectParams() {
    static const std::vector<ParamDescriptor> params = [] {
        std::vector<ParamDescriptor> list;
        ParamDescriptor preset {};
        preset.id = "preset";
        preset.label = "Preset";
        preset.type = ParamType::Enum;
        preset.minValue = 0.0f;
        // The trailing "Custom" option sits one past the engine's presets.
        preset.maxValue = static_cast<float>(chipgoodizer::kPresetCount);
        preset.defaultValue = 3.0f; // Punchy Club, the flagship preset.
        preset.step = 1.0f;
        preset.enumCount = chipgoodizer::kPresetCount + 1;
        preset.optionValues = presetValues().data();
        preset.optionValueCount = chipgoodizer::kParamCount;
        preset.optionCount = chipgoodizer::kPresetCount;
        list.push_back(preset);
        const int count = cg_param_count();
        list.reserve(static_cast<size_t>(count) + 1);
        for (int i = 0; i < count; ++i) {
            const cg_param_info* info = cg_param_at(i);
            if (!info || !info->id) {
                continue;
            }
            ParamDescriptor descriptor {};
            descriptor.id = info->id;
            descriptor.label = info->label;
            descriptor.type = static_cast<ParamType>(info->type);
            descriptor.minValue = info->min_value;
            descriptor.maxValue = info->max_value;
            descriptor.defaultValue = info->default_value;
            descriptor.step = info->step;
            list.push_back(descriptor);
        }
        return list;
    }();
    return params;
}

IAudioEffect* createChipgoodizerEffect() {
    return new ChipgoodizerEffect();
}

} // namespace

const EffectDescriptor& ChipgoodizerEffect::descriptor() {
    static const EffectDescriptor desc {
            "chipgoodizer",
            "Chipgoodizer",
            1,
            effectParams().data(),
            static_cast<int>(effectParams().size()),
            false,
            &createChipgoodizerEffect,
    };
    return desc;
}

ChipgoodizerEffect::ChipgoodizerEffect() {
    handle = cg_create();
}

ChipgoodizerEffect::~ChipgoodizerEffect() {
    cg_destroy(handle);
}

void ChipgoodizerEffect::prepare(int sampleRate, int channels) {
    cg_prepare(handle, sampleRate, channels);
}

void ChipgoodizerEffect::reset() {
    cg_reset(handle);
}

void ChipgoodizerEffect::process(float* interleaved, int frames, int channels) {
    if (!interleaved || frames <= 0 || channels <= 0) {
        return;
    }
    cg_process_interleaved(handle, interleaved, frames, channels);
}

void ChipgoodizerEffect::setParam(int index, float value) {
    if (index == 0) {
        const int preset = static_cast<int>(value);
        // "Custom" keeps the knobs the user dialed in.
        if (preset >= 0 && preset < chipgoodizer::kPresetCount) {
            cg_apply_preset(handle, static_cast<cg_preset>(preset));
        }
        return;
    }
    cg_set_param(handle, index - 1, value);
}

} // namespace siliconplayer::effects
