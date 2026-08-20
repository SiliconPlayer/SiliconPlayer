#pragma once

#include "vis_types.h"
#include <string>
#include <memory>

namespace silicon::vis {

/**
 * Interface that all visualizer renderers (built-in and 3rd-party plugins) must implement.
 */
class IVisualizerRenderer {
public:
    virtual ~IVisualizerRenderer() = default;

    virtual SiliconVisMode getMode() const = 0;
    virtual const char* getName() const = 0;

    virtual bool initGl() = 0;
    virtual void resize(int32_t widthPx, int32_t heightPx, float density = 1.0f) = 0;
    virtual void render() = 0;
    virtual void releaseGl() = 0;

    // Optional audio push for renderers that manage internal ring buffers
    virtual void pushPcm(const float* pcmInterleaved, int32_t frames, int32_t channels, int32_t sampleRate) {}
    virtual void pushFft(const float* magnitudes, int32_t binCount) {}
};

using VisualizerRendererPtr = std::unique_ptr<IVisualizerRenderer>;

} // namespace silicon::vis
