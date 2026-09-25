cmake_minimum_required(VERSION 3.22)
project(siliconplayer_desktop LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Ensure runtime path searches the directory where the shared library resides
set(CMAKE_BUILD_RPATH "$ORIGIN")
set(CMAKE_INSTALL_RPATH "$ORIGIN")

# Path variables
set(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../..")
set(APP_CPP_DIR "${REPO_ROOT}/app/src/main/cpp")
set(EXTERNAL_DIR "${REPO_ROOT}/external")

# Find system dependencies
find_package(JNI REQUIRED)
find_package(Threads REQUIRED)
find_package(OpenGL REQUIRED)
find_package(PkgConfig REQUIRED)

pkg_check_modules(ALSA REQUIRED alsa)
pkg_check_modules(PULSE libpulse)
pkg_check_modules(X11 REQUIRED x11)

option(USE_SYSTEM_FFMPEG "Use system FFmpeg libraries instead of external/ffmpeg submodule" ON)
if (USE_SYSTEM_FFMPEG)
    pkg_check_modules(AVCODEC REQUIRED libavcodec)
    pkg_check_modules(AVFORMAT REQUIRED libavformat)
    pkg_check_modules(AVUTIL REQUIRED libavutil)
    pkg_check_modules(SWRESAMPLE REQUIRED libswresample)
    pkg_check_modules(SOXR REQUIRED soxr)
else()
    set(FFMPEG_INSTALL_DIR "${EXTERNAL_DIR}/ffmpeg/build_host/install")
    set(AVCODEC_INCLUDE_DIRS "${FFMPEG_INSTALL_DIR}/include")
    set(AVFORMAT_INCLUDE_DIRS "${FFMPEG_INSTALL_DIR}/include")
    set(AVUTIL_INCLUDE_DIRS "${FFMPEG_INSTALL_DIR}/include")
    set(SWRESAMPLE_INCLUDE_DIRS "${FFMPEG_INSTALL_DIR}/include")
    find_library(AVCODEC_LIBRARIES avcodec HINTS "${FFMPEG_INSTALL_DIR}/lib" REQUIRED)
    find_library(AVFORMAT_LIBRARIES avformat HINTS "${FFMPEG_INSTALL_DIR}/lib" REQUIRED)
    find_library(AVUTIL_LIBRARIES avutil HINTS "${FFMPEG_INSTALL_DIR}/lib" REQUIRED)
    find_library(SWRESAMPLE_LIBRARIES swresample HINTS "${FFMPEG_INSTALL_DIR}/lib" REQUIRED)
    pkg_check_modules(SOXR soxr)
endif()

# silicon_vis visualizer core
add_subdirectory("${EXTERNAL_DIR}/silicon/silicon_vis" "${CMAKE_CURRENT_BINARY_DIR}/silicon_vis")

# Common include directories
set(COMMON_INCLUDES
    "${CMAKE_CURRENT_SOURCE_DIR}/include" # android/log.h shim
    "${APP_CPP_DIR}"
    "${EXTERNAL_DIR}/miniaudio"
    "${EXTERNAL_DIR}/libopenmpt"
    "${EXTERNAL_DIR}/libsidplayfp/src"
    "${EXTERNAL_DIR}/libusb/libusb"
    "${EXTERNAL_DIR}/silicon/silicon_vis/include"
    "${EXTERNAL_DIR}/silicon/silicon_vis/src"
    "${EXTERNAL_DIR}/projectm/src/api/include"
    ${JNI_INCLUDE_DIRS}
    ${ALSA_INCLUDE_DIRS}
    ${PULSE_INCLUDE_DIRS}
    ${X11_INCLUDE_DIRS}
)

# -----------------------------------------------------------------------------
# Main Desktop JNI Playback Library
# -----------------------------------------------------------------------------
set(MAIN_SOURCES
    "${APP_CPP_DIR}/SiliconPlayerNative.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
    "${APP_CPP_DIR}/ChannelScopeTrigger.cpp"
    "${EXTERNAL_DIR}/miniaudio/miniaudio.c"
    "${APP_CPP_DIR}/AudioEngine.cpp"
    "${APP_CPP_DIR}/AudioEngineStream.cpp"
    "${APP_CPP_DIR}/AudioEngineRender.cpp"
    "${APP_CPP_DIR}/AudioEngineCoreOptions.cpp"
    "${APP_CPP_DIR}/AudioEngineMetadata.cpp"
    "${APP_CPP_DIR}/AudioEngineTransport.cpp"
    "${APP_CPP_DIR}/AudioEnginePipeline.cpp"
    "${APP_CPP_DIR}/AudioEngineEffects.cpp"
    "${APP_CPP_DIR}/effects/openmpt_dsp/OpenMptDspEffects.cpp"
    "${APP_CPP_DIR}/decoders/DecoderPluginLoader.cpp"
    "${APP_CPP_DIR}/decoders/DecoderRegistry.cpp"
    "${APP_CPP_DIR}/decoders/SdlCompat.c"
    "${APP_CPP_DIR}/decoders/CPConvStub.c"
    "${APP_CPP_DIR}/SiliconVisJni.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/DesktopGlSurface.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/UacDriverStub.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/ProjectMStub.cpp"
)

add_library(siliconplayer_desktop SHARED ${MAIN_SOURCES})

target_include_directories(siliconplayer_desktop PRIVATE ${COMMON_INCLUDES})

target_compile_definitions(siliconplayer_desktop PRIVATE
    _GNU_SOURCE
    MA_NO_DECODING
    MA_NO_ENCODING
    MA_NO_RESOURCE_MANAGER
    MA_NO_NODE_GRAPH
    MA_NO_ENGINE
    MA_ENABLE_ALSA
    MA_ENABLE_PULSEAUDIO
    SILICON_NO_PROJECTM
)

target_link_libraries(siliconplayer_desktop PRIVATE
    silicon_vis
    OpenGL::GL
    ${X11_LIBRARIES}
    Threads::Threads
    ${CMAKE_DL_LIBS}
    m
    ${ALSA_LIBRARIES}
    ${PULSE_LIBRARIES}
    ${AVUTIL_LIBRARIES}
    ${SWRESAMPLE_LIBRARIES}
    ${SOXR_LIBRARIES}
)

# -----------------------------------------------------------------------------
# Decoder Plugins (shared .so files dynamically loaded by DecoderPluginLoader)
# -----------------------------------------------------------------------------

# 1. FFmpeg Decoder
add_library(siliconplayer_ffmpeg_decoder SHARED
    "${APP_CPP_DIR}/decoders/FFmpegDecoder.cpp"
    "${APP_CPP_DIR}/decoders/FFmpegDecoderPlugin.cpp"
)
target_include_directories(siliconplayer_ffmpeg_decoder PRIVATE
    ${COMMON_INCLUDES}
    ${AVCODEC_INCLUDE_DIRS}
    ${AVFORMAT_INCLUDE_DIRS}
    ${AVUTIL_INCLUDE_DIRS}
    ${SWRESAMPLE_INCLUDE_DIRS}
    ${SOXR_INCLUDE_DIRS}
)
target_link_libraries(siliconplayer_ffmpeg_decoder PRIVATE
    ${AVFORMAT_LIBRARIES}
    ${AVCODEC_LIBRARIES}
    ${AVUTIL_LIBRARIES}
    ${SWRESAMPLE_LIBRARIES}
    ${SOXR_LIBRARIES}
    m
)

# 2. LibOpenMPT Decoder (using submodule build with custom channel scope)
add_library(libopenmpt_submodule SHARED IMPORTED)
set_target_properties(libopenmpt_submodule PROPERTIES IMPORTED_LOCATION "${EXTERNAL_DIR}/libopenmpt/bin/libopenmpt.so")

add_library(siliconplayer_openmpt_decoder SHARED
    "${APP_CPP_DIR}/decoders/LibOpenMPTDecoder.cpp"
    "${APP_CPP_DIR}/decoders/LibOpenMPTDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_openmpt_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${EXTERNAL_DIR}/libopenmpt"
)
target_link_libraries(siliconplayer_openmpt_decoder PRIVATE
    libopenmpt_submodule
    m
)
add_custom_command(TARGET siliconplayer_openmpt_decoder POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    "${EXTERNAL_DIR}/libopenmpt/bin/libopenmpt.so"
    "${CMAKE_CURRENT_BINARY_DIR}/libopenmpt.so.0"
)

# Build custom-patched libgme and libxmp from submodules
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
add_subdirectory("${EXTERNAL_DIR}/libgme" "${CMAKE_CURRENT_BINARY_DIR}/libgme")
add_subdirectory("${EXTERNAL_DIR}/libxmp" "${CMAKE_CURRENT_BINARY_DIR}/libxmp")

# 3. Game Music Emu (GME) Decoder
add_library(siliconplayer_gme_decoder SHARED
    "${APP_CPP_DIR}/decoders/GmeDecoder.cpp"
    "${APP_CPP_DIR}/decoders/GmeDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_gme_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${EXTERNAL_DIR}/libgme"
)
target_link_libraries(siliconplayer_gme_decoder PRIVATE
    gme_static
    m
)

# 4. LibXMP Decoder
add_library(siliconplayer_xmp_decoder SHARED
    "${APP_CPP_DIR}/decoders/XmpDecoder.cpp"
    "${APP_CPP_DIR}/decoders/XmpDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_xmp_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${EXTERNAL_DIR}/libxmp/include"
)
target_link_libraries(siliconplayer_xmp_decoder PRIVATE
    xmp_shared
    m
)
add_custom_command(TARGET siliconplayer_xmp_decoder POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    "$<TARGET_FILE:xmp_shared>"
    "${CMAKE_CURRENT_BINARY_DIR}/libxmp.so.4"
)

# 5. LibSIDPlayFP Decoder (using desktop prebuilt)
set(PREBUILT_DIR "${REPO_ROOT}/desktop/prebuilt/x86_64")

add_library(libresidfp_prebuilt SHARED IMPORTED)
set_target_properties(libresidfp_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresidfp.so")

add_library(libsidplayfp_prebuilt SHARED IMPORTED)
set_target_properties(libsidplayfp_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsidplayfp.so")

add_library(libresid_prebuilt STATIC IMPORTED)
set_target_properties(libresid_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresid.a")

add_library(siliconplayer_libsidplayfp_decoder SHARED
    "${APP_CPP_DIR}/decoders/LibSidPlayFpDecoder.cpp"
    "${APP_CPP_DIR}/decoders/LibSidPlayFpDecoderPlugin.cpp"
    "${APP_CPP_DIR}/decoders/sid/ReSidBuilder.cpp"
    "${APP_CPP_DIR}/decoders/sid/ReSidEmu.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_libsidplayfp_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
    "${EXTERNAL_DIR}"
    "${EXTERNAL_DIR}/resid"
    "${EXTERNAL_DIR}/resid/build_desktop_x86_64"
    "${EXTERNAL_DIR}/libsidplayfp/src"
)
target_link_libraries(siliconplayer_libsidplayfp_decoder PRIVATE
    libsidplayfp_prebuilt
    libresidfp_prebuilt
    libresid_prebuilt
    m
)
set_target_properties(siliconplayer_libsidplayfp_decoder PROPERTIES
    BUILD_RPATH "\$ORIGIN"
    INSTALL_RPATH "\$ORIGIN"
)
add_custom_command(TARGET siliconplayer_libsidplayfp_decoder POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    "${PREBUILT_DIR}/lib/libresidfp.so"
    "${CMAKE_CURRENT_BINARY_DIR}/libresidfp.so.0"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    "${PREBUILT_DIR}/lib/libsidplayfp.so"
    "${CMAKE_CURRENT_BINARY_DIR}/libsidplayfp.so.6"
)

