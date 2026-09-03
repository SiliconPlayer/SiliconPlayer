#pragma once

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum SiliconVisMode {
    SILICON_VIS_MODE_NONE = 0,
    SILICON_VIS_MODE_BARS = 1,
    SILICON_VIS_MODE_OSCILLOSCOPE = 2,
    SILICON_VIS_MODE_VU_METERS = 3,
    SILICON_VIS_MODE_CHANNEL_SCOPE = 4,
    SILICON_VIS_MODE_CUSTOM_PLUGIN = 100
} SiliconVisMode;

typedef enum SiliconVisChannelLayout {
    SILICON_VIS_LAYOUT_AUTO = 0,
    SILICON_VIS_LAYOUT_SINGLE_COLUMN = 1,
    SILICON_VIS_LAYOUT_DUAL_COLUMN = 2,
    SILICON_VIS_LAYOUT_THREE_COLUMN = 3,
    SILICON_VIS_LAYOUT_FOUR_COLUMN = 4
} SiliconVisChannelLayout;

typedef enum SiliconVisTextAnchor {
    SILICON_VIS_TEXT_ANCHOR_TOP_LEFT = 0,
    SILICON_VIS_TEXT_ANCHOR_TOP_CENTER = 1,
    SILICON_VIS_TEXT_ANCHOR_TOP_RIGHT = 2,
    SILICON_VIS_TEXT_ANCHOR_BOTTOM_LEFT = 3,
    SILICON_VIS_TEXT_ANCHOR_BOTTOM_CENTER = 4,
    SILICON_VIS_TEXT_ANCHOR_BOTTOM_RIGHT = 5
} SiliconVisTextAnchor;

typedef enum SiliconVisVuAnchor {
    SILICON_VIS_VU_ANCHOR_TOP = 0,
    SILICON_VIS_VU_ANCHOR_BOTTOM = 1
} SiliconVisVuAnchor;

typedef enum SiliconVisWaveRenderMode {
    SILICON_VIS_WAVE_RENDER_OFF = 0,
    SILICON_VIS_WAVE_RENDER_ANTIALIAS = 1,
    SILICON_VIS_WAVE_RENDER_CRT = 2
} SiliconVisWaveRenderMode;

typedef enum SiliconVisContrastMode {
    SILICON_VIS_CONTRAST_NONE = 0,
    SILICON_VIS_CONTRAST_BARS = 1,
    SILICON_VIS_CONTRAST_OSCILLOSCOPE_MONO = 2,
    SILICON_VIS_CONTRAST_OSCILLOSCOPE_STEREO = 3,
    SILICON_VIS_CONTRAST_VU_TOP = 4,
    SILICON_VIS_CONTRAST_VU_BOTTOM = 5,
    SILICON_VIS_CONTRAST_CHANNEL_SCOPE = 6
} SiliconVisContrastMode;

typedef struct SiliconVisChannelTextState {
    int32_t channelIndex;
    int32_t note;
    int32_t volume;
    uint8_t effectPrimaryLetterAscii;
    int16_t effectPrimaryParam;
    uint8_t effectSecondaryLetterAscii;
    int16_t effectSecondaryParam;
    int32_t instrumentIndex;
    int32_t sampleIndex;
    uint32_t flags;
} SiliconVisChannelTextState;

typedef struct SiliconVisTextPalette {
    uint32_t channelArgb;
    uint32_t noteArgb;
    uint32_t volumeArgb;
    uint32_t effectArgb;
    uint32_t instrumentOrSampleArgb;
    uint32_t separatorArgb;
} SiliconVisTextPalette;

typedef struct SiliconVisArtworkFrame {
    bool hasArtwork;
    const uint8_t* rgbaPixels;
    int32_t pixelWidth;
    int32_t pixelHeight;
    uint32_t primaryColorArgb;
    uint32_t surfaceColorArgb;
    int32_t placeholderIconType; // 0=none, 1=music_note, 2=tracker_chip, 3=gamepad
    SiliconVisContrastMode contrastMode;
    float density;
} SiliconVisArtworkFrame;

#ifdef __cplusplus
}
#endif
