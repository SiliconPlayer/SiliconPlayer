#!/bin/sh
# Packs the release distributable into a portable .tar.gz bare package.
# Usage: ./package-tarball.sh after :desktop:createReleaseDistributable.
set -eu
cd "$(dirname "$0")"

APP_PKG="build/compose/binaries/main-release/app/SiliconPlayer"
OUT_DIR="build/compose/binaries/main-release"
VERSION="$(grep -oP '^siliconplayer\.version=\K.+' ../gradle.properties | head -1)"
VERSION="${VERSION:-0.1.0}"
SHA="$(git rev-parse --short HEAD 2>/dev/null || echo nogit)"

if [ ! -x "$APP_PKG/bin/SiliconPlayer" ]; then
    echo "missing $APP_PKG: run ./gradlew :desktop:createReleaseDistributable first" >&2
    exit 1
fi
if [ ! -f "$APP_PKG/lib/app/libsiliconplayer_desktop.so" ]; then
    echo "missing natives in $APP_PKG/lib/app: run ./gradlew :desktop:copyDesktopNativesToReleaseDistributable" >&2
    exit 1
fi

OUT="$OUT_DIR/SiliconPlayer-$VERSION-$SHA-x86_64.tar.gz"
tar -czf "$OUT" -C "$(dirname "$APP_PKG")" "$(basename "$APP_PKG")"
echo "wrote $OUT"
