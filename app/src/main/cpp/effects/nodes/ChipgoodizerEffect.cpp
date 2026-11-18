#include "ChipgoodizerEffect.h"

#include <vector>

namespace siliconplayer::effects {

namespace {

// The C API owns the parameter table; surface it verbatim so the descriptor
// stays in sync with the engine.
const std::vector<ParamDescriptor>& effectParams() {
    static const std::vector<ParamDescriptor> params = [] {
        std::vector<ParamDescriptor> list;
        const int count = cg_param_count();
        list.reserve(static_cast<size_t>(count));
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
    cg_set_param(handle, index, value);
}

} // namespace siliconplayer::effects
