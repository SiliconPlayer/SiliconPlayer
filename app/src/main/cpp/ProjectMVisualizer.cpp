#include "ProjectMVisualizer.h"

#include <algorithm>
#include <dirent.h>
#include <sys/stat.h>

namespace {

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
    projectm_set_preset_duration(instance_, presetDurationSeconds_);
    projectm_set_preset_locked(instance_, false);
    if (widthPx_ > 0 && heightPx_ > 0) {
        projectm_set_window_size(instance_, static_cast<size_t>(widthPx_), static_cast<size_t>(heightPx_));
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

void ProjectMVisualizer::resize(int32_t widthPx, int32_t heightPx, float /*density*/) {
    widthPx_ = widthPx;
    heightPx_ = heightPx;
    if (instance_) {
        projectm_set_window_size(instance_, static_cast<size_t>(widthPx), static_cast<size_t>(heightPx));
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

void ProjectMVisualizer::setStartPreset(const std::string& presetKey) {
    startPresetKey_ = presetKey;
}

void ProjectMVisualizer::scanPresetSets() {
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

void ProjectMVisualizer::render() {
    if (!instance_ && !initGl()) return;

    drainCommands();
    feedAudio();

    const auto now = std::chrono::steady_clock::now();
    const double elapsed = std::chrono::duration<double>(now - presetStartedAt_).count();
    if (!presetLocked_ && !presets_.empty() && elapsed >= presetDurationSeconds_) {
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

std::string ProjectMVisualizer::currentPresetName() const {
    std::lock_guard<std::mutex> lock(commandMutex_);
    return currentPresetName_;
}

std::string ProjectMVisualizer::currentPresetKey() const {
    std::lock_guard<std::mutex> lock(commandMutex_);
    return currentPresetKey_;
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
        if (command.type == PresetCommand::Type::Next) {
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
