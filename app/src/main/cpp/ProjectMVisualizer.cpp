#include "ProjectMVisualizer.h"

#include <algorithm>
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

    maxPcmFeedFrames_ = projectm_pcm_get_max_samples();
    projectm_set_preset_duration(instance_, presetDurationSeconds_);
    projectm_set_preset_locked(instance_, false);
    if (widthPx_ > 0 && heightPx_ > 0) {
        projectm_set_window_size(instance_, static_cast<size_t>(widthPx_), static_cast<size_t>(heightPx_));
    }

    if (!presetFiles_.empty()) {
        size_t startIndex = 0;
        if (!startPresetRelative_.empty()) {
            const auto found = std::find(presetFiles_.begin(), presetFiles_.end(), startPresetRelative_);
            if (found != presetFiles_.end()) {
                startIndex = static_cast<size_t>(found - presetFiles_.begin());
            }
        }
        loadPresetAt(startIndex, false);
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

void ProjectMVisualizer::setStartPreset(const std::string& relativePath) {
    startPresetRelative_ = relativePath;
}

void ProjectMVisualizer::render() {
    if (!instance_ && !initGl()) return;

    drainCommands();
    feedAudio();

    const auto now = std::chrono::steady_clock::now();
    const double elapsed = std::chrono::duration<double>(now - presetStartedAt_).count();
    if (!presetLocked_ && !presetFiles_.empty() && elapsed >= presetDurationSeconds_) {
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
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingPresetCommands_.emplace_back(PresetCommand::Next, smoothTransition);
}

void ProjectMVisualizer::previousPreset(bool smoothTransition) {
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingPresetCommands_.emplace_back(PresetCommand::Previous, smoothTransition);
}

void ProjectMVisualizer::setPresetLocked(bool locked) {
    presetLocked_.store(locked, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(commandMutex_);
    pendingLockCommand_ = {true, locked};
}

bool ProjectMVisualizer::isPresetLocked() const {
    return presetLocked_.load(std::memory_order_relaxed);
}

std::string ProjectMVisualizer::currentPresetName() const {
    std::lock_guard<std::mutex> lock(commandMutex_);
    return currentPresetName_;
}

void ProjectMVisualizer::drainCommands() {
    std::vector<std::pair<PresetCommand, bool>> commands;
    std::pair<bool, bool> lockCommand = {false, false};
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        commands.swap(pendingPresetCommands_);
        lockCommand = pendingLockCommand_;
        pendingLockCommand_.first = false;
    }

    if (lockCommand.first && instance_) {
        projectm_set_preset_locked(instance_, lockCommand.second);
    }

    for (const auto& [command, smooth] : commands) {
        if (presetFiles_.empty()) {
            loadIdlePreset();
            continue;
        }
        if (command == PresetCommand::Next) {
            loadPresetAt((presetIndex_ + 1) % presetFiles_.size(), smooth);
        } else {
            loadPresetAt((presetIndex_ + presetFiles_.size() - 1) % presetFiles_.size(), smooth);
        }
    }
}

void ProjectMVisualizer::loadPresetAt(size_t index, bool smoothTransition) {
    if (!instance_ || presetFiles_.empty()) return;
    presetIndex_ = index % presetFiles_.size();
    const std::string& path = presetFiles_[presetIndex_];
    projectm_load_preset_file(instance_, path.c_str(), smoothTransition);

    std::string name = path;
    const size_t slash = name.find_last_of('/');
    if (slash != std::string::npos) name = name.substr(slash + 1);
    if (name.size() > 5 && name.compare(name.size() - 5, 5, ".milk") == 0) {
        name.erase(name.size() - 5);
    }
    std::replace(name.begin(), name.end(), '_', ' ');
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        currentPresetName_ = name;
    }
    presetStartedAt_ = std::chrono::steady_clock::now();
}

void ProjectMVisualizer::loadIdlePreset() {
    if (!instance_) return;
    projectm_load_preset_file(instance_, "idle://", false);
    {
        std::lock_guard<std::mutex> lock(commandMutex_);
        currentPresetName_ = "Idle";
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
