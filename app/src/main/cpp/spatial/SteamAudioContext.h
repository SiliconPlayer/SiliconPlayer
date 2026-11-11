#ifndef SILICONPLAYER_SPATIAL_STEAM_AUDIO_CONTEXT_H
#define SILICONPLAYER_SPATIAL_STEAM_AUDIO_CONTEXT_H

#include <phonon/phonon.h>

#include <string>

namespace siliconplayer::spatial {

// Shared Steam Audio handles: one context for the app and one HRTF per
// sample rate / frame size / SOFA file, so the spatializer and future
// Steam Audio effect nodes reuse the same loaded tables.
class SteamAudioContext {
public:
    static IPLContext context();
    static IPLHRTF hrtf(int sampleRate, int frameSize, const std::string& sofaPath);
    static void release();
};

} // namespace siliconplayer::spatial

#endif // SILICONPLAYER_SPATIAL_STEAM_AUDIO_CONTEXT_H
