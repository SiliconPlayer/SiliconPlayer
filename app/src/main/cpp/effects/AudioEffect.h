#ifndef SILICONPLAYER_EFFECTS_AUDIO_EFFECT_H
#define SILICONPLAYER_EFFECTS_AUDIO_EFFECT_H

namespace siliconplayer::effects {

enum class ParamType { Bool, Int, Float, Enum };

struct ParamDescriptor {
    const char* id;
    const char* label;
    ParamType type;
    float minValue;
    float maxValue;
    float defaultValue;
    float step;
    int enumCount = 0;
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
