#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
ABSOLUTE_PATH="$SCRIPT_DIR"

source "$SCRIPT_DIR/build_deps_common.sh"

DESKTOP_DIR="$ABSOLUTE_PATH/../desktop"
PREBUILT_BASE="$DESKTOP_DIR/prebuilt"

usage() {
    echo "Usage: $0 <arch|all> <lib|all[,lib2,...]> [clean]"
    echo "  ARCH: main (x86_64, aarch64), legacy (armv7, x86), x86_64, aarch64, x86, armv7"
    echo "  LIB: all, libsoxr, mbedtls, ffmpeg, libopenmpt, libxmp, libayfly, ufmod, libvgm, libgme, libresid, libresidfp, libsidplayfp, crsid, lazyusf2, psflib, vio2sf, fluidsynth, sc68, libbinio, adplug, libzakalwe, bencodetools, vasm, uade, hivelytracker, klystrack, furnace, projectm"
    echo "  clean (optional): force rebuild (bypass already-built skip checks)"
    echo "  Aliases: sox/soxr, gme, xmp, ayfly, resid/residfp, sid/sidplayfp, crsid/cRSID/libcrsid, usf/lazyusf, psf, 2sf/twosf, fluid/libfluidsynth, libsc68, binio, libadplug, zakalwe, bencode, assembler/vasm, libuade, hvl/hively, kly/kt, fur"
}

if [ "$#" -eq 0 ]; then
    usage
    exit 1
fi

RAW_ARCH="$1"
TARGET_LIB="${2:-all}"
FORCE_CLEAN=0

if [ "$#" -ge 3 ]; then
    case "$3" in
        clean|--clean)
            FORCE_CLEAN=1
            ;;
        *)
            echo "Error: invalid third argument '$3'. Expected 'clean'."
            usage
            exit 1
            ;;
    esac
fi

case "$RAW_ARCH" in
    main|desktop|all)
        TARGET_ARCHES=("x86_64" "aarch64")
        ;;
    legacy|desktop_legacy)
        TARGET_ARCHES=("armv7" "x86")
        ;;
    x86_64|desktop_x86_64)
        TARGET_ARCHES=("x86_64")
        ;;
    aarch64|arm64|armv8|desktop_aarch64|desktop_arm64|desktop_armv8)
        TARGET_ARCHES=("aarch64")
        ;;
    x86|i686|desktop_x86|desktop_i686)
        TARGET_ARCHES=("x86")
        ;;
    armv7|armhf|desktop_armv7|desktop_armhf)
        TARGET_ARCHES=("armv7")
        ;;
    *)
        echo "Error: unknown architecture '$RAW_ARCH'"
        usage
        exit 1
        ;;
esac

if [ "$TARGET_LIB" != "all" ]; then
    IFS=',' read -r -a requested_libs <<< "$TARGET_LIB"
    for raw in "${requested_libs[@]}"; do
        item="$(echo "$raw" | xargs)"
        item="$(normalize_lib_name "$item")"
        if ! is_valid_lib "$item"; then
            echo "Error: invalid lib '$raw'."
            usage
            exit 1
        fi
    done
fi

configure_desktop_toolchain() {
    local TARGET_ARCH=$1
    INSTALL_DIR="$PREBUILT_BASE/$TARGET_ARCH"
    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include"

    case "$TARGET_ARCH" in
        x86_64)
            CC="${CC_x86_64:-${CC:-gcc}}"
            CXX="${CXX_x86_64:-${CXX:-g++}}"
            AR="${AR_x86_64:-${AR:-ar}}"
            RANLIB="${RANLIB_x86_64:-${RANLIB:-ranlib}}"
            STRIP="${STRIP_x86_64:-${STRIP:-strip}}"
            ARCH_FLAGS="-m64"
            CMAKE_ARCH_FLAGS="-DCMAKE_SYSTEM_PROCESSOR=x86_64"
            AUTOTOOLS_HOST=""
            ;;
        aarch64)
            local CROSS_PREFIX="aarch64-linux-gnu-"
            CC="${CC_aarch64:-${CROSS_PREFIX}gcc}"
            CXX="${CXX_aarch64:-${CROSS_PREFIX}g++}"
            AR="${AR_aarch64:-${CROSS_PREFIX}ar}"
            RANLIB="${RANLIB_aarch64:-${CROSS_PREFIX}ranlib}"
            STRIP="${STRIP_aarch64:-${CROSS_PREFIX}strip}"
            ARCH_FLAGS=""
            CMAKE_ARCH_FLAGS="-DCMAKE_SYSTEM_NAME=Linux -DCMAKE_SYSTEM_PROCESSOR=aarch64 -DCMAKE_C_COMPILER=$CC -DCMAKE_CXX_COMPILER=$CXX"
            AUTOTOOLS_HOST="--host=aarch64-linux-gnu"
            ;;
        x86)
            CC="${CC_x86:-gcc}"
            CXX="${CXX_x86:-g++}"
            AR="${AR_x86:-ar}"
            RANLIB="${RANLIB_x86:-ranlib}"
            STRIP="${STRIP_x86:-strip}"
            ARCH_FLAGS="-m32"
            CMAKE_ARCH_FLAGS="-DCMAKE_SYSTEM_NAME=Linux -DCMAKE_SYSTEM_PROCESSOR=i686 -DCMAKE_C_COMPILER=$CC -DCMAKE_CXX_COMPILER=$CXX -DCMAKE_C_FLAGS=-m32 -DCMAKE_CXX_FLAGS=-m32"
            AUTOTOOLS_HOST="--host=i686-linux-gnu"
            ;;
        armv7)
            local CROSS_PREFIX="arm-linux-gnueabihf-"
            CC="${CC_armv7:-${CROSS_PREFIX}gcc}"
            CXX="${CXX_armv7:-${CROSS_PREFIX}g++}"
            AR="${AR_armv7:-${CROSS_PREFIX}ar}"
            RANLIB="${RANLIB_armv7:-${CROSS_PREFIX}ranlib}"
            STRIP="${STRIP_armv7:-${CROSS_PREFIX}strip}"
            ARCH_FLAGS="-march=armv7-a -mfpu=neon -mfloat-abi=hard"
            CMAKE_ARCH_FLAGS="-DCMAKE_SYSTEM_NAME=Linux -DCMAKE_SYSTEM_PROCESSOR=armv7 -DCMAKE_C_COMPILER=$CC -DCMAKE_CXX_COMPILER=$CXX"
            AUTOTOOLS_HOST="--host=arm-linux-gnueabihf"
            ;;
    esac

    CFLAGS="-fPIC $ARCH_FLAGS $DEP_OPT_FLAGS"
    CXXFLAGS="-fPIC $ARCH_FLAGS $DEP_OPT_FLAGS"
    CMAKE_COMMON_FLAGS="-Wno-dev -DCMAKE_POLICY_VERSION_MINIMUM=3.5 $CMAKE_ARCH_FLAGS"
}

clean_target_artifacts() {
    echo "Cleaning desktop target artifacts for: $TARGET_LIB..."
    local lib_list=()
    if [ "$TARGET_LIB" = "all" ]; then
        lib_list=("${ALL_DEPENDENCY_LIBS[@]}")
    else
        IFS=',' read -r -a raw_libs <<< "$TARGET_LIB"
        for raw in "${raw_libs[@]}"; do
            lib_list+=("$(normalize_lib_name "$(echo "$raw" | xargs)")")
        done
    fi

    for lib in "${lib_list[@]}"; do
        local PROJ=""
        case "$lib" in
            libsoxr)        PROJ="$ABSOLUTE_PATH/libsoxr" ;;
            mbedtls)        PROJ="$MBEDTLS_DIR" ;;
            ffmpeg)         PROJ="$ABSOLUTE_PATH/ffmpeg" ;;
            libopenmpt)     PROJ="$ABSOLUTE_PATH/libopenmpt" ;;
            libxmp)         PROJ="$ABSOLUTE_PATH/libxmp" ;;
            libayfly)       PROJ="$ABSOLUTE_PATH/ayfly" ;;
            ufmod)          PROJ="$ABSOLUTE_PATH/ufmod_c" ;;
            libvgm)         PROJ="$ABSOLUTE_PATH/libvgm" ;;
            libgme)         PROJ="$ABSOLUTE_PATH/libgme" ;;
            libresid)       PROJ="$ABSOLUTE_PATH/resid" ;;
            libresidfp)     PROJ="$ABSOLUTE_PATH/libresidfp" ;;
            libsidplayfp)   PROJ="$ABSOLUTE_PATH/libsidplayfp" ;;
            crsid)          PROJ="$ABSOLUTE_PATH/cRSID" ;;
            lazyusf2)       PROJ="$ABSOLUTE_PATH/lazyusf2" ;;
            psflib)         PROJ="$ABSOLUTE_PATH/psflib" ;;
            vio2sf)         PROJ="$ABSOLUTE_PATH/2sf/vio2sf/src/vio2sf" ;;
            fluidsynth)     PROJ="$ABSOLUTE_PATH/fluidsynth" ;;
            sc68)           PROJ="$ABSOLUTE_PATH/sc68" ;;
            libbinio)       PROJ="$ABSOLUTE_PATH/libbinio" ;;
            adplug)         PROJ="$ABSOLUTE_PATH/adplug" ;;
            libzakalwe)     PROJ="$ABSOLUTE_PATH/libzakalwe" ;;
            bencodetools)   PROJ="$ABSOLUTE_PATH/bencodetools" ;;
            vasm)           PROJ="$ABSOLUTE_PATH/vasm" ;;
            uade)           PROJ="$ABSOLUTE_PATH/uade" ;;
            hivelytracker)  PROJ="$ABSOLUTE_PATH/hivelytracker" ;;
            klystrack)      PROJ="$ABSOLUTE_PATH/klystrack" ;;
            furnace)        PROJ="$ABSOLUTE_PATH/furnace" ;;
            projectm)       PROJ="$ABSOLUTE_PATH/projectm" ;;
        esac

        [ -n "$PROJ" ] && rm -rf "$PROJ/build_desktop_${ARCH}" 2>/dev/null || true

        case "$lib" in
            libsoxr) rm -f "$INSTALL_DIR/lib/libsoxr.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/soxr"* 2>/dev/null || true ;;
            mbedtls) rm -f "$INSTALL_DIR/lib"/libmbed*.a 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/mbedtls" 2>/dev/null || true ;;
            ffmpeg) rm -f "$INSTALL_DIR/lib"/libav*.so* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include"/libav* 2>/dev/null || true ;;
            libopenmpt) rm -f "$INSTALL_DIR/lib/libopenmpt.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/libopenmpt" 2>/dev/null || true ;;
            libvgm) rm -f "$INSTALL_DIR/lib/libvgm"*.so* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/libvgm" 2>/dev/null || true ;;
            libgme) rm -f "$INSTALL_DIR/lib/libgme.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/gme" 2>/dev/null || true ;;
            libxmp) rm -f "$INSTALL_DIR/lib/libxmp.so"* 2>/dev/null || true; rm -f "$INSTALL_DIR/include/xmp.h" 2>/dev/null || true ;;
            ayfly) rm -f "$INSTALL_DIR/lib/libayfly.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/ayfly" 2>/dev/null || true ;;
            ufmod) rm -f "$INSTALL_DIR/lib/libufmod.a" 2>/dev/null || true; rm -f "$INSTALL_DIR/include/ufmod"*.h 2>/dev/null || true ;;
            libresid) rm -f "$INSTALL_DIR/lib/libresid.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/resid" 2>/dev/null || true ;;
            libresidfp) rm -f "$INSTALL_DIR/lib/libresidfp.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/residfp" 2>/dev/null || true ;;
            libsidplayfp) rm -f "$INSTALL_DIR/lib/libsidplayfp.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/sidplayfp" 2>/dev/null || true ;;
            crsid) rm -f "$INSTALL_DIR/lib/libcRSID.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/crsid" 2>/dev/null || true ;;
            lazyusf2) rm -f "$INSTALL_DIR/lib/liblazyusf"*.a 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/lazyusf2" 2>/dev/null || true ;;
            psflib) rm -f "$INSTALL_DIR/lib/libpsflib.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/psflib" 2>/dev/null || true ;;
            vio2sf) rm -f "$INSTALL_DIR/lib/libvio2sf.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/vio2sf" 2>/dev/null || true ;;
            fluidsynth) rm -f "$INSTALL_DIR/lib/libfluidsynth.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/fluidsynth" 2>/dev/null || true ;;
            sc68) rm -f "$INSTALL_DIR/lib/lib"*68.so* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/"*68 2>/dev/null || true ;;
            libbinio) rm -f "$INSTALL_DIR/lib/libbinio.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/libbinio" 2>/dev/null || true ;;
            adplug) rm -f "$INSTALL_DIR/lib/libadplug.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/adplug" 2>/dev/null || true ;;
            libzakalwe) rm -f "$INSTALL_DIR/lib/libzakalwe.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/zakalwe" 2>/dev/null || true ;;
            bencodetools) rm -f "$INSTALL_DIR/lib/libbencodetools.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/bencodetools" 2>/dev/null || true ;;
            uade) rm -f "$INSTALL_DIR/lib/libuade.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/uade" 2>/dev/null || true ;;
            hivelytracker) rm -f "$INSTALL_DIR/lib/libhivelytracker.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/hivelytracker" 2>/dev/null || true ;;
            klystrack) rm -f "$INSTALL_DIR/lib/libklystrack.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/klystrack" 2>/dev/null || true ;;
            furnace) rm -f "$INSTALL_DIR/lib/libfurnace.so"* "$INSTALL_DIR/lib/libfftw3.so"* "$INSTALL_DIR/lib/libfmt.so"* "$INSTALL_DIR/lib/libsndfile.so"* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/furnace" 2>/dev/null || true ;;
            projectm) rm -f "$INSTALL_DIR/lib/libprojectM"*.so* 2>/dev/null || true; rm -rf "$INSTALL_DIR/include/projectM"* 2>/dev/null || true ;;
        esac
    done

    if [ "$TARGET_LIB" = "all" ]; then
        rm -rf "$INSTALL_DIR" 2>/dev/null || true
        mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include"
    fi
    echo "Clean complete."
}

if [ "$FORCE_CLEAN" -eq 1 ]; then
    clean_target_artifacts
fi

build_ufmod() {
    local PROJECT_PATH="$ABSOLUTE_PATH/ufmod_c"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libufmod.a" ]; then return 0; fi
    echo "Building uFMOD for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR/obj"
    local SOURCES=("$PROJECT_PATH/src/ufmod_load.c" "$PROJECT_PATH/src/ufmod_play.c" "$PROJECT_PATH/src/ufmod_mix.c" "$PROJECT_PATH/src/ufmod_core.c")
    local objects=()
    for s in "${SOURCES[@]}"; do
        local obj="$BUILD_DIR/obj/$(basename "${s%.c}").o"
        "$CC" -fPIC -I"$PROJECT_PATH/include" -I"$PROJECT_PATH/src" $DEP_OPT_FLAGS -DUFMOD_RUNTIME_QUIRKS=1 -c "$s" -o "$obj"
        objects+=("$obj")
    done
    "$AR" rcs "$INSTALL_DIR/lib/libufmod.a" "${objects[@]}"
    cp "$PROJECT_PATH/include/ufmod.h" "$PROJECT_PATH/include/ufmod_config.h" "$INSTALL_DIR/include/"
}

build_libsoxr() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libsoxr"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libsoxr.so" ]; then return 0; fi
    echo "Building libsoxr for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS="$CFLAGS" -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DBUILD_SHARED_LIBS=ON -DBUILD_STATIC_LIBS=OFF -DBUILD_TESTS=OFF -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
}

build_mbedtls() {
    local PROJECT_PATH="$MBEDTLS_DIR"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libmbedtls.a" ]; then return 0; fi
    echo "Building mbedTLS for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS="$CFLAGS" -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DENABLE_TESTING=OFF -DENABLE_PROGRAMS=OFF -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
}

build_ffmpeg() {
    local PROJECT_PATH="$ABSOLUTE_PATH/ffmpeg"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$TARGET_LIB" = "all" ] && [ "${BUILD_FFMPEG_SOURCE:-0}" -ne 1 ]; then
        echo "Note: Using system FFmpeg for desktop. (Run '$0 ffmpeg' to build submodule ffmpeg from source)."
        return 0
    fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libavcodec.so" ]; then return 0; fi
    echo "Building shared FFmpeg from submodule for host..."
    mkdir -p "$BUILD_DIR"
    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true
        "$CC" -fPIC -c "$ABSOLUTE_PATH/../desktop/src/native/LibmCompatWrappers.c" -o "$BUILD_DIR/wrap_libm.o"
        ./configure --prefix="$INSTALL_DIR" --enable-shared --disable-static --disable-doc --disable-programs \
            --disable-avdevice --disable-avfilter --disable-swscale --disable-asm \
            --disable-encoders --disable-muxers \
            --disable-vaapi --disable-vdpau --disable-vulkan --disable-libdrm \
            --disable-cuda --disable-cuvid --disable-nvdec --disable-nvenc \
            --disable-dxva2 --disable-d3d11va --disable-videotoolbox \
            --extra-cflags="-fPIC $DEP_OPT_FLAGS" \
            --extra-ldflags="$BUILD_DIR/wrap_libm.o -L$INSTALL_DIR/lib -lm -Wl,--wrap,sqrtf -Wl,--wrap,atan2f -Wl,--wrap,log10f -Wl,--wrap,cosh -Wl,--wrap,sinh -Wl,--wrap,hypot"
        make -j"$NPROC"
        make install
    )
}

build_libopenmpt() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libopenmpt"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libopenmpt.so" ]; then return 0; fi
    echo "Building libopenmpt for host..."
    make -C "$PROJECT_PATH" clean >/dev/null 2>&1 || true
    make -C "$PROJECT_PATH" -j"$NPROC" bin/libopenmpt.so CONFIG=gcc CFLAGS="-fPIC $DEP_OPT_FLAGS" CXXFLAGS="-fPIC $DEP_OPT_FLAGS"
    cp "$PROJECT_PATH/bin/libopenmpt.so" "$INSTALL_DIR/lib/"
    mkdir -p "$INSTALL_DIR/include/libopenmpt"
    cp "$PROJECT_PATH/libopenmpt/"*.h "$PROJECT_PATH/libopenmpt/"*.hpp "$INSTALL_DIR/include/libopenmpt/" 2>/dev/null || true
}

build_libxmp() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libxmp"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libxmp.so" ]; then return 0; fi
    echo "Building libxmp for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS="$CFLAGS" -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DBUILD_SHARED=ON -DBUILD_STATIC=OFF -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
}

build_ayfly() {
    local PROJECT_PATH="$ABSOLUTE_PATH/ayfly"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libayfly.so" ]; then return 0; fi
    echo "Building ayfly for host..."
    if [ ! -f "$PROJECT_PATH/configure" ]; then
        (cd "$PROJECT_PATH" && autoreconf -vfi)
    fi
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR" "$INSTALL_DIR/include/ayfly"
    (
        cd "$BUILD_DIR"
        "$PROJECT_PATH/configure" \
            --prefix="$INSTALL_DIR" \
            --without-gui \
            --without-audio \
            CFLAGS="$CFLAGS" \
            CXXFLAGS="$CXXFLAGS"
        make -j"$NPROC" -C src/libayfly
    )
    local static_lib
    static_lib="$(find "$BUILD_DIR" -type f -name 'libayfly.a' | head -n 1)"
    if [ -z "$static_lib" ]; then
        echo "Error: libayfly static archive not found after build."
        return 1
    fi
    mkdir -p "$BUILD_DIR/.so_work"
    (
        cd "$BUILD_DIR/.so_work"
        "$AR" x "$static_lib"
        "$CXX" -shared -o "$BUILD_DIR/libayfly.so" -Wl,-soname,libayfly.so $DEP_OPT_FLAGS ./*.o
    )
    cp "$BUILD_DIR/libayfly.so" "$INSTALL_DIR/lib/libayfly.so"
    cp "$PROJECT_PATH/src/libayfly/ayfly.h" \
       "$PROJECT_PATH/src/libayfly/ayflyString.h" \
       "$PROJECT_PATH/src/libayfly/Filter3.h" \
       "$PROJECT_PATH/src/libayfly/ay.h" \
       "$PROJECT_PATH/src/libayfly/AbstractAudio.h" \
       "$INSTALL_DIR/include/ayfly/" 2>/dev/null || true
    cp "$PROJECT_PATH/src/libayfly/z80ex/include/"*.h "$INSTALL_DIR/include/ayfly/" 2>/dev/null || true
}

# Compiler wrapper appending -std=gnu++17 last, so it wins over any older
# -std the CMake standard machinery emits. The player libs need C++11+.
make_cxx17_wrapper() {
    printf '#!/bin/sh\nexec "%s" "$@" -std=gnu++17\n' "$CXX" > "$1/cxx17.sh"
    chmod +x "$1/cxx17.sh"
}

build_libvgm() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libvgm"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libvgm-player.so" ]; then return 0; fi
    echo "Building libvgm for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    make_cxx17_wrapper "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS="$CFLAGS" -DCMAKE_CXX_FLAGS="$CXXFLAGS -std=c++17" \
        -DCMAKE_CXX_COMPILER="$BUILD_DIR/cxx17.sh" \
        -DCMAKE_CXX_STANDARD=17 -DCMAKE_CXX_STANDARD_REQUIRED=ON \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DLIBRARY_TYPE=SHARED -DBUILD_LIBAUDIO=OFF -DBUILD_LIBEMU=ON -DBUILD_LIBPLAYER=ON \
        -DBUILD_TESTS=OFF -DBUILD_PLAYER=OFF -DBUILD_VGM2WAV=OFF -DUTIL_CHARSET_CONV=ON \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    # Log what the CMake standard machinery emits. The wrapper below
    # appends the effective standard last, so it wins regardless.
    grep '^CXX_FLAGS' "$BUILD_DIR/player/CMakeFiles/vgm-player.dir/flags.make" 2>/dev/null | grep -o '\-std=[^ ]*' | tr '\n' ' ' || true; echo
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
    mkdir -p "$INSTALL_DIR/include/vgm/player" "$INSTALL_DIR/include/vgm/utils" "$INSTALL_DIR/include/vgm/emu"
    cp "$PROJECT_PATH/player/"*.h "$INSTALL_DIR/include/vgm/player/" 2>/dev/null || true
    cp "$PROJECT_PATH/player/"*.hpp "$INSTALL_DIR/include/vgm/player/" 2>/dev/null || true
    cp "$PROJECT_PATH/utils/"*.h "$INSTALL_DIR/include/vgm/utils/" 2>/dev/null || true
    cp "$PROJECT_PATH/emu/"*.h "$INSTALL_DIR/include/vgm/emu/" 2>/dev/null || true
}

build_libgme() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libgme"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libgme.so" ]; then return 0; fi
    echo "Building libgme for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS="$CFLAGS" -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DBUILD_SHARED_LIBS=ON -DENABLE_FX=OFF \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
}

build_libresid() {
    local PROJECT_PATH="$ABSOLUTE_PATH/resid"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libresid.so" ]; then return 0; fi
    echo "Building libresid for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    (
        cd "$BUILD_DIR"
        "$PROJECT_PATH/configure" --prefix="$INSTALL_DIR" CFLAGS="$CFLAGS" CXXFLAGS="$CXXFLAGS"
        make -j"$NPROC"
        # Upstream builds a static archive only; link the shared
        # library ourselves, mirroring build_deps_android.sh.
        mkdir -p "$BUILD_DIR/.so_work"
        (
            cd "$BUILD_DIR/.so_work"
            "$AR" x "$BUILD_DIR/libresid.a"
            "$CXX" -shared -o "$BUILD_DIR/libresid.so" -Wl,-soname,libresid.so $CXXFLAGS ./*.o
        )
        mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include/resid"
        cp "$BUILD_DIR/libresid.so" "$INSTALL_DIR/lib/libresid.so"
        cp "$PROJECT_PATH/"*.h "$BUILD_DIR/siddefs.h" "$INSTALL_DIR/include/resid/" 2>/dev/null || true
    )
}

build_libresidfp() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libresidfp"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libresidfp.so" ]; then return 0; fi
    echo "Building libresidfp for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    (
        cd "$BUILD_DIR"
        "$PROJECT_PATH/configure" --prefix="$INSTALL_DIR" --enable-shared --disable-static CFLAGS="$CFLAGS" CXXFLAGS="$CXXFLAGS"
        make -j"$NPROC"
        make install
    )
}

build_libsidplayfp() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libsidplayfp"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libsidplayfp.so" ]; then return 0; fi
    apply_libsidplayfp_patches
    if [ ! -f "$INSTALL_DIR/lib/libresidfp.so" ]; then build_libresidfp; fi
    if [ ! -f "$INSTALL_DIR/lib/libresid.so" ]; then build_libresid; fi
    echo "Building libsidplayfp for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    (
        cd "$BUILD_DIR"
        PKG_CONFIG_PATH="$INSTALL_DIR/lib/pkgconfig" \
        RESIDFP_CFLAGS="-I$INSTALL_DIR/include" \
        RESIDFP_LIBS="-L$INSTALL_DIR/lib -lresidfp" \
        "$PROJECT_PATH/configure" --prefix="$INSTALL_DIR" --enable-shared --disable-static \
            --with-usbsid=no --with-exsid=no CFLAGS="$CFLAGS" CXXFLAGS="$CXXFLAGS"
        make -j"$NPROC"
        make install
    )
}

build_crsid() {
    local PROJECT_PATH="$ABSOLUTE_PATH/cRSID"
    local LIBCRSID_DIR="$PROJECT_PATH/libcRSID"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libcRSID.so" ]; then return 0; fi
    echo "Building cRSID for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include/crsid"
    "$CC" -c "$LIBCRSID_DIR/libcRSID.c" -o "$BUILD_DIR/libcRSID.o" -I"$LIBCRSID_DIR" -fPIC $DEP_OPT_FLAGS -DCRSID_LIBRARY
    "$CC" -shared -o "$INSTALL_DIR/lib/libcRSID.so" -Wl,-soname,libcRSID.so "$BUILD_DIR/libcRSID.o"
    cp "$LIBCRSID_DIR/libcRSID.h" "$LIBCRSID_DIR/Config.h" "$LIBCRSID_DIR/Optimize.h" "$INSTALL_DIR/include/crsid/"
}

build_lazyusf2() {
    local PROJECT_PATH="$ABSOLUTE_PATH/lazyusf2"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/liblazyusf2.a" ]; then return 0; fi
    echo "Building lazyusf2 for host..."
    mkdir -p "$INSTALL_DIR/include/lazyusf2"
    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true
        make -s -j"$NPROC" liblazyusf.a \
            CC="$CC" AR="$AR" CPU="x86_64" ARCH="64" \
            OPTFLAGS="$DEP_OPT_FLAGS" FLAGS_64="-fPIC" \
            OBJS_RECOMPILER_64="" OPTS_x86_64="" ROPTS_x86_64="-DARCH_MIN_SSE2"
    )
    cp "$PROJECT_PATH/liblazyusf.a" "$INSTALL_DIR/lib/liblazyusf2.a"
    cp "$PROJECT_PATH/liblazyusf.a" "$INSTALL_DIR/lib/liblazyusf.a"
    while IFS= read -r header_path; do
        local rel_path="${header_path#"$PROJECT_PATH"/}"
        mkdir -p "$INSTALL_DIR/include/lazyusf2/$(dirname "$rel_path")"
        cp "$header_path" "$INSTALL_DIR/include/lazyusf2/$rel_path"
    done < <(find "$PROJECT_PATH" -type f -name '*.h')
}

build_psflib() {
    local PROJECT_PATH="$ABSOLUTE_PATH/psflib"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libpsflib.so" ]; then return 0; fi
    echo "Building psflib for host..."
    mkdir -p "$INSTALL_DIR/include/psflib"
    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true
        make -s -j"$NPROC" libpsflib.a CC="$CC" AR="$AR" CFLAGS="-c -fPIC $DEP_OPT_FLAGS"
        local objs=()
        while IFS= read -r obj; do objs+=("$obj"); done < <(find "$PROJECT_PATH" -maxdepth 1 -name '*.o' | sort)
        "$CC" -shared -o "$PROJECT_PATH/libpsflib.so" -Wl,-soname,libpsflib.so -fPIC "${objs[@]}"
    )
    cp "$PROJECT_PATH/libpsflib.so" "$INSTALL_DIR/lib/libpsflib.so"
    cp "$PROJECT_PATH/psflib.h" "$PROJECT_PATH/psf2fs.h" "$INSTALL_DIR/include/"
    cp "$PROJECT_PATH/psflib.h" "$PROJECT_PATH/psf2fs.h" "$INSTALL_DIR/include/psflib/"
}

build_vio2sf() {
    local PROJECT_PATH="$ABSOLUTE_PATH/2sf/vio2sf/src/vio2sf"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libvio2sf.so" ]; then return 0; fi
    if [ ! -f "$INSTALL_DIR/lib/libpsflib.so" ]; then build_psflib; fi
    echo "Building vio2sf for host..."
    mkdir -p "$INSTALL_DIR/include/vio2sf/desmume"
    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true
        make -s -j"$NPROC" libvio2sf.a CC="$CC" AR="$AR" CFLAGS="-c -fPIC $DEP_OPT_FLAGS" CXXFLAGS="-c -fPIC $DEP_OPT_FLAGS" \
            OPTS="-O3 -I. -DBARRAY_DECORATE=TWOSF -DRESAMPLER_DECORATE=TWOSF $DEP_OPT_FLAGS"
        local objs=()
        while IFS= read -r obj; do objs+=("$obj"); done < <(find "$PROJECT_PATH" -name '*.o' | sort)
        "$CXX" -shared -o "$PROJECT_PATH/libvio2sf.so" -Wl,-soname,libvio2sf.so -fPIC "${objs[@]}"
    )
    cp "$PROJECT_PATH/libvio2sf.so" "$INSTALL_DIR/lib/libvio2sf.so"
    cp "$PROJECT_PATH/desmume/"*.h "$INSTALL_DIR/include/vio2sf/desmume/"
}

build_fluidsynth() {
    local PROJECT_PATH="$ABSOLUTE_PATH/fluidsynth"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libfluidsynth.so" ]; then return 0; fi
    echo "Building fluidsynth for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS="$CFLAGS" -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DBUILD_SHARED_LIBS=ON -Denable-pulseaudio=OFF -Denable-alsa=OFF -Denable-systemd=OFF \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
}

build_sc68() {
    local PROJECT_PATH="$ABSOLUTE_PATH/sc68"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libsc68.so" ]; then return 0; fi
    echo "Building sc68 for host..."
    find "$PROJECT_PATH" \( -name "*.o" -o -name "*.lo" -o -name "*.la" -o -name "*.a" \) -delete 2>/dev/null || true
    find "$PROJECT_PATH" -name ".libs" -type d -exec rm -rf {} + 2>/dev/null || true
    mkdir -p "$PROJECT_PATH/unice68/m4" "$PROJECT_PATH/file68/m4" "$PROJECT_PATH/libsc68/m4"
    ln -sfn ../vcversion.sh "$PROJECT_PATH/unice68/vcversion.sh"
    ln -sfn ../vcversion.sh "$PROJECT_PATH/file68/vcversion.sh"
    ln -sfn ../vcversion.sh "$PROJECT_PATH/libsc68/vcversion.sh"
    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include"
    (
        cd "$PROJECT_PATH/as68"
        $CC -std=gnu89 -O2 -DPACKAGE_VERSION='"build-deps"' -DPACKAGE_URL='"https://sourceforge.net/p/sc68"' \
            -o as68 as68.c error.c expression.c opcode.c word.c
    )
    (
        cd "$PROJECT_PATH/unice68"
        [ ! -f configure ] && autoreconf -vfi
        ./configure --prefix="$INSTALL_DIR" --enable-shared --disable-static CFLAGS="$CFLAGS"
        make -j"$NPROC" && make install
    )
    (
        cd "$PROJECT_PATH/file68"
        [ ! -f configure ] && autoreconf -vfi
        ./configure --prefix="$INSTALL_DIR" --enable-shared --disable-static \
            UNICE68_CFLAGS="-I$INSTALL_DIR/include" UNICE68_LIBS="-L$INSTALL_DIR/lib -lunice68" CFLAGS="$CFLAGS"
        make -j"$NPROC" && make install
    )
    (
        cd "$PROJECT_PATH/libsc68"
        [ ! -f configure ] && autoreconf -vfi
        PATH="$PROJECT_PATH/as68:$PATH" ./configure --prefix="$INSTALL_DIR" --enable-shared --disable-static \
            UNICE68_CFLAGS="-I$INSTALL_DIR/include" UNICE68_LIBS="-L$INSTALL_DIR/lib -lunice68" \
            FILE68_CFLAGS="-I$INSTALL_DIR/include" FILE68_LIBS="-L$INSTALL_DIR/lib -lfile68" CFLAGS="$CFLAGS"
        PATH="$PROJECT_PATH/as68:$PATH" make -j"$NPROC"
        PATH="$PROJECT_PATH/as68:$PATH" make install
    )
}

build_libbinio() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libbinio"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libbinio.so" ]; then return 0; fi
    echo "Building libbinio for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS="$CFLAGS" -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DBUILD_SHARED_LIBS=ON -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
}

build_adplug() {
    local PROJECT_PATH="$ABSOLUTE_PATH/adplug"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libadplug.so" ]; then return 0; fi
    if [ ! -f "$INSTALL_DIR/lib/libbinio.so" ]; then build_libbinio; fi
    echo "Building adplug for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    make_cxx17_wrapper "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS="$CFLAGS" -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
        -DCMAKE_CXX_COMPILER="$BUILD_DIR/cxx17.sh" \
        -DCMAKE_CXX_STANDARD=17 -DCMAKE_CXX_STANDARD_REQUIRED=ON \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DBUILD_SHARED_LIBS=ON \
        -DCMAKE_PREFIX_PATH="$INSTALL_DIR" -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
}

build_vasm_host() {
    local PROJECT_PATH="$ABSOLUTE_PATH/vasm"
    if [ ! -d "$PROJECT_PATH" ]; then
        echo "vasm source not found at $PROJECT_PATH (skipping)."
        return 0
    fi
    if [ "$FORCE_CLEAN" -eq 1 ]; then
        (
            cd "$PROJECT_PATH"
            make --no-print-directory clean >/dev/null 2>&1 || true
        )
    fi
    if [ -x "$PROJECT_PATH/vasmm68k_mot" ]; then
        return 0
    fi
    echo "Building vasm host tool..."
    (
        cd "$PROJECT_PATH"
        make --no-print-directory -j"$NPROC" CPU=m68k SYNTAX=mot
    )
}

build_libzakalwe() {
    local PROJECT_PATH="$ABSOLUTE_PATH/libzakalwe"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libzakalwe.so" ] && [ -f "$INSTALL_DIR/include/zakalwe/string.h" ]; then return 0; fi
    echo "Building libzakalwe for host..."
    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include/zakalwe"
    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true
        ./configure
        make --no-print-directory V=0 -j"$NPROC" \
            AR="ar" \
            CC="$CC" \
            CFLAGS="$CFLAGS -I include -g -pthread -D_DEFAULT_SOURCE" \
            static_pack.o
    )
    if [ ! -f "$PROJECT_PATH/static_pack.o" ]; then
        echo "Error: libzakalwe object payload not found after build."
        return 1
    fi
    mkdir -p "$PROJECT_PATH/.so_work"
    (
        cd "$PROJECT_PATH/.so_work"
        rm -f ./*.o
        ar x "$PROJECT_PATH/static_pack.o"
        "$CC" -shared -o "$INSTALL_DIR/lib/libzakalwe.so" -Wl,-soname,libzakalwe.so -fPIC ./*.o
    )
    rm -rf "$PROJECT_PATH/.so_work"
    cp "$PROJECT_PATH/include/zakalwe/"*.h "$INSTALL_DIR/include/zakalwe/" 2>/dev/null || true
}

build_bencodetools() {
    local PROJECT_PATH="$ABSOLUTE_PATH/bencodetools"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libbencodetools.so" ] && [ -f "$INSTALL_DIR/include/bencodetools/bencode.h" ]; then return 0; fi
    echo "Building bencodetools for host..."
    mkdir -p "$INSTALL_DIR/lib" "$INSTALL_DIR/include/bencodetools"
    (
        cd "$PROJECT_PATH"
        make clean >/dev/null 2>&1 || true
        ./configure --prefix="$INSTALL_DIR" --without-python --c-compiler="$CC"
        make --no-print-directory V=0 -j"$NPROC" compile-c
    )
    if [ ! -f "$PROJECT_PATH/bencode.o" ]; then
        echo "Error: bencodetools object payload not found after build."
        return 1
    fi
    "$CC" -shared -o "$INSTALL_DIR/lib/libbencodetools.so" -Wl,-soname,libbencodetools.so -fPIC "$PROJECT_PATH/bencode.o"
    cp "$PROJECT_PATH/include/bencodetools/"*.h "$INSTALL_DIR/include/bencodetools/" 2>/dev/null || true
}

build_uade() {
    local PROJECT_PATH="$ABSOLUTE_PATH/uade"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libuade.so" ] && [ -f "$INSTALL_DIR/lib/uade/uadecore" ]; then return 0; fi
    build_vasm_host
    if [ ! -f "$INSTALL_DIR/lib/libzakalwe.so" ]; then build_libzakalwe; fi
    if [ ! -f "$INSTALL_DIR/lib/libbencodetools.so" ]; then build_bencodetools; fi
    echo "Building uade for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    local UADE_DEPS="$BUILD_DIR/deps_prefix"
    mkdir -p "$UADE_DEPS/lib" "$UADE_DEPS/include" "$BUILD_DIR/tools"
    ln -sfn "$INSTALL_DIR/lib/libzakalwe.so" "$UADE_DEPS/lib/libzakalwe.so"
    ln -sfn "$INSTALL_DIR/lib/libbencodetools.so" "$UADE_DEPS/lib/libbencodetools.so"
    ln -sfn "$INSTALL_DIR/include/zakalwe" "$UADE_DEPS/include/zakalwe"
    ln -sfn "$INSTALL_DIR/include/bencodetools" "$UADE_DEPS/include/bencodetools"
    cat > "$BUILD_DIR/tools/vasm.vasmm68k-mot" <<EOF
#!/usr/bin/env bash
exec "$ABSOLUTE_PATH/vasm/vasmm68k_mot" "\$@"
EOF
    chmod +x "$BUILD_DIR/tools/vasm.vasmm68k-mot"
    (
        cd "$BUILD_DIR"
        PATH="$BUILD_DIR/tools:$PATH" "$PROJECT_PATH/configure" \
            --srcdir="$PROJECT_PATH" \
            --prefix="$INSTALL_DIR" \
            --pkg-config=false \
            --without-uade123 \
            --without-uadesimple \
            --without-uadefs \
            --without-write-audio \
            --without-avx2 \
            --bencode-tools-prefix="$UADE_DEPS" \
            --libzakalwe-prefix="$UADE_DEPS" \
            CFLAGS="$CFLAGS" \
            LDFLAGS="-L$INSTALL_DIR/lib -Wl,-rpath,$INSTALL_DIR/lib"
        PATH="$BUILD_DIR/tools:$PATH" make --no-print-directory V=0 -j"$NPROC" staticlibuade uadecore score
    )
    if [ ! -f "$BUILD_DIR/src/frontends/common/libuade.a" ]; then
        echo "Error: uade static library not found after build."
        return 1
    fi
    "$CC" -shared -o "$INSTALL_DIR/lib/libuade.so" -Wl,-soname,libuade.so -fPIC \
        -Wl,--whole-archive "$BUILD_DIR/src/frontends/common/libuade.a" -Wl,--no-whole-archive \
        -L"$INSTALL_DIR/lib" -lzakalwe -lbencodetools -Wl,-rpath,"$INSTALL_DIR/lib"
    mkdir -p "$INSTALL_DIR/include/uade" "$INSTALL_DIR/lib/uade" "$INSTALL_DIR/share/uade"
    cp "$BUILD_DIR/src/frontends/include/uade/"*.h "$INSTALL_DIR/include/uade/" 2>/dev/null || true
    cp "$BUILD_DIR/src/uadecore" "$INSTALL_DIR/lib/uade/uadecore"
    cp "$BUILD_DIR/amigasrc/score/score" "$INSTALL_DIR/share/uade/score"
    cp "$PROJECT_PATH/uaerc" "$INSTALL_DIR/share/uade/uaerc"
    cp "$PROJECT_PATH/eagleplayer.conf" "$INSTALL_DIR/share/uade/eagleplayer.conf"
    rm -rf "$INSTALL_DIR/share/uade/players"
    cp -R "$PROJECT_PATH/players" "$INSTALL_DIR/share/uade/players"
}

build_hivelytracker() {
    local PROJECT_PATH="$ABSOLUTE_PATH/hivelytracker"
    local REPLAYER_DIR="$PROJECT_PATH/Replayer_Windows"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libhivelytracker.so" ]; then return 0; fi
    echo "Building hivelytracker for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include/hivelytracker"
    # -Ofast implies -fno-semantic-interposition on GCC, which misresolves the
    # replayer's tentative-definition tables (period_tab reads as zeros, so every
    # note clamps to the 0x71 minimum period). Keep interposition on.
    "$CC" -c "$REPLAYER_DIR/hvl_replay.c" -o "$BUILD_DIR/hvl_replay.o" -fPIC -fcommon -fsigned-char -fsemantic-interposition $DEP_OPT_FLAGS
    "$CC" -c "$REPLAYER_DIR/hvl_tables.c" -o "$BUILD_DIR/hvl_tables.o" -fPIC -fcommon -fsigned-char -fsemantic-interposition $DEP_OPT_FLAGS
    "$CC" -shared -o "$INSTALL_DIR/lib/libhivelytracker.so" -Wl,-soname,libhivelytracker.so "$BUILD_DIR/hvl_replay.o" "$BUILD_DIR/hvl_tables.o"
    cp "$REPLAYER_DIR/hvl_replay.h" "$REPLAYER_DIR/hvl_tables.h" "$INSTALL_DIR/include/hivelytracker/"
}

build_klystrack() {
    local PROJECT_PATH="$ABSOLUTE_PATH/klystrack"
    local KLYSTRON_PATH="$PROJECT_PATH/klystron"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    local SDL_SHIM_DIR="$BUILD_DIR/sdl_compat"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ ! -f "$KLYSTRON_PATH/src/lib/ksnd.c" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libklystrack.so" ]; then return 0; fi
    echo "Building klystrack for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR" "$SDL_SHIM_DIR" "$INSTALL_DIR/lib" "$INSTALL_DIR/include/klystrack"
    cat > "$SDL_SHIM_DIR/SDL.h" <<'EOF'
#ifndef SILICONPLAYER_KLYSTRACK_SDL_H
#define SILICONPLAYER_KLYSTRACK_SDL_H
#include <stddef.h>
#include <stdint.h>
typedef uint8_t Uint8;
typedef int8_t Sint8;
typedef uint16_t Uint16;
typedef int16_t Sint16;
typedef uint32_t Uint32;
typedef int32_t Sint32;
typedef uint64_t Uint64;
typedef int64_t Sint64;
typedef struct SDL_mutex SDL_mutex;
typedef struct SDL_RWops SDL_RWops;
typedef struct SDL_Surface SDL_Surface;
typedef struct SDL_Texture SDL_Texture;
typedef struct SDL_Window SDL_Window;
typedef struct SDL_Renderer SDL_Renderer;
typedef struct SDL_Cursor SDL_Cursor;
typedef Uint32 SDL_TimerID;
typedef struct SDL_Keysym { Uint32 scancode; Uint32 sym; Uint16 mod; Uint32 unused; } SDL_Keysym;
typedef struct SDL_KeyboardEvent { Uint32 type; Uint32 timestamp; Uint32 windowID; Uint8 state; Uint8 repeat; Uint8 padding2; Uint8 padding3; SDL_Keysym keysym; } SDL_KeyboardEvent;
typedef struct SDL_Rect { int x; int y; int w; int h; } SDL_Rect;
typedef struct SDL_Event SDL_Event;
typedef struct SDL_AudioSpec { int freq; Uint16 format; Uint8 channels; Uint8 silence; Uint16 samples; Uint16 padding; Uint32 size; void (*callback)(void *userdata, Uint8 *stream, int len); void *userdata; } SDL_AudioSpec;
#define AUDIO_S16SYS 0x8010
Uint32 SDL_GetTicks(void);
void SDL_Delay(Uint32 ms);
SDL_mutex* SDL_CreateMutex(void);
void SDL_DestroyMutex(SDL_mutex* mutex);
int SDL_LockMutex(SDL_mutex* mutex);
int SDL_UnlockMutex(SDL_mutex* mutex);
size_t SDL_RWread(SDL_RWops* context, void* ptr, size_t size, size_t maxnum);
Sint64 SDL_RWseek(SDL_RWops* context, Sint64 offset, int whence);
Sint64 SDL_RWtell(SDL_RWops* context);
int SDL_RWclose(SDL_RWops* context);
SDL_RWops* SDL_RWFromMem(void* mem, int size);
SDL_RWops* SDL_RWFromFP(void* fp, int autoclose);
SDL_RWops* SDL_RWFromFile(const char* file, const char* mode);
SDL_RWops* SDL_AllocRW(void);
const char* SDL_GetError(void);
int SDL_OpenAudio(void* desired, void* obtained);
void SDL_PauseAudio(int pauseOn);
void SDL_CloseAudio(void);
#endif
EOF
    cat > "$SDL_SHIM_DIR/SDL_rwops.h" <<'EOF'
#ifndef SILICONPLAYER_KLYSTRACK_SDL_RWOPS_H
#define SILICONPLAYER_KLYSTRACK_SDL_RWOPS_H
#include "SDL.h"
#endif
EOF
    cat > "$SDL_SHIM_DIR/SDL_endian.h" <<'EOF'
#ifndef SILICONPLAYER_KLYSTRACK_SDL_ENDIAN_H
#define SILICONPLAYER_KLYSTRACK_SDL_ENDIAN_H
#include "SDL.h"
static inline Uint16 SDL_Swap16(Uint16 value) { return (Uint16)((value << 8) | (value >> 8)); }
static inline Uint32 SDL_Swap32(Uint32 value) { return ((value & 0x000000FFu) << 24) | ((value & 0x0000FF00u) << 8) | ((value & 0x00FF0000u) >> 8) | ((value & 0xFF000000u) >> 24); }
#define SDL_SwapLE16(X) ((Uint16)(X))
#define SDL_SwapLE32(X) ((Uint32)(X))
#endif
EOF
    cat > "$SDL_SHIM_DIR/SDL_shim.c" <<'EOF'
#include "SDL.h"
#include <stdlib.h>
#include <pthread.h>
struct SDL_mutex { pthread_mutex_t mutex; };
SDL_mutex *SDL_CreateMutex(void) { SDL_mutex *m = (SDL_mutex *)malloc(sizeof(SDL_mutex)); if (m) { pthread_mutex_init(&m->mutex, NULL); } return m; }
void SDL_DestroyMutex(SDL_mutex *m) { if (m) { pthread_mutex_destroy(&m->mutex); free(m); } }
int SDL_LockMutex(SDL_mutex *m) { if (!m) return -1; return pthread_mutex_lock(&m->mutex); }
int SDL_UnlockMutex(SDL_mutex *m) { if (!m) return -1; return pthread_mutex_unlock(&m->mutex); }
int SDL_OpenAudio(void *desired, void *obtained) { return -1; }
void SDL_CloseAudio(void) {}
void SDL_PauseAudio(int pause_on) {}
EOF
    "$CC" -c "$SDL_SHIM_DIR/SDL_shim.c" -o "$BUILD_DIR/SDL_shim.o" -I"$SDL_SHIM_DIR" -fPIC $DEP_OPT_FLAGS
    local common_flags="-fPIC $DEP_OPT_FLAGS -include math.h -DM_PI=3.14159265358979323846 -DSTANDALONE_COMPILE -DNOSDL_MIXER -DSTEREOOUTPUT -DUSESDLMUTEXES -I$SDL_SHIM_DIR -I$KLYSTRON_PATH/src"
    local src
    for src in "$KLYSTRON_PATH"/src/snd/*.c "$KLYSTRON_PATH"/src/lib/ksnd.c; do
        local obj="$BUILD_DIR/$(basename "$src" .c).o"
        "$CC" -c "$src" -o "$obj" $common_flags
    done
    "$CC" -shared -o "$INSTALL_DIR/lib/libklystrack.so" -Wl,-soname,libklystrack.so "$BUILD_DIR"/*.o
    cp "$KLYSTRON_PATH/src/lib/ksnd.h" "$INSTALL_DIR/include/klystrack/"
}

build_furnace() {
    local PROJECT_PATH="$ABSOLUTE_PATH/furnace"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libfurnace.so" ]; then return 0; fi
    echo "Building furnace for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="$CFLAGS -include $ABSOLUTE_PATH/glibc-compat-symver.h" \
        -DCMAKE_CXX_FLAGS="$CXXFLAGS -include $ABSOLUTE_PATH/glibc-compat-symver.h" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DCMAKE_DISABLE_PRECOMPILE_HEADERS=ON \
        -DBUILD_SHARED_LIBS=ON \
        -DBUILD_GUI=OFF \
        -DUSE_SDL2=OFF \
        -DUSE_SNDFILE=ON \
        -DENABLE_PACKAGE_CONFIG=OFF \
        -DINSTALL_PKGCONFIG=OFF \
        -DWITH_LOCALE=OFF \
        -DUSE_MOMO=OFF \
        -DUSE_RTMIDI=OFF \
        -DUSE_BACKWARD=OFF \
        -DWITH_OGG=OFF \
        -DWITH_MPEG=OFF \
        -DWITH_JACK=OFF \
        -DWITH_PORTAUDIO=OFF \
        -DWITH_RENDER_SDL=OFF \
        -DWITH_RENDER_OPENGL=OFF \
        -DWITH_RENDER_OPENGL1=OFF \
        -DWITH_RENDER_DX11=OFF \
        -DWITH_RENDER_DX9=OFF \
        -DWITH_RENDER_METAL=OFF \
        -DUSE_GLES=OFF \
        -DUSE_FREETYPE=OFF \
        -DWITH_DEMOS=OFF \
        -DWITH_INSTRUMENTS=OFF \
        -DWITH_WAVETABLES=OFF \
        -DNO_INTRO=ON \
        -DWARNINGS_ARE_ERRORS=OFF \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" --target furnace -j"$NPROC"
    local BUILT_LIB="$(find "$BUILD_DIR" -type f -name 'libfurnace.so' | head -n 1)"
    if [ -z "$BUILT_LIB" ] || [ ! -f "$BUILT_LIB" ]; then
        echo "Error: furnace shared library not found after build."
        return 1
    fi
    cp "$BUILT_LIB" "$INSTALL_DIR/lib/libfurnace.so"
    for vendored in fftw/libfftw3 fmt/libfmt libsndfile-modified/libsndfile; do
        if ! cp -d "$BUILD_DIR/extern/${vendored}.so"* "$INSTALL_DIR/lib/"; then
            echo "Error: furnace vendored dep $vendored not found after build."
            return 1
        fi
    done
    mkdir -p "$INSTALL_DIR/include/furnace/engine" "$INSTALL_DIR/include/furnace/audio"
    cp "$PROJECT_PATH/src/"*.h "$INSTALL_DIR/include/furnace/" 2>/dev/null || true
    cp "$PROJECT_PATH/src/engine/"*.h "$INSTALL_DIR/include/furnace/engine/" 2>/dev/null || true
    cp "$PROJECT_PATH/src/audio/"*.h "$INSTALL_DIR/include/furnace/audio/" 2>/dev/null || true
}

build_projectm() {
    local PROJECT_PATH="$ABSOLUTE_PATH/projectm"
    local BUILD_DIR="$PROJECT_PATH/build_desktop_${ARCH}"
    if [ ! -d "$PROJECT_PATH" ]; then return 0; fi
    if [ "$FORCE_CLEAN" -ne 1 ] && [ -f "$INSTALL_DIR/lib/libprojectM-4.so" ]; then return 0; fi
    echo "Building projectM for host..."
    rm -rf "$BUILD_DIR" && mkdir -p "$BUILD_DIR"
    cmake $CMAKE_COMMON_FLAGS -S "$PROJECT_PATH" -B "$BUILD_DIR" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_FLAGS="$CFLAGS" \
        -DCMAKE_CXX_FLAGS="$CXXFLAGS" \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DBUILD_SHARED_LIBS=ON \
        -DENABLE_SYSTEM_GLM=OFF \
        -DENABLE_SYSTEM_PROJECTM_EVAL=OFF \
        -DENABLE_PLAYLIST=OFF \
        -DENABLE_DEBUG_POSTFIX=OFF \
        -DENABLE_BOOST_FILESYSTEM=OFF \
        -DBUILD_TESTING=OFF \
        -DBUILD_DOCS=OFF \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR"
    cmake --build "$BUILD_DIR" -j"$NPROC"
    cmake --install "$BUILD_DIR"
    if [ ! -f "$INSTALL_DIR/lib/libprojectM-4.so" ]; then
        local BUILT_LIB="$(find "$BUILD_DIR" -type f -name "libprojectM-4.so" | head -n 1)"
        if [ -n "$BUILT_LIB" ] && [ -f "$BUILT_LIB" ]; then
            cp "$BUILT_LIB" "$INSTALL_DIR/lib/libprojectM-4.so"
        fi
    fi
}

# Run build targets
for ARCH in "${TARGET_ARCHES[@]}"; do
    configure_desktop_toolchain "$ARCH"

    echo "========================================"
    echo "SiliconPlayer Desktop Dependency Build ($ARCH)"
    echo "Target: $TARGET_LIB"
    echo "Install prefix: $INSTALL_DIR"
    echo "========================================"

    if [ "$FORCE_CLEAN" -eq 1 ]; then
        clean_target_artifacts
    fi

    if target_has_lib "libsidplayfp"; then
        apply_libsidplayfp_patches
    fi

    if target_has_lib "vasm"; then
        build_vasm_host
    fi

    if target_has_lib "libsoxr"; then build_libsoxr; fi
    if target_has_lib "mbedtls"; then build_mbedtls; fi
    if target_has_lib "ffmpeg"; then build_ffmpeg; fi
    if target_has_lib "libopenmpt"; then build_libopenmpt; fi
    if target_has_lib "libxmp"; then build_libxmp; fi
    if target_has_lib "libayfly"; then build_ayfly; fi
    if target_has_lib "ufmod"; then build_ufmod; fi
    if target_has_lib "libvgm"; then build_libvgm; fi
    if target_has_lib "libgme"; then build_libgme; fi
    if target_has_lib "libresid"; then build_libresid; fi
    if target_has_lib "libresidfp"; then build_libresidfp; fi
    if target_has_lib "libsidplayfp"; then build_libsidplayfp; fi
    if target_has_lib "crsid"; then build_crsid; fi
    if target_has_lib "lazyusf2"; then build_lazyusf2; fi
    if target_has_lib "psflib"; then build_psflib; fi
    if target_has_lib "vio2sf"; then build_vio2sf; fi
    if target_has_lib "fluidsynth"; then build_fluidsynth; fi
    if target_has_lib "sc68"; then build_sc68; fi
    if target_has_lib "libbinio"; then build_libbinio; fi
    if target_has_lib "adplug"; then build_adplug; fi
    if target_has_lib "libzakalwe"; then build_libzakalwe; fi
    if target_has_lib "bencodetools"; then build_bencodetools; fi
    if target_has_lib "uade"; then build_uade; fi
    if target_has_lib "hivelytracker"; then build_hivelytracker; fi
    if target_has_lib "klystrack"; then build_klystrack; fi
    if target_has_lib "furnace"; then build_furnace; fi
    if target_has_lib "projectm"; then build_projectm; fi

    echo "========================================"
    echo "Desktop Dependency Build Complete for $ARCH!"
    echo "Installed to: $INSTALL_DIR"
    echo "========================================"
done
