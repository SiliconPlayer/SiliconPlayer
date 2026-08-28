#pragma once

#include "silicon/vis/IVisualizerRenderer.h"
#include "silicon/vis/IVisualizationAudioProvider.h"
#include "gl/gl_program.h"
#include "projectM-4/projectM.h"

#include <atomic>
#include <chrono>
#include <mutex>
#include <string>
#include <utility>
#include <vector>

/**
 * silicon_vis plugin renderer driving a libprojectM instance; preset rotation
 * is managed here since libprojectM core has no playlist support.
 *
 * Presets come from a set of directories ("preset sets"). Each preset is
 * identified by a key "<setId>\x1F<relativePath>" so same-named files in
 * different sets never collide. The set id and relative path together select
 * the source directory and the file within it.
 */
class ProjectMVisualizer : public silicon::vis::IVisualizerRenderer {
public:
    // Separator between set id and relative path in a preset key. Not a valid
    // path character, so keys are unambiguous.
    static constexpr char kKeySeparator = '\x1F';

    explicit ProjectMVisualizer(silicon::vis::IVisualizationAudioProvider* audioProvider);
    ~ProjectMVisualizer() override = default;

    SiliconVisMode getMode() const override { return SILICON_VIS_MODE_CUSTOM_PLUGIN; }
    const char* getName() const override { return "projectM"; }

    bool initGl() override;
    void resize(int32_t widthPx, int32_t heightPx, float density) override;
    void render() override;
    void releaseGl() override;

    // Ordered {setId, dir} pairs describing the enabled preset sets. Replacing
    // the set list rescans each directory and resets the current selection.
    void setPresetSets(const std::vector<std::pair<std::string, std::string>>& sets);
    void setPresetKeys(const std::vector<std::pair<std::string, std::string>>& sets,
                       const std::vector<std::string>& presetKeys);
    void setStartPreset(const std::string& presetKey);
    void nextPreset(bool smoothTransition);
    void previousPreset(bool smoothTransition);
    void loadPresetKey(const std::string& presetKey, bool smoothTransition);
    void setPresetLocked(bool locked);
    bool isPresetLocked() const;
    void setPresetDuration(double seconds);
    void setHardCutEnabled(bool enabled);
    void setHardCutSensitivity(float sensitivity);
    void setRotationRandom(bool random);
    void setMeshSize(int size);
    void setAspectCorrection(bool enabled);
    void setFps(int fps);
    // Cap projectM's internal render resolution. maxLongEdgePx is the maximum
    // of the render target's dimensions (0 = native screen resolution). When a
    // cap is active the projectM scene renders into an offscreen texture at a
    // capped resolution (preserving the device aspect ratio) and is then
    // upsampled to fill the surface; scrim/overlays are unaffected.
    void setMaxResolutionPx(int maxLongEdgePx);
    std::string currentPresetName() const;
    std::string currentPresetKey() const;
    std::vector<std::string> presetKeys() const;
    std::vector<std::string> presetSetIds() const;

private:
    struct PresetEntry {
        std::string setId;
        std::string relativePath;
    };

    struct PresetCommand {
        enum class Type { Next, Previous, Load };
        Type type;
        bool smooth;
        std::string key;
    };

    // projectM is not thread-safe; setters can be invoked from any thread.
    // Values are staged here and applied on the GL thread by applyPendingSettings().
    struct PendingSettings {
        bool presetDurationDirty = false;
        double presetDurationSeconds = 25.0;
        bool hardCutEnabledDirty = false;
        bool hardCutEnabled = true;
        bool hardCutSensitivityDirty = false;
        float hardCutSensitivity = 1.0f;
        bool meshSizeDirty = false;
        int meshSize = 48;
        bool aspectCorrectionDirty = false;
        bool aspectCorrection = true;
        bool fpsDirty = false;
        int fps = 30;
        bool rotationRandomDirty = false;
        bool rotationRandom = false;
        bool maxResolutionDirty = false;
        int maxResolutionLongEdge = 0;
    };

    void applyPendingSettings();
    void scanPresetSets();
    void loadPresetAt(size_t index, bool smoothTransition);
    void loadIdlePreset();
    void feedAudio();
    void drainCommands();
    void recomputeRenderSize();
    void ensureOffscreenTarget(int32_t w, int32_t h);
    void blitOffscreenToSurface();
    void releaseOffscreen();
    std::string dirForSet(const std::string& setId) const;
    static std::string makeKey(const std::string& setId, const std::string& relativePath);
    std::string displayNameFor(const std::string& relativePath) const;

    silicon::vis::IVisualizationAudioProvider* audioProvider_;
    projectm_handle instance_ = nullptr;
    unsigned int maxPcmFeedFrames_ = 2048;
    std::vector<std::pair<std::string, std::string>> sets_;
    std::vector<PresetEntry> presets_;
    std::vector<std::string> presetKeys_;
    std::vector<std::string> presetSetIds_;
    size_t presetIndex_ = 0;
    std::string startPresetKey_;
    std::string currentPresetKey_;
    std::string currentPresetName_;
    double presetDurationSeconds_ = 25.0;
    std::atomic<bool> presetLocked_ { false };
    bool hardCutEnabled_ = true;
    float hardCutSensitivity_ = 1.0f;
    bool rotationRandom_ = false;
    int meshSize_ = 48;
    bool aspectCorrection_ = true;
    int fps_ = 30;

    // projectM is not thread-safe: values set from any thread are staged here and
    // applied on the GL thread in applyPendingSettings().
    PendingSettings pendingSettings_;
    std::atomic<bool> pendingSettingsDirty_ { false };

    // Render-resolution cap (max of render target dimensions; 0 = native).
    int maxResolutionLongEdge_ = 0;
    bool renderSizeDirty_ = false;
    int32_t renderWidth_ = 0;
    int32_t renderHeight_ = 0;

    // Offscreen render target for capped-resolution projectM output.
    bool offscreenInited_ = false;
    GLuint offscreenFbo_ = 0;
    GLuint offscreenTex_ = 0;
    int32_t offscreenWidth_ = 0;
    int32_t offscreenHeight_ = 0;
    silicon::vis::gl::GlProgram blitProgram_;
    GLint blitResLoc_ = -1;
    GLint blitPosLoc_ = -1;
    GLint blitCoordLoc_ = -1;
    GLint blitSamplerLoc_ = -1;

    mutable std::mutex commandMutex_;
    std::vector<PresetCommand> pendingCommands_;
    std::pair<bool, bool> pendingLockCommand_ = {false, false};
    // Guards preset-state vectors read by UI-thread getters during attach.
    mutable std::mutex presetListMutex_;
    std::chrono::steady_clock::time_point presetStartedAt_{};
    int32_t widthPx_ = 0;
    int32_t heightPx_ = 0;
    std::vector<float> audioBuffer_;
};
