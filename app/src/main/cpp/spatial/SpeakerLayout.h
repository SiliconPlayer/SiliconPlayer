#ifndef SILICONPLAYER_SPATIAL_SPEAKER_LAYOUT_H
#define SILICONPLAYER_SPATIAL_SPEAKER_LAYOUT_H

#include <array>
#include <cstddef>

namespace siliconplayer::spatial {

enum class BedLayoutId : int {
    Mono_1_0 = 0,       // [FC]
    Stereo_2_0 = 1,     // [FL, FR]
    Quad_4_0 = 2,       // [FL, FR, BL, BR]
    Surround_5_1 = 3,   // [FL, FR, FC, LFE, BL/SL, BR/SR]
    Surround_7_1 = 4,   // [FL, FR, FC, LFE, BL, BR, SL, SR]
    Atmos_7_1_4 = 5     // [FL, FR, FC, LFE, BL, BR, SL, SR, TFL, TFR, TBL, TBR]
};

struct SpeakerPolarPosition {
    // Azimuth in degrees: 0 = front center, +angles = left (counter-clockwise), -angles = right.
    float azimuthDeg = 0.0f;
    // Elevation in degrees: 0 = horizontal plane, +45 = top height speakers.
    float elevationDeg = 0.0f;
    bool isLfe = false;
};

constexpr int channelCountForBedLayout(BedLayoutId layout) {
    switch (layout) {
        case BedLayoutId::Mono_1_0:
            return 1;
        case BedLayoutId::Stereo_2_0:
            return 2;
        case BedLayoutId::Quad_4_0:
            return 4;
        case BedLayoutId::Surround_5_1:
            return 6;
        case BedLayoutId::Surround_7_1:
            return 8;
        case BedLayoutId::Atmos_7_1_4:
            return 12;
    }
    return 2;
}

constexpr BedLayoutId canonicalBedLayoutForChannelCount(int channels) {
    if (channels <= 1) {
        return BedLayoutId::Mono_1_0;
    }
    if (channels == 2) {
        return BedLayoutId::Stereo_2_0;
    }
    if (channels == 4) {
        return BedLayoutId::Quad_4_0;
    }
    if (channels <= 6) {
        return BedLayoutId::Surround_5_1;
    }
    if (channels <= 8) {
        return BedLayoutId::Surround_7_1;
    }
    return BedLayoutId::Atmos_7_1_4;
}

inline constexpr std::array<SpeakerPolarPosition, 1> kMono10Speakers {{
    { 0.0f, 0.0f, false }
}};

inline constexpr std::array<SpeakerPolarPosition, 2> kStereo20Speakers {{
    {  30.0f, 0.0f, false }, // FL
    { -30.0f, 0.0f, false }  // FR
}};

inline constexpr std::array<SpeakerPolarPosition, 4> kQuad40Speakers {{
    {   45.0f, 0.0f, false }, // FL
    {  -45.0f, 0.0f, false }, // FR
    {  135.0f, 0.0f, false }, // BL
    { -135.0f, 0.0f, false }  // BR
}};

inline constexpr std::array<SpeakerPolarPosition, 6> kSurround51Speakers {{
    {   30.0f, 0.0f, false }, // FL
    {  -30.0f, 0.0f, false }, // FR
    {    0.0f, 0.0f, false }, // FC
    {    0.0f, 0.0f, true  }, // LFE
    {  110.0f, 0.0f, false }, // SL / BL
    { -110.0f, 0.0f, false }  // SR / BR
}};

inline constexpr std::array<SpeakerPolarPosition, 8> kSurround71Speakers {{
    {   30.0f, 0.0f, false }, // FL
    {  -30.0f, 0.0f, false }, // FR
    {    0.0f, 0.0f, false }, // FC
    {    0.0f, 0.0f, true  }, // LFE
    {  135.0f, 0.0f, false }, // BL
    { -135.0f, 0.0f, false }, // BR
    {   90.0f, 0.0f, false }, // SL
    {  -90.0f, 0.0f, false }  // SR
}};

inline constexpr std::array<SpeakerPolarPosition, 12> kAtmos714Speakers {{
    {   30.0f,  0.0f, false }, // FL
    {  -30.0f,  0.0f, false }, // FR
    {    0.0f,  0.0f, false }, // FC
    {    0.0f,  0.0f, true  }, // LFE
    {  135.0f,  0.0f, false }, // BL
    { -135.0f,  0.0f, false }, // BR
    {   90.0f,  0.0f, false }, // SL
    {  -90.0f,  0.0f, false }, // SR
    {   45.0f, 45.0f, false }, // TFL
    {  -45.0f, 45.0f, false }, // TFR
    {  135.0f, 45.0f, false }, // TBL
    { -135.0f, 45.0f, false }  // TBR
}};

inline const SpeakerPolarPosition* speakersForBedLayout(BedLayoutId layout, int* outCount) {
    switch (layout) {
        case BedLayoutId::Mono_1_0:
            if (outCount) *outCount = static_cast<int>(kMono10Speakers.size());
            return kMono10Speakers.data();
        case BedLayoutId::Stereo_2_0:
            if (outCount) *outCount = static_cast<int>(kStereo20Speakers.size());
            return kStereo20Speakers.data();
        case BedLayoutId::Quad_4_0:
            if (outCount) *outCount = static_cast<int>(kQuad40Speakers.size());
            return kQuad40Speakers.data();
        case BedLayoutId::Surround_5_1:
            if (outCount) *outCount = static_cast<int>(kSurround51Speakers.size());
            return kSurround51Speakers.data();
        case BedLayoutId::Surround_7_1:
            if (outCount) *outCount = static_cast<int>(kSurround71Speakers.size());
            return kSurround71Speakers.data();
        case BedLayoutId::Atmos_7_1_4:
            if (outCount) *outCount = static_cast<int>(kAtmos714Speakers.size());
            return kAtmos714Speakers.data();
    }
    if (outCount) *outCount = static_cast<int>(kStereo20Speakers.size());
    return kStereo20Speakers.data();
}

} // namespace siliconplayer::spatial

#endif // SILICONPLAYER_SPATIAL_SPEAKER_LAYOUT_H
