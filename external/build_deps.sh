#!/bin/bash
set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

source "$SCRIPT_DIR/build_deps_common.sh"

print_help() {
    echo "Usage: $0 <target> [lib[,lib2,...]] [clean]"
    echo ""
    echo "Targets:"
    echo "  desktop [arch]  Build desktop prebuilts (default arch: main)"
    echo "  android [abi]   Build Android prebuilts (default abi: all)"
    echo "  Single-word forms: desktop_<arch>, android_<abi> ('_' or '-' both work),"
    echo "    e.g. desktop_x86_64, desktop_legacy, android_arm64-v8a, android_all, android_legacy"
    echo "  Bare '<abi> [lib] [clean]' is passed straight to build_deps_android.sh"
    echo ""
    echo "  Desktop ARCH: main (x86_64, aarch64), legacy (armv7, x86),"
    echo "    or a single arch: x86_64, aarch64, x86, armv7"
    echo "  Android ABI: all, all_legacy, arm64-v8a, armeabi-v7a, x86_64, x86"
    echo "  LIB: $(all_libs_csv)"
    echo "  Aliases: $LIB_ALIASES_HINT"
    echo "  clean (optional): force rebuild (bypass already-built skip checks)"
    echo ""
    echo "Examples:"
    echo "  $0 desktop x86_64 ffmpeg clean"
    echo "  $0 android arm64-v8a libupse clean"
    echo "  $0 android_all libopenmpt,libxmp"
}

case "${1:-}" in
    ""|-h|--help)
        print_help
        exit 0
        ;;
esac

case "$1" in
    # Desktop targets
    desktop_x86_64|desktop-x86_64)
        exec "$SCRIPT_DIR/build_deps_desktop.sh" x86_64 "${@:2}"
        ;;
    desktop_aarch64|desktop-aarch64|desktop_arm64|desktop-arm64|desktop_armv8|desktop-armv8)
        exec "$SCRIPT_DIR/build_deps_desktop.sh" aarch64 "${@:2}"
        ;;
    desktop_legacy|desktop-legacy)
        exec "$SCRIPT_DIR/build_deps_desktop.sh" legacy "${@:2}"
        ;;
    desktop_x86|desktop-x86|desktop_i686|desktop-i686)
        exec "$SCRIPT_DIR/build_deps_desktop.sh" x86 "${@:2}"
        ;;
    desktop_armv7|desktop-armv7|desktop_armhf|desktop-armhf)
        exec "$SCRIPT_DIR/build_deps_desktop.sh" armv7 "${@:2}"
        ;;
    desktop|--desktop)
        case "$2" in
            x86_64|aarch64|arm64|legacy|x86|i686|armv7|armhf)
                exec "$SCRIPT_DIR/build_deps_desktop.sh" "$2" "${@:3}"
                ;;
            *)
                exec "$SCRIPT_DIR/build_deps_desktop.sh" main "${@:2}"
                ;;
        esac
        ;;

    # Android explicit targets
    android_x86_64|android-x86_64)
        exec "$SCRIPT_DIR/build_deps_android.sh" x86_64 "${@:2}"
        ;;
    android_arm64|android-arm64|android_arm64-v8a|android-arm64-v8a)
        exec "$SCRIPT_DIR/build_deps_android.sh" arm64-v8a "${@:2}"
        ;;
    android_armv7|android-armv7|android_armeabi-v7a|android-armeabi-v7a)
        exec "$SCRIPT_DIR/build_deps_android.sh" armeabi-v7a "${@:2}"
        ;;
    android_x86|android-x86)
        exec "$SCRIPT_DIR/build_deps_android.sh" x86 "${@:2}"
        ;;
    android_legacy|android-legacy)
        exec "$SCRIPT_DIR/build_deps_android.sh" all_legacy "${@:2}"
        ;;
    android_all|android-all)
        exec "$SCRIPT_DIR/build_deps_android.sh" all "${@:2}"
        ;;
    android|--android)
        case "$2" in
            x86_64|x86|arm64|arm64-v8a|armv7|armeabi-v7a|all|all_legacy|legacy)
                local_abi="$2"
                [ "$local_abi" = "legacy" ] && local_abi="all_legacy"
                [ "$local_abi" = "arm64" ] && local_abi="arm64-v8a"
                [ "$local_abi" = "armv7" ] && local_abi="armeabi-v7a"
                exec "$SCRIPT_DIR/build_deps_android.sh" "$local_abi" "${@:3}"
                ;;
            *)
                exec "$SCRIPT_DIR/build_deps_android.sh" all "${@:2}"
                ;;
        esac
        ;;

    # Classic bare Android targets (for 100% backward compatibility)
    *)
        exec "$SCRIPT_DIR/build_deps_android.sh" "$@"
        ;;
esac
