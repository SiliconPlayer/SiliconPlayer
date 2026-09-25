#include "ProjectMVisualizer.h"

ProjectMVisualizer::ProjectMVisualizer(silicon::vis::IVisualizationAudioProvider*) {}
bool ProjectMVisualizer::initGl() { return true; }
void ProjectMVisualizer::resize(int32_t, int32_t, float) {}
void ProjectMVisualizer::render() {}
void ProjectMVisualizer::releaseGl() {}
void ProjectMVisualizer::setPresetSets(const std::vector<std::pair<std::string, std::string>>&) {}
void ProjectMVisualizer::setPresetKeys(const std::vector<std::pair<std::string, std::string>>&, const std::vector<std::string>&) {}
void ProjectMVisualizer::setStartPreset(const std::string&) {}
void ProjectMVisualizer::nextPreset(bool) {}
void ProjectMVisualizer::previousPreset(bool) {}
void ProjectMVisualizer::loadPresetKey(const std::string&, bool) {}
void ProjectMVisualizer::setPresetLocked(bool) {}
bool ProjectMVisualizer::isPresetLocked() const { return false; }
void ProjectMVisualizer::setPresetDuration(double) {}
void ProjectMVisualizer::setHardCutEnabled(bool) {}
void ProjectMVisualizer::setHardCutSensitivity(float) {}
void ProjectMVisualizer::setRotationRandom(bool) {}
void ProjectMVisualizer::setMeshSize(int) {}
void ProjectMVisualizer::setAspectCorrection(bool) {}
void ProjectMVisualizer::setFps(int) {}
void ProjectMVisualizer::setMaxResolutionPx(int) {}
std::string ProjectMVisualizer::currentPresetName() const { return ""; }
std::string ProjectMVisualizer::currentPresetKey() const { return ""; }
std::vector<std::string> ProjectMVisualizer::presetKeys() const { return {}; }
std::vector<std::string> ProjectMVisualizer::presetSetIds() const { return {}; }
