#ifndef SILICONPLAYER_EFFECTS_EFFECT_REGISTRY_H
#define SILICONPLAYER_EFFECTS_EFFECT_REGISTRY_H

#include "AudioEffect.h"

#include <memory>
#include <string>
#include <string_view>
#include <vector>

namespace siliconplayer::effects {

class EffectRegistry {
public:
    static EffectRegistry& getInstance();

    void registerEffect(const EffectDescriptor& descriptor);
    const EffectDescriptor* findDescriptor(std::string_view id) const;
    std::unique_ptr<IAudioEffect> create(std::string_view id) const;
    const std::vector<EffectDescriptor>& descriptors() const { return registered; }
    std::string getEffectDescriptorsJson() const;

private:
    EffectRegistry();
    std::vector<EffectDescriptor> registered;
};

} // namespace siliconplayer::effects

#endif // SILICONPLAYER_EFFECTS_EFFECT_REGISTRY_H
