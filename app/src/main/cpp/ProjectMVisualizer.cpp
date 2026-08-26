#include "ProjectMVisualizer.h"

#include <algorithm>
#include <cstdlib>
#include <dirent.h>

namespace {

bool hasMilkExtension(const std::string& name) {
    return name.size() > 5 && name.compare(name.size() - 5, 5, ".milk") == 0;
}

} // namespace

ProjectMVisualizer::ProjectMVisualizer(silicon::vis::IVisualizationAudioProvider* audioProvider)
    : audioProvider_(audioProvider) {
    presetStartedAt_ = std::chrono::steady_clock::now();
}

bool ProjectMVisualizer::initGl() {
    if (instance_) return true;
    instance_ = projectm_create();
    if (!instance_) return false;

    projectm_set_preset_duration(instance_, presetDurationSeconds_);
    projectm_set_preset_locked(instance_, false);
    if (widthPx_ > 0 && heightPx_ > 0) {
        projectm_set_window_size(instance_, static_cast<size_t>(widthPx_), static_cast<size_t>(heightPx_));
    }

    if (!presetFiles_.empty()) {
        loadPresetAt(0, false);
    } else {
        loadIdlePreset();
    }
    return true;
}

void ProjectMVisualizer::resize(int32_t widthPx, int32_t heightPx, float /*density*/) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    if (instance_) {
        projectm_set_window_size(instance_, static_cast<size_t>(widthPx), static_cast<size_t>(heightPx));
    }
}

void ProjectMVisualizer::render() {
    if (!instance_ && !initGl()) return;

    feedAudio();

    const auto now = std::chrono::steady_clock::now();
    const double elapsed = std::chrono::duration<double>(now - presetStartedAt_).count();
    if (!presetFiles_.empty() && elapsed >= presetDurationSeconds_) {
        nextPreset(true);
    }

    projectm_opengl_render_frame(instance_);
}

void ProjectMVisualizer::releaseGl() {
    if (instance_) {
        projectm_destroy(instance_);
        instance_ = nullptr;
    }
}

void ProjectMVisualizer::setPresetDirectory(const std::string& dir) {
    presetDir_ = dir;
    presetFiles_.clear();
    presetIndex_ = 0;
    if (dir.empty()) return;

    DIR* dp = opendir(dir.c_str());
    if (!dp) return;
    while (dirent* entry = readdir(dp)) {
        std::string name = entry->d_name;
        if (hasMilkExtension(name)) {
            presetFiles_.push_back(dir + "/" + name);
        }
    }
    closedir(dp);
    std::sort(presetFiles_.begin(), presetFiles_.end());

    if (instance_) {
        if (!presetFiles_.empty()) {
            loadPresetAt(0, true);
        } else {
            loadIdlePreset();
        }
    }
}

void ProjectMVisualizer::nextPreset(bool smoothTransition) {
    if (presetFiles_.empty()) {
        loadIdlePreset();
        return;
    }
    const size_t next = presetFiles_.empty() ? 0 : (presetIndex_ + 1) % presetFiles_.size();
    loadPresetAt(next, smoothTransition);
}

void ProjectMVisualizer::loadPresetAt(size_t index, bool smoothTransition) {
    if (!instance_ || presetFiles_.empty()) return;
    presetIndex_ = index % presetFiles_.size();
    projectm_load_preset_file(instance_, presetFiles_[presetIndex_].c_str(), smoothTransition);
    presetStartedAt_ = std::chrono::steady_clock::now();
}

void ProjectMVisualizer::loadIdlePreset() {
    if (!instance_) return;
    projectm_load_preset_file(instance_, "idle://", false);
    presetStartedAt_ = std::chrono::steady_clock::now();
}

void ProjectMVisualizer::feedAudio() {
    if (!audioProvider_) return;
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
