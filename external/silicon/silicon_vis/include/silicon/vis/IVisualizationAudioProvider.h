#pragma once

#include "vis_types.h"
#include <vector>
#include <cstdint>

namespace silicon::vis {

class IVisualizationAudioProvider {
public:
    virtual ~IVisualizationAudioProvider() = default;

    virtual void getWaveformScope(int channelIndex, int windowMs, int triggerMode, std::vector<float>& out) = 0;
    virtual void getFftBars(std::vector<float>& out) = 0;
    virtual void getVuLevels(float& left, float& right) = 0;
    virtual int getChannelCount() = 0;
    virtual void getChannelScopeHistories(int samplesPerChannel, int presentationDelayFrames, std::vector<float>& flatOut, int& channelCount) = 0;
    virtual void getChannelScopeTextStates(int maxChannels, std::vector<int32_t>& flatOut) = 0;
};

} // namespace silicon::vis
