#ifndef SILICONPLAYER_EFFECTS_AUDIO_EFFECT_H
#define SILICONPLAYER_EFFECTS_AUDIO_EFFECT_H

namespace siliconplayer::effects {

enum class ParamType { Bool, Int, Float, Enum };

struct ParamOptionValue {
    const char* id;
    float value;
};

struct ParamDescriptor {
    const char* id;
    const char* label;
    ParamType type;
    float minValue;
    float maxValue;
    float defaultValue;
    float step;
    int enumCount = 0;
    // Knob values each enum option stands for, packed as
    // [option * optionValueCount + entry]. Lets the UI mirror the values the
    // engine ends up with after the option is picked.
    const ParamOptionValue* optionValues = nullptr;
    int optionValueCount = 0;
    int optionCount = 0;
};

class IAudioEffect {
public:
    virtual ~IAudioEffect() = default;
    virtual void prepare(int sampleRate, int channels) = 0;
    virtual void reset() = 0;
    virtual void process(float* interleaved, int frames, int channels) = 0;
    virtual void setParam(int index, float value) = 0;
    virtual double latencyFrames() const { return 0.0; }
};

struct EffectDescriptor {
    const char* id;
    const char* displayName;
    int schemaVersion;
    const ParamDescriptor* params;
    int paramCount;
    bool supportsDryWet;
    IAudioEffect* (*create)();
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_AUDIO_EFFECT_H
