cmake_minimum_required(VERSION 3.22.1)

project("siliconplayer")

# Define the library name
set(CMAKE_CXX_STANDARD 20)

# -----------------------------------------------------------------------------
# LibOpenMPT Configuration (Paused due to build complexity)
# -----------------------------------------------------------------------------
# set(LIBOPENMPT_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/libopenmpt)
# ... sources ...

# -----------------------------------------------------------------------------
# Main Native Library
# -----------------------------------------------------------------------------

add_library(
        siliconplayer
        SHARED
        SiliconPlayerNative.cpp
        ChannelScopeSharedState.cpp
        ChannelScopeTrigger.cpp
        AudioTrackJniBridge.cpp
        AudioEngine.cpp
        AudioEngineStream.cpp
        AudioEngineRender.cpp
        AudioEngineCoreOptions.cpp
        AudioEngineMetadata.cpp
        AudioEngineTransport.cpp
        AudioEnginePipeline.cpp
        AudioEngineEffects.cpp
        effects/openmpt_dsp/OpenMptDspEffects.cpp
        decoders/sid/ReSidBuilder.cpp
        decoders/sid/ReSidEmu.cpp
        decoders/FFmpegDecoder.cpp
        decoders/DecoderRegistry.cpp
        decoders/LibOpenMPTDecoder.cpp
        decoders/VGMDecoder.cpp
        decoders/GmeDecoder.cpp
        decoders/CRSIDDecoder.cpp
        decoders/LibSidPlayFpDecoder.cpp
        decoders/LazyUsf2Decoder.cpp
        decoders/Vio2sfDecoder.cpp
        decoders/Sc68Decoder.cpp
        decoders/AdPlugDecoder.cpp
        decoders/UadeDecoder.cpp
        decoders/HivelyTrackerDecoder.cpp
        decoders/KlystrackDecoder.cpp
        decoders/FurnaceDecoder.cpp
        decoders/SdlCompat.c
        decoders/CPConvStub.c
)

# Furnace exported headers rely on this define for SFWrapper-dependent structs.
target_compile_definitions(siliconplayer PRIVATE HAVE_SNDFILE)

# Android 15+ prefers 16KB page-aligned native libraries.
if (ANDROID)
    target_compile_options(
            siliconplayer
            PRIVATE
            "-fvisibility=hidden"
            "-fvisibility-inlines-hidden"
    )
    target_link_options(
            siliconplayer
            PRIVATE
            "-Wl,-z,max-page-size=16384"
            "-Wl,-z,common-page-size=16384"
            "-Wl,--exclude-libs,ALL"
    )
endif()

include_directories(${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include)
# Expose full libopenmpt headers (including libopenmpt_ext.hpp) from the submodule tree.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/libopenmpt)
# ReSID backend implementation requires libsidplayfp internal SID emulation headers.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/libsidplayfp/src)
# Furnace headers depend on bundled fmt headers through ta-log.h.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/furnace/extern/fmt/include)
# Furnace engine headers also reference bundled blip_buf headers directly.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/furnace/extern/blip_buf)
# Furnace SFWrapper type declarations require bundled libsndfile public headers.
include_directories(${CMAKE_SOURCE_DIR}/../../../../external/furnace/extern/libsndfile-modified/include)

    # Find Android libraries
    find_library(log-lib log)
    find_library(android-lib android)
    find_library(opensles-lib OpenSLES)
    find_library(mediandk-lib mediandk)
    find_library(dl-lib dl)

    set(PREBUILT_DIR ${CMAKE_SOURCE_DIR}/prebuilt/${ANDROID_ABI})

    # Import libopenmpt (Shared)
    add_library(libopenmpt SHARED IMPORTED)
    set_target_properties(libopenmpt PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/${ANDROID_ABI}/libopenmpt.so")

    # Import FFmpeg libraries (Shared)
    add_library(libavformat SHARED IMPORTED)
    set_target_properties(libavformat PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavformat.so")

    add_library(libavcodec SHARED IMPORTED)
    set_target_properties(libavcodec PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavcodec.so")

    add_library(libavutil SHARED IMPORTED)
    set_target_properties(libavutil PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libavutil.so")

    add_library(libswresample SHARED IMPORTED)
    set_target_properties(libswresample PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libswresample.so")

    add_library(libsoxr SHARED IMPORTED)
    set_target_properties(libsoxr PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsoxr.so")

    # Import libvgm libraries (Shared)
    add_library(libvgm-utils SHARED IMPORTED)
    set_target_properties(libvgm-utils PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-utils.so")

    add_library(libvgm-emu SHARED IMPORTED)
    set_target_properties(libvgm-emu PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-emu.so")

    add_library(libvgm-player SHARED IMPORTED)
    set_target_properties(libvgm-player PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvgm-player.so")

    # Import libgme (Shared)
    add_library(libgme SHARED IMPORTED)
    set_target_properties(libgme PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libgme.so")

    # Import lazyusf2 (Shared)
    add_library(liblazyusf2 SHARED IMPORTED)
    set_target_properties(liblazyusf2 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/liblazyusf2.so")

    # Import psflib + vio2sf (Shared)
    add_library(libpsflib SHARED IMPORTED)
    set_target_properties(libpsflib PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libpsflib.so")

    add_library(libvio2sf SHARED IMPORTED)
    set_target_properties(libvio2sf PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libvio2sf.so")

    # Import sc68 stack (Shared)
    add_library(libsc68 SHARED IMPORTED)
    set_target_properties(libsc68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsc68.so")

    add_library(libfile68 SHARED IMPORTED)
    set_target_properties(libfile68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfile68.so")

    add_library(libunice68 SHARED IMPORTED)
    set_target_properties(libunice68 PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libunice68.so")

    # Import adplug stack (Shared)
    add_library(libadplug SHARED IMPORTED)
    set_target_properties(libadplug PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libadplug.so")

    set(LIBBINIO_LOCATION "${PREBUILT_DIR}/lib/libbinio.so")
    if (NOT EXISTS "${LIBBINIO_LOCATION}")
        set(LIBBINIO_LOCATION "${PREBUILT_DIR}/lib/liblibbinio.so")
    endif()
    add_library(libbinio SHARED IMPORTED)
    set_target_properties(libbinio PROPERTIES IMPORTED_LOCATION "${LIBBINIO_LOCATION}")

    # Import UADE stack (Shared)
    add_library(libuade SHARED IMPORTED)
    set_target_properties(libuade PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libuade.so")

    add_library(libzakalwe SHARED IMPORTED)
    set_target_properties(libzakalwe PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libzakalwe.so")

    add_library(libbencodetools SHARED IMPORTED)
    set_target_properties(libbencodetools PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libbencodetools.so")

    # Import hivelytracker (Shared)
    add_library(libhivelytracker SHARED IMPORTED)
    set_target_properties(libhivelytracker PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libhivelytracker.so")

    # Import klystrack replayer (Shared)
    add_library(libklystrack SHARED IMPORTED)
    set_target_properties(libklystrack PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libklystrack.so")

    # Import furnace headless engine (Shared)
    add_library(libfurnace SHARED IMPORTED)
    set_target_properties(libfurnace PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libfurnace.so")

    # Import libsidplayfp + libresidfp (Shared)
    add_library(libsidplayfp SHARED IMPORTED)
    set_target_properties(libsidplayfp PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libsidplayfp.so")

    add_library(libresidfp SHARED IMPORTED)
    set_target_properties(libresidfp PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresidfp.so")

    add_library(libresid SHARED IMPORTED)
    set_target_properties(libresid PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libresid.so")

    set(HAS_CRSID_PREBUILT FALSE)
    if (EXISTS "${PREBUILT_DIR}/lib/libcRSID.so")
        add_library(libcRSID SHARED IMPORTED)
        set_target_properties(libcRSID PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libcRSID.so")
        set(HAS_CRSID_PREBUILT TRUE)
    endif()

    set(HAS_OPENSSL_PREBUILT FALSE)
    if (EXISTS "${PREBUILT_DIR}/lib/libssl.so" AND EXISTS "${PREBUILT_DIR}/lib/libcrypto.so")
        add_library(libssl SHARED IMPORTED)
        set_target_properties(libssl PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libssl.so")

        add_library(libcrypto SHARED IMPORTED)
        set_target_properties(libcrypto PROPERTIES IMPORTED_LOCATION "${PREBUILT_DIR}/lib/libcrypto.so")
        set(HAS_OPENSSL_PREBUILT TRUE)
    endif()

    # Link libraries
    target_link_libraries(
            siliconplayer
            ${log-lib}
            ${android-lib}
            ${opensles-lib}
            ${mediandk-lib}
            libopenmpt
            libavformat
            libavcodec
            libavutil
            libswresample
            libsoxr
            libvgm-player
            libvgm-emu
            libvgm-utils
            libgme
            liblazyusf2
            libvio2sf
            libpsflib
            libsc68
            libfile68
            libunice68
            libadplug
            libbinio
            libuade
            libzakalwe
            libbencodetools
            libhivelytracker
            libklystrack
            libsidplayfp
            libresidfp
            libresid
            libfurnace
            z # zlib is needed for openmpt/ffmpeg/vgm
            m # libbinio can require libm symbols on some toolchains
            ${dl-lib}
    )

    if (HAS_CRSID_PREBUILT)
        target_link_libraries(siliconplayer libcRSID)
    endif()

    if (HAS_OPENSSL_PREBUILT)
        target_link_libraries(siliconplayer libssl libcrypto)
    endif()
