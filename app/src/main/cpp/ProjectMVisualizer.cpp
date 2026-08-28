#include "ProjectMVisualizer.h"

#include "gl/gl_primitives.h"

#include <algorithm>
#include <cstdlib>
#include <cmath>
#include <dirent.h>
#include <sys/stat.h>

namespace {

static const char* BLIT_VERTEX_SHADER = R"(
    precision mediump float;
    attribute vec2 aPosition;
    attribute vec2 aTexCoord;
    uniform vec2 uResolution;
    varying vec2 vTexCoord;
    void main() {
        vTexCoord = aTexCoord;
        vec2 zeroToOne = aPosition / uResolution;
        vec2 zeroToTwo = zeroToOne * 2.0;
        vec2 clipSpace = zeroToTwo - 1.0;
        gl_Position = vec4(clipSpace.x, -clipSpace.y, 0.0, 1.0);
    }
)";

static const char* BLIT_FRAGMENT_SHADER = R"(
    precision mediump float;
    varying vec2 vTexCoord;
    uniform sampler2D uSampler;
    void main() {
        gl_FragColor = texture2D(uSampler, vTexCoord);
    }
)";

bool hasMilkExtension(const std::string& name) {
    return name.size() > 5 && name.compare(name.size() - 5, 5, ".milk") == 0;
}

void scanPresetsRecursive(const std::string& dir, const std::string& prefix,
                          std::vector<std::string>& out) {
    DIR* dp = opendir(dir.c_str());
    if (!dp) return;
    while (dirent* entry = readdir(dp)) {
        std::string name = entry->d_name;
        if (name == "." || name == "..") continue;
        std::string relative = prefix.empty() ? name : prefix + "/" + name;
        std::string full = dir + "/" + name;
        struct stat st {};
        if (stat(full.c_str(), &st) != 0) continue;
        if (S_ISDIR(st.st_mode)) {
            scanPresetsRecursive(full, relative, out);
        } else if (hasMilkExtension(name)) {
            out.push_back(relative);
        }
    }
    closedir(dp);
}

} // namespace

ProjectMVisualizer::ProjectMVisualizer(silicon::vis::IVisualizationAudioProvider* audioProvider)
    : audioProvider_(audioProvider) {
    presetStartedAt_ = std::chrono::steady_clock::now();
}

std::string ProjectMVisualizer::makeKey(const std::string& setId, const std::string& relativePath) {
    return setId + kKeySeparator + relativePath;
}

std::string ProjectMVisualizer::dirForSet(const std::string& setId) const {
    for (const auto& set : sets_) {
        if (set.first == setId) return set.second;
    }
    return {};
}

std::string ProjectMVisualizer::displayNameFor(const std::string& relativePath) const {
    std::string name = relativePath;
    const size_t slash = name.find_last_of('/');
    if (slash != std::string::npos) name = name.substr(slash + 1);
    if (name.size() > 5 && name.compare(name.size() - 5, 5, ".milk") == 0) {
        name.erase(name.size() - 5);
    }
    std::replace(name.begin(), name.end(), '_', ' ');
    return name;
}

bool ProjectMVisualizer::initGl() {
    if (instance_) return true;
    instance_ = projectm_create();
    if (!instance_) return false;

    maxPcmFeedFrames_ = projectm_pcm_get_max_samples();
    // Apply settings staged from another thread before configuring the instance.
    applyPendingSettings();
    projectm_set_preset_duration(instance_, presetDurationSeconds_);
    projectm_set_preset_locked(instance_, false);
    projectm_set_hard_cut_enabled(instance_, hardCutEnabled_);
    projectm_set_hard_cut_sensitivity(instance_, hardCutSensitivity_);
    projectm_set_mesh_size(instance_, static_cast<size_t>(meshSize_), static_cast<size_t>(meshSize_));
    projectm_set_aspect_correction(instance_, aspectCorrection_);
    projectm_set_fps(instance_, fps_);
    if (widthPx_ > 0 && heightPx_ > 0) {
        recomputeRenderSize();
        projectm_set_window_size(instance_, static_cast<size_t>(renderWidth_), static_cast<size_t>(renderHeight_));
    }

    if (!presets_.empty()) {
        size_t startIndex = 0;
        if (!startPresetKey_.empty()) {
            const auto found = std::find(presetKeys_.begin(), presetKeys_.end(), startPresetKey_);
            if (found != presetKeys_.end()) {
                startIndex = static_cast<size_t>(found - presetKeys_.begin());
            }
        }
        loadPresetAt(startIndex, false);
    } else {
        loadIdlePreset();
    }
    return true;
}

void ProjectMVisualizer::setMaxResolutionPx(int maxLongEdgePx) {
    if (maxLongEdgePx < 0) maxLongEdgePx = 0;
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingSettings_.maxResolutionLongEdge = maxLongEdgePx;
    pendingSettings_.maxResolutionDirty = true;
    pendingSettingsDirty_.store(true, std::memory_order_relaxed);
}

void ProjectMVisualizer::applyPendingSettings() {
    if (!pendingSettingsDirty_.exchange(false, std::memory_order_relaxed)) {
        return;
    }
    PendingSettings pending;
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        pending = pendingSettings_;
        pendingSettings_ = PendingSettings{};
    }
    if (pending.presetDurationDirty) {
        presetDurationSeconds_ = pending.presetDurationSeconds;
        if (instance_) projectm_set_preset_duration(instance_, presetDurationSeconds_);
    }
    if (pending.hardCutEnabledDirty) {
        hardCutEnabled_ = pending.hardCutEnabled;
        if (instance_) projectm_set_hard_cut_enabled(instance_, hardCutEnabled_);
    }
    if (pending.hardCutSensitivityDirty) {
        hardCutSensitivity_ = pending.hardCutSensitivity;
        if (instance_) projectm_set_hard_cut_sensitivity(instance_, hardCutSensitivity_);
    }
    if (pending.meshSizeDirty) {
        meshSize_ = pending.meshSize;
        if (instance_) projectm_set_mesh_size(instance_, static_cast<size_t>(meshSize_), static_cast<size_t>(meshSize_));
    }
    if (pending.aspectCorrectionDirty) {
        aspectCorrection_ = pending.aspectCorrection;
        if (instance_) projectm_set_aspect_correction(instance_, aspectCorrection_);
    }
    if (pending.fpsDirty) {
        fps_ = pending.fps;
        if (instance_) projectm_set_fps(instance_, fps_);
    }
    if (pending.rotationRandomDirty) {
        rotationRandom_ = pending.rotationRandom;
    }
    if (pending.maxResolutionDirty) {
        maxResolutionLongEdge_ = pending.maxResolutionLongEdge;
        renderSizeDirty_ = true;
        if (instance_ && widthPx_ > 0 && heightPx_ > 0) {
            recomputeRenderSize();
            if (renderWidth_ > 0 && renderHeight_ > 0) {
                projectm_set_window_size(instance_, static_cast<size_t>(renderWidth_), static_cast<size_t>(renderHeight_));
            }
        }
    }
}

void ProjectMVisualizer::recomputeRenderSize() {
    renderSizeDirty_ = false;
    if (widthPx_ <= 0 || heightPx_ <= 0) {
        renderWidth_ = 0;
        renderHeight_ = 0;
        return;
    }
    if (maxResolutionLongEdge_ <= 0) {
        // Native: render at the surface resolution.
        renderWidth_ = widthPx_;
        renderHeight_ = heightPx_;
        return;
    }
    const float surfaceLong = static_cast<float>(std::max(widthPx_, heightPx_));
    const int longEdge = std::min(static_cast<int>(surfaceLong), maxResolutionLongEdge_);
    const float scale = surfaceLong > 0.0f ? static_cast<float>(longEdge) / surfaceLong : 1.0f;
    int rw = static_cast<int>(std::lround(static_cast<float>(widthPx_) * scale));
    int rh = static_cast<int>(std::lround(static_cast<float>(heightPx_) * scale));
    renderWidth_ = std::clamp(rw, 1, widthPx_);
    renderHeight_ = std::clamp(rh, 1, heightPx_);
}

void ProjectMVisualizer::resize(int32_t widthPx, int32_t heightPx, float /*density*/) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    recomputeRenderSize();
    if (instance_) {
        projectm_set_window_size(instance_, static_cast<size_t>(renderWidth_), static_cast<size_t>(renderHeight_));
    }
}

void ProjectMVisualizer::ensureOffscreenTarget(int32_t w, int32_t h) {
    if (offscreenInited_ && offscreenWidth_ == w && offscreenHeight_ == h) return;
    releaseOffscreen();
    if (w <= 0 || h <= 0) return;

    glGenTextures(1, &offscreenTex_);
    glBindTexture(GL_TEXTURE_2D, offscreenTex_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);

    glGenFramebuffers(1, &offscreenFbo_);
    glBindFramebuffer(GL_FRAMEBUFFER, offscreenFbo_);
    glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, offscreenTex_, 0);
    const GLenum status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (status != GL_FRAMEBUFFER_COMPLETE) {
        releaseOffscreen();
        return;
    }

    if (!blitProgram_.isReady()) {
        if (!blitProgram_.compileAndLink(BLIT_VERTEX_SHADER, BLIT_FRAGMENT_SHADER)) {
            releaseOffscreen();
            return;
        }
        blitResLoc_ = blitProgram_.getUniformLoc("uResolution");
        blitPosLoc_ = blitProgram_.getAttribLoc("aPosition");
        blitCoordLoc_ = blitProgram_.getAttribLoc("aTexCoord");
        blitSamplerLoc_ = blitProgram_.getUniformLoc("uSampler");
    }

    offscreenWidth_ = w;
    offscreenHeight_ = h;
    offscreenInited_ = true;
}

void ProjectMVisualizer::releaseOffscreen() {
    if (offscreenFbo_ != 0) {
        glDeleteFramebuffers(1, &offscreenFbo_);
        offscreenFbo_ = 0;
    }
    if (offscreenTex_ != 0) {
        glDeleteTextures(1, &offscreenTex_);
        offscreenTex_ = 0;
    }
    offscreenWidth_ = 0;
    offscreenHeight_ = 0;
    offscreenInited_ = false;
}

void ProjectMVisualizer::blitOffscreenToSurface() {
    if (!offscreenInited_ || offscreenTex_ == 0 || !blitProgram_.isReady()) return;
    if (widthPx_ <= 0 || heightPx_ <= 0) return;

    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    blitProgram_.use();
    glUniform2f(blitResLoc_, static_cast<float>(widthPx_), static_cast<float>(heightPx_));
    glUniform1i(blitSamplerLoc_, 0);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, offscreenTex_);

    float quad[24];
    // projectM renders into this offscreen texture with the visual top at v=1
    // (render-to-target convention), unlike bitmap textures, so V is flipped here
    // to present the scene upright on the surface.
    silicon::vis::gl::GlPrimitives::generateTexturedQuad(
        0.0f, 0.0f, static_cast<float>(widthPx_), static_cast<float>(heightPx_),
        0.0f, 1.0f, 1.0f, 0.0f, quad);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glEnableVertexAttribArray(blitPosLoc_);
    glVertexAttribPointer(blitPosLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), quad);
    glEnableVertexAttribArray(blitCoordLoc_);
    glVertexAttribPointer(blitCoordLoc_, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), &quad[2]);

    glDrawArrays(GL_TRIANGLES, 0, 6);

    glDisableVertexAttribArray(blitPosLoc_);
    glDisableVertexAttribArray(blitCoordLoc_);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void ProjectMVisualizer::render() {
    if (!instance_ && !initGl()) return;

    applyPendingSettings();

    drainCommands();
    feedAudio();

    const auto now = std::chrono::steady_clock::now();
    const double elapsed = std::chrono::duration<double>(now - presetStartedAt_).count();
    if (!presetLocked_ && !presets_.empty() && elapsed >= presetDurationSeconds_) {
        nextPreset(true);
    }

    const bool useOffscreen = renderWidth_ > 0 && renderHeight_ > 0 &&
                              (renderWidth_ != widthPx_ || renderHeight_ != heightPx_);
    if (useOffscreen) {
        ensureOffscreenTarget(renderWidth_, renderHeight_);
        if (offscreenInited_) {
            // Render projectM into the offscreen target at the capped size.
            glBindFramebuffer(GL_FRAMEBUFFER, offscreenFbo_);
            glViewport(0, 0, renderWidth_, renderHeight_);
            glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            glClear(GL_COLOR_BUFFER_BIT);
            projectm_opengl_render_frame_fbo(instance_, offscreenFbo_);

            // Blit the capped texture scaled up to fill the full surface.
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            glViewport(0, 0, widthPx_, heightPx_);
            blitOffscreenToSurface();
            return;
        }
    }

    projectm_opengl_render_frame(instance_);
}

void ProjectMVisualizer::releaseGl() {
    releaseOffscreen();
    if (instance_) {
        projectm_destroy(instance_);
        instance_ = nullptr;
    }
}

void ProjectMVisualizer::setPresetSets(const std::vector<std::pair<std::string, std::string>>& sets) {
    sets_ = sets;
    scanPresetSets();

    if (instance_) {
        if (!presets_.empty()) {
            loadPresetAt(0, true);
        } else {
            loadIdlePreset();
        }
    }
}

void ProjectMVisualizer::setPresetKeys(const std::vector<std::pair<std::string, std::string>>& sets,
                                       const std::vector<std::string>& presetKeys) {
    {
        std::lock_guard<std::mutex> lock(presetListMutex_);
        if (sets_ == sets && presetKeys_ == presetKeys) return;
        sets_ = sets;
        presets_.clear();
        presetKeys_.clear();
        presetSetIds_.clear();
        presetIndex_ = 0;
        for (const auto& key : presetKeys) {
            size_t sep = key.find(kKeySeparator);
            if (sep == std::string::npos) continue;
            std::string setId = key.substr(0, sep);
            std::string rel = key.substr(sep + 1);
            bool known = false;
            for (const auto& s : sets_) if (s.first == setId) { known = true; break; }
            if (!known) continue;
            presets_.push_back({setId, rel});
        }
        std::sort(presets_.begin(), presets_.end(),
                  [](const PresetEntry& a, const PresetEntry& b) {
                      if (a.setId != b.setId) return a.setId < b.setId;
                      return a.relativePath < b.relativePath;
                  });
        presetKeys_.reserve(presets_.size());
        presetSetIds_.reserve(presets_.size());
        for (const auto& e : presets_) {
            presetKeys_.push_back(makeKey(e.setId, e.relativePath));
            presetSetIds_.push_back(e.setId);
        }
    }
    if (instance_) {
        if (!presets_.empty()) loadPresetAt(0, true);
        else loadIdlePreset();
    }
}

void ProjectMVisualizer::setStartPreset(const std::string& presetKey) {
    startPresetKey_ = presetKey;
}

void ProjectMVisualizer::scanPresetSets() {
    {
        std::lock_guard<std::mutex> lock(presetListMutex_);
        presets_.clear();
        presetKeys_.clear();
        presetSetIds_.clear();
        presetIndex_ = 0;
        if (sets_.empty()) return;

        for (const auto& set : sets_) {
            std::vector<std::string> relativePaths;
            scanPresetsRecursive(set.second, "", relativePaths);
            for (const auto& relative : relativePaths) {
                presets_.push_back({set.first, relative});
            }
        }

        std::sort(presets_.begin(), presets_.end(),
                  [](const PresetEntry& a, const PresetEntry& b) {
                      if (a.setId != b.setId) return a.setId < b.setId;
                      return a.relativePath < b.relativePath;
                  });
        presetKeys_.reserve(presets_.size());
        presetSetIds_.reserve(presets_.size());
        for (const auto& entry : presets_) {
            presetKeys_.push_back(makeKey(entry.setId, entry.relativePath));
            presetSetIds_.push_back(entry.setId);
        }
    }
}

void ProjectMVisualizer::loadPresetKey(const std::string& presetKey, bool smoothTransition) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingCommands_.push_back({PresetCommand::Type::Load, smoothTransition, presetKey});
}

void ProjectMVisualizer::nextPreset(bool smoothTransition) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingCommands_.push_back({PresetCommand::Type::Next, smoothTransition, ""});
}

void ProjectMVisualizer::previousPreset(bool smoothTransition) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingCommands_.push_back({PresetCommand::Type::Previous, smoothTransition, ""});
}

void ProjectMVisualizer::setPresetLocked(bool locked) {
    presetLocked_.store(locked, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingLockCommand_ = {true, locked};
}

bool ProjectMVisualizer::isPresetLocked() const {
    return presetLocked_.load(std::memory_order_relaxed);
}

void ProjectMVisualizer::setPresetDuration(double seconds) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingSettings_.presetDurationSeconds = seconds;
    pendingSettings_.presetDurationDirty = true;
    pendingSettingsDirty_.store(true, std::memory_order_relaxed);
}

void ProjectMVisualizer::setHardCutEnabled(bool enabled) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingSettings_.hardCutEnabled = enabled;
    pendingSettings_.hardCutEnabledDirty = true;
    pendingSettingsDirty_.store(true, std::memory_order_relaxed);
}

void ProjectMVisualizer::setHardCutSensitivity(float sensitivity) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingSettings_.hardCutSensitivity = sensitivity;
    pendingSettings_.hardCutSensitivityDirty = true;
    pendingSettingsDirty_.store(true, std::memory_order_relaxed);
}

void ProjectMVisualizer::setRotationRandom(bool random) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingSettings_.rotationRandom = random;
    pendingSettings_.rotationRandomDirty = true;
    pendingSettingsDirty_.store(true, std::memory_order_relaxed);
}

void ProjectMVisualizer::setMeshSize(int size) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingSettings_.meshSize = size;
    pendingSettings_.meshSizeDirty = true;
    pendingSettingsDirty_.store(true, std::memory_order_relaxed);
}

void ProjectMVisualizer::setAspectCorrection(bool enabled) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingSettings_.aspectCorrection = enabled;
    pendingSettings_.aspectCorrectionDirty = true;
    pendingSettingsDirty_.store(true, std::memory_order_relaxed);
}

void ProjectMVisualizer::setFps(int fps) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingSettings_.fps = fps;
    pendingSettings_.fpsDirty = true;
    pendingSettingsDirty_.store(true, std::memory_order_relaxed);
}

std::string ProjectMVisualizer::currentPresetName() const {
    std::lock_guard<std::mutex> lock(commandMutex_);
    return currentPresetName_;
}

std::string ProjectMVisualizer::currentPresetKey() const {
    std::lock_guard<std::mutex> lock(commandMutex_);
    return currentPresetKey_;
}

std::vector<std::string> ProjectMVisualizer::presetKeys() const {
    std::lock_guard<std::mutex> lock(presetListMutex_);
    return presetKeys_;
}

std::vector<std::string> ProjectMVisualizer::presetSetIds() const {
    std::lock_guard<std::mutex> lock(presetListMutex_);
    return presetSetIds_;
}

void ProjectMVisualizer::drainCommands() {
    std::vector<PresetCommand> commands;
    std::pair<bool, bool> lockCommand = {false, false};
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        commands.swap(pendingCommands_);
        lockCommand = pendingLockCommand_;
        pendingLockCommand_.first = false;
    }

    if (lockCommand.first && instance_) {
        projectm_set_preset_locked(instance_, lockCommand.second);
    }

    for (const auto& command : commands) {
        if (command.type == PresetCommand::Type::Load) {
            const auto found = std::find(presetKeys_.begin(), presetKeys_.end(), command.key);
            if (found != presetKeys_.end()) {
                loadPresetAt(static_cast<size_t>(found - presetKeys_.begin()), command.smooth);
            }
            continue;
        }
        if (presets_.empty()) {
            loadIdlePreset();
            continue;
        }
        if (rotationRandom_ && presets_.size() > 1) {
            size_t next = presetIndex_;
            while (next == presetIndex_) next = static_cast<size_t>(rand()) % presets_.size();
            loadPresetAt(next, command.smooth);
        } else if (command.type == PresetCommand::Type::Next) {
            loadPresetAt((presetIndex_ + 1) % presets_.size(), command.smooth);
        } else {
            loadPresetAt((presetIndex_ + presets_.size() - 1) % presets_.size(), command.smooth);
        }
    }
}

void ProjectMVisualizer::loadPresetAt(size_t index, bool smoothTransition) {
    if (!instance_ || presets_.empty()) return;
    presetIndex_ = index % presets_.size();
    const PresetEntry& entry = presets_[presetIndex_];
    const std::string setDir = dirForSet(entry.setId);
    const std::string fullPath = setDir.empty() ? entry.relativePath : setDir + "/" + entry.relativePath;
    projectm_load_preset_file(instance_, fullPath.c_str(), smoothTransition);

    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        currentPresetName_ = displayNameFor(entry.relativePath);
        currentPresetKey_ = makeKey(entry.setId, entry.relativePath);
    }
    presetStartedAt_ = std::chrono::steady_clock::now();
}

void ProjectMVisualizer::loadIdlePreset() {
    if (!instance_) return;
    projectm_load_preset_file(instance_, "idle://", false);
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        currentPresetName_ = "Idle";
        currentPresetKey_.clear();
    }
    presetStartedAt_ = std::chrono::steady_clock::now();
}

void ProjectMVisualizer::feedAudio() {
    if (!audioProvider_ || !instance_) return;

    if (audioProvider_->getNewPcmMono(static_cast<int32_t>(maxPcmFeedFrames_), audioBuffer_)) {
        if (!audioBuffer_.empty()) {
            projectm_pcm_add_float(
                instance_,
                audioBuffer_.data(),
                static_cast<unsigned int>(audioBuffer_.size()),
                PROJECTM_MONO
            );
        }
        return;
    }

    audioProvider_->getWaveformScope(0, 34, 0, audioBuffer_);
    if (!audioBuffer_.empty()) {
        projectm_pcm_add_float(
            instance_,
            audioBuffer_.data(),
            static_cast<unsigned int>(audioBuffer_.size()),
            PROJECTM_MONO
        );
    }
}
