cmake_minimum_required(VERSION 3.22)
project(siliconplayer_desktop LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Path variables
set(REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../..")
set(APP_CPP_DIR "${REPO_ROOT}/app/src/main/cpp")
set(EXTERNAL_DIR "${REPO_ROOT}/external")
set(PREBUILT_DIR "${REPO_ROOT}/desktop/prebuilt/x86_64")

# Ensure runtime path searches the directory where the shared library resides
set(CMAKE_BUILD_RPATH "$ORIGIN;${PREBUILT_DIR}/lib;${EXTERNAL_DIR}/projectm/build_host/src/libprojectM;${CMAKE_CURRENT_BINARY_DIR}/projectm/src/libprojectM")
set(CMAKE_INSTALL_RPATH "$ORIGIN;${PREBUILT_DIR}/lib;${EXTERNAL_DIR}/projectm/build_host/src/libprojectM;${CMAKE_CURRENT_BINARY_DIR}/projectm/src/libprojectM")

# Find system dependencies
find_package(JNI REQUIRED)
find_package(Threads REQUIRED)
find_package(OpenGL REQUIRED)
find_package(PkgConfig REQUIRED)
find_package(ZLIB REQUIRED)

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
    "${APP_CPP_DIR}/ProjectMVisualizer.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/DesktopGlSurface.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/DesktopAudioRoute.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/UacDriverStub.cpp"
)

set(PROJECTM_PREBUILT "${PREBUILT_DIR}/lib/libprojectM-4.so")
set(PROJECTM_HOST_LIB "${EXTERNAL_DIR}/projectm/build_host/src/libprojectM/libprojectM-4.so")
if (EXISTS "${PROJECTM_PREBUILT}")
    add_library(libprojectM-4 SHARED IMPORTED)
    set_target_properties(libprojectM-4 PROPERTIES IMPORTED_LOCATION "${PROJECTM_PREBUILT}")
elseif (EXISTS "${PROJECTM_HOST_LIB}")
    add_library(libprojectM-4 SHARED IMPORTED)
    set_target_properties(libprojectM-4 PROPERTIES IMPORTED_LOCATION "${PROJECTM_HOST_LIB}")
else()
    set(ENABLE_SDL_UI OFF CACHE BOOL "" FORCE)
    set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
    set(BUILD_DOCS OFF CACHE BOOL "" FORCE)
    add_subdirectory("${EXTERNAL_DIR}/projectm" "${CMAKE_CURRENT_BINARY_DIR}/projectm" EXCLUDE_FROM_ALL)
    add_library(libprojectM-4 ALIAS projectM)
endif()

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
)

target_link_libraries(siliconplayer_desktop PRIVATE
    silicon_vis
    libprojectM-4
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
add_custom_command(TARGET siliconplayer_libsidplayfp_decoder POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    "${PREBUILT_DIR}/lib/libresidfp.so"
    "${CMAKE_CURRENT_BINARY_DIR}/libresidfp.so.0"
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    "${PREBUILT_DIR}/lib/libsidplayfp.so"
    "${CMAKE_CURRENT_BINARY_DIR}/libsidplayfp.so.6"
)

# 6. uFMOD Decoder
add_library(libufmod_prebuilt STATIC IMPORTED)
set_target_properties(libufmod_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libufmod.a")

add_library(siliconplayer_ufmod_decoder SHARED
    "${APP_CPP_DIR}/decoders/UfmodDecoder.cpp"
    "${APP_CPP_DIR}/decoders/UfmodDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_ufmod_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_ufmod_decoder PRIVATE
    libufmod_prebuilt
    m
)

# 7. Ayfly Decoder
add_library(libayfly_prebuilt SHARED IMPORTED)
set_target_properties(libayfly_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libayfly.so")

add_library(siliconplayer_ayfly_decoder SHARED
    "${APP_CPP_DIR}/decoders/AyflyDecoder.cpp"
    "${APP_CPP_DIR}/decoders/AyflyDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_compile_definitions(siliconplayer_ayfly_decoder PRIVATE
    DISABLE_AUDIO
    _UNICODE
    UNICODE
)
target_include_directories(siliconplayer_ayfly_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_ayfly_decoder PRIVATE
    libayfly_prebuilt
    m
)

# 8. UADE Decoder
add_library(libuade_prebuilt SHARED IMPORTED)
set_target_properties(libuade_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libuade.so")

add_library(libzakalwe_prebuilt SHARED IMPORTED)
set_target_properties(libzakalwe_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libzakalwe.so")

add_library(libbencodetools_prebuilt SHARED IMPORTED)
set_target_properties(libbencodetools_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libbencodetools.so")

add_library(siliconplayer_uade_decoder SHARED
    "${APP_CPP_DIR}/decoders/UadeDecoder.cpp"
    "${APP_CPP_DIR}/decoders/UadeDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_uade_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_uade_decoder PRIVATE
    libuade_prebuilt
    libzakalwe_prebuilt
    libbencodetools_prebuilt
    ${CMAKE_DL_LIBS}
    m
)

# 9. VGMPlay Decoder
add_library(libvgm_player_prebuilt SHARED IMPORTED)
set_target_properties(libvgm_player_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-player.so")

add_library(libvgm_emu_prebuilt SHARED IMPORTED)
set_target_properties(libvgm_emu_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-emu.so")

add_library(libvgm_utils_prebuilt SHARED IMPORTED)
set_target_properties(libvgm_utils_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-utils.so")

add_library(siliconplayer_vgm_decoder SHARED
    "${APP_CPP_DIR}/decoders/VGMDecoder.cpp"
    "${APP_CPP_DIR}/decoders/VGMDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_vgm_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_vgm_decoder PRIVATE
    libvgm_player_prebuilt
    libvgm_emu_prebuilt
    libvgm_utils_prebuilt
    ZLIB::ZLIB
    m
)

# 10. cRSID Decoder
add_library(libcRSID_prebuilt SHARED IMPORTED)
set_target_properties(libcRSID_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libcRSID.so")

add_library(siliconplayer_crsid_decoder SHARED
    "${APP_CPP_DIR}/decoders/CRSIDDecoder.cpp"
    "${APP_CPP_DIR}/decoders/CRSIDDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_crsid_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_crsid_decoder PRIVATE
    libcRSID_prebuilt
    m
)

# 11. SC68 Decoder
add_library(libsc68_prebuilt SHARED IMPORTED)
set_target_properties(libsc68_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsc68.so")

add_library(libfile68_prebuilt SHARED IMPORTED)
set_target_properties(libfile68_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfile68.so")

add_library(libunice68_prebuilt SHARED IMPORTED)
set_target_properties(libunice68_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libunice68.so")

add_library(siliconplayer_sc68_decoder SHARED
    "${APP_CPP_DIR}/decoders/Sc68Decoder.cpp"
    "${APP_CPP_DIR}/decoders/Sc68DecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_sc68_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_sc68_decoder PRIVATE
    libsc68_prebuilt
    libfile68_prebuilt
    libunice68_prebuilt
    m
)

# 12. AdPlug Decoder
add_library(libadplug_prebuilt SHARED IMPORTED)
set_target_properties(libadplug_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libadplug.so")

add_library(libbinio_prebuilt SHARED IMPORTED)
set_target_properties(libbinio_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/liblibbinio.so")

add_library(siliconplayer_adplug_decoder SHARED
    "${APP_CPP_DIR}/decoders/AdPlugDecoder.cpp"
    "${APP_CPP_DIR}/decoders/AdPlugDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_adplug_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_adplug_decoder PRIVATE
    libadplug_prebuilt
    libbinio_prebuilt
    m
)

# 13. LazyUSF2 Decoder
add_library(liblazyusf2_prebuilt STATIC IMPORTED)
set_target_properties(liblazyusf2_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/liblazyusf2.a")

add_library(siliconplayer_lazyusf2_decoder SHARED
    "${APP_CPP_DIR}/decoders/LazyUsf2Decoder.cpp"
    "${APP_CPP_DIR}/decoders/LazyUsf2DecoderPlugin.cpp"
)
target_include_directories(siliconplayer_lazyusf2_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
    "${EXTERNAL_DIR}"
)
target_link_libraries(siliconplayer_lazyusf2_decoder PRIVATE
    liblazyusf2_prebuilt
    ZLIB::ZLIB
    m
)

# 14. Vio2sf Decoder
add_library(libvio2sf_prebuilt SHARED IMPORTED)
set_target_properties(libvio2sf_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvio2sf.so")

add_library(libpsflib_prebuilt SHARED IMPORTED)
set_target_properties(libpsflib_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libpsflib.so")

add_library(siliconplayer_vio2sf_decoder SHARED
    "${APP_CPP_DIR}/decoders/Vio2sfDecoder.cpp"
    "${APP_CPP_DIR}/decoders/Vio2sfDecoderPlugin.cpp"
)
target_include_directories(siliconplayer_vio2sf_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_vio2sf_decoder PRIVATE
    libvio2sf_prebuilt
    libpsflib_prebuilt
    ZLIB::ZLIB
    m
)

# 15. HivelyTracker Decoder
add_library(libhivelytracker_prebuilt SHARED IMPORTED)
set_target_properties(libhivelytracker_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libhivelytracker.so")

add_library(siliconplayer_hivelytracker_decoder SHARED
    "${APP_CPP_DIR}/decoders/HivelyTrackerDecoder.cpp"
    "${APP_CPP_DIR}/decoders/HivelyTrackerDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_hivelytracker_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_hivelytracker_decoder PRIVATE
    libhivelytracker_prebuilt
    m
)

# 16. Klystrack Decoder
add_library(libklystrack_prebuilt SHARED IMPORTED)
set_target_properties(libklystrack_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libklystrack.so")

add_library(siliconplayer_klystrack_decoder SHARED
    "${APP_CPP_DIR}/decoders/KlystrackDecoder.cpp"
    "${APP_CPP_DIR}/decoders/KlystrackDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_include_directories(siliconplayer_klystrack_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
)
target_link_libraries(siliconplayer_klystrack_decoder PRIVATE
    libklystrack_prebuilt
    m
)

# 17. Furnace Decoder
add_library(libfurnace_prebuilt SHARED IMPORTED)
set_target_properties(libfurnace_prebuilt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfurnace.so")

add_library(siliconplayer_furnace_decoder SHARED
    "${APP_CPP_DIR}/decoders/FurnaceDecoder.cpp"
    "${APP_CPP_DIR}/decoders/FurnaceDecoderPlugin.cpp"
    "${APP_CPP_DIR}/ChannelScopeSharedState.cpp"
)
target_compile_definitions(siliconplayer_furnace_decoder PRIVATE HAVE_SNDFILE)
target_include_directories(siliconplayer_furnace_decoder PRIVATE
    ${COMMON_INCLUDES}
    "${PREBUILT_DIR}/include"
    "${EXTERNAL_DIR}/furnace/extern/fmt/include"
    "${EXTERNAL_DIR}/furnace/extern/blip_buf"
    "${EXTERNAL_DIR}/furnace/extern/libsndfile-modified/include"
)
target_link_libraries(siliconplayer_furnace_decoder PRIVATE
    libfurnace_prebuilt
    m
)

