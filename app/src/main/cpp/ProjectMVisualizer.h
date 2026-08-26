#pragma once

#include "silicon/vis/IVisualizerRenderer.h"
#include "silicon/vis/IVisualizationAudioProvider.h"
#include "projectM-4/projectM.h"

#include <atomic>
#include <chrono>
#include <mutex>
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
    void setStartPreset(const std::string& relativePath);
    void nextPreset(bool smoothTransition);
    void previousPreset(bool smoothTransition);
    void loadPresetRelative(const std::string& relativePath, bool smoothTransition);
    void setPresetLocked(bool locked);
    bool isPresetLocked() const;
    std::string currentPresetName() const;
    std::string currentPresetRelative() const;
    const std::vector<std::string>& presetFiles() const { return presetFiles_; }

private:
    struct PresetCommand {
        enum class Type { Next, Previous, Load };
        Type type;
        bool smooth;
        std::string path;
    };
    void loadPresetAt(size_t index, bool smoothTransition);
    void loadIdlePreset();
    void feedAudio();
    void drainCommands();
    void scanPresetDirectory();

    silicon::vis::IVisualizationAudioProvider* audioProvider_;
    projectm_handle instance_ = nullptr;
    unsigned int maxPcmFeedFrames_ = 2048;
    std::string presetDir_;
    std::string startPresetRelative_;
    std::vector<std::string> presetFiles_;
    size_t presetIndex_ = 0;
    std::string currentPresetRelative_;
    std::string currentPresetName_;
    double presetDurationSeconds_ = 25.0;
    std::atomic<bool> presetLocked_ { false };

    mutable std::mutex commandMutex_;
    std::vector<PresetCommand> pendingCommands_;
    std::pair<bool, bool> pendingLockCommand_ = {false, false};
    std::chrono::steady_clock::time_point presetStartedAt_{};
    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    std::vector<float> audioBuffer_;
};
