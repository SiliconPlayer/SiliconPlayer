#ifndef SILICONPLAYER_EFFECTS_AUDIO_EFFECT_CHAIN_H
#define SILICONPLAYER_EFFECTS_AUDIO_EFFECT_CHAIN_H

#include "AudioEffect.h"

#include <atomic>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace siliconplayer::effects {

struct NodeOptions {
    bool mono = false;
};

struct EffectNodeSpec {
    std::string id;
    bool enabled = true;
    NodeOptions options;
    std::vector<float> params;
};

struct EffectChainSpec {
    NodeOptions inputOptions;
    NodeOptions masterOptions;
    std::vector<EffectNodeSpec> nodes;
};

EffectChainSpec parseEffectChainJson(std::string_view json);
std::string serializeEffectChainJson(const EffectChainSpec& spec);

class AudioEffectChain {
public:
    void prepare(int sampleRate, int channels);
    void reset();
    void process(float* buffer, int frames, int channels);
    void process(float* buffer, int frames, int channels, int sampleRate);

    // Caller must hold decoderMutex.
    void setSpec(const EffectChainSpec& spec, int sampleRate, int channels);
    bool updateIfStructurallyEqual(const EffectChainSpec& spec);
    const EffectChainSpec& spec() const { return currentSpec; }

    double latencyFrames() const { return cachedLatencyFrames.load(std::memory_order_relaxed); }

private:
    struct LiveNode {
        std::string id;
        bool enabled = true;
        std::atomic<bool> mono { false };
        std::unique_ptr<IAudioEffect> effect;

        LiveNode() = default;
        LiveNode(LiveNode&& other) noexcept
            : id(std::move(other.id)),
              enabled(other.enabled),
              mono(other.mono.load(std::memory_order_relaxed)),
              effect(std::move(other.effect)) {}
        LiveNode& operator=(LiveNode&& other) noexcept {
            if (this != &other) {
                id = std::move(other.id);
                enabled = other.enabled;
                mono.store(other.mono.load(std::memory_order_relaxed), std::memory_order_relaxed);
                effect = std::move(other.effect);
            }
            return *this;
        }
    };

    void refreshLatencyLocked();

    std::vector<LiveNode> liveNodes;
    EffectChainSpec currentSpec;
    int preparedSampleRate = 0;
    int preparedChannels = 0;
    std::atomic<bool> inputMono { false };
    std::atomic<bool> masterMono { false };
    std::atomic<double> cachedLatencyFrames { 0.0 };
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_AUDIO_EFFECT_CHAIN_H
