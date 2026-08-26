#pragma once

#include "silicon/vis/IVisualizerRenderer.h"
#include "silicon/vis/IVisualizationAudioProvider.h"
#include "projectM-4/projectM.h"

#include <chrono>
#include <string>
#include <vector>

/**
 * silicon_vis plugin renderer driving a libprojectM instance; preset rotation
 * is managed here since libprojectM core has no playlist support.
 */
class ProjectMVisualizer : public silicon::vis::IVisualizerRenderer {
public:
    explicit ProjectMVisualizer(silicon::vis::IVisualizationAudioProvider* audioProvider);
    ~ProjectMVisualizer() override = default;

    SiliconVisMode getMode() const override { return SILICON_VIS_MODE_CUSTOM_PLUGIN; }
    const char* getName() const override { return "projectM"; }

    bool initGl() override;
    void resize(int32_t widthPx, int32_t heightPx, float density) override;
    void render() override;
    void releaseGl() override;

    void setPresetDirectory(const std::string& dir);
    void nextPreset(bool smoothTransition);

private:
    void loadPresetAt(size_t index, bool smoothTransition);
    void loadIdlePreset();
    void feedAudio();

    silicon::vis::IVisualizationAudioProvider* audioProvider_;
    projectm_handle instance_ = nullptr;
    unsigned int maxPcmFeedFrames_ = 2048;
    std::string presetDir_;
    std::vector<std::string> presetFiles_;
    size_t presetIndex_ = 0;
    double presetDurationSeconds_ = 25.0;
    std::chrono::steady_clock::time_point presetStartedAt_{};
    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    std::vector<float> audioBuffer_;
};
