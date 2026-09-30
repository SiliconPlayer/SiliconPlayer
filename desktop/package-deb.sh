#!/bin/sh
# Wraps the release distributable (runtime + natives) into a .deb installer.
# Usage: ./package-deb.sh after :desktop:createReleaseDistributable.
# Needs jpackage with dpkg-deb and fakeroot (both present on Ubuntu runners).
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
for tool in jpackage dpkg-deb fakeroot; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "missing $tool: install it first" >&2
        exit 1
    fi
done

rm -f "$OUT_DIR"/siliconplayer_*.deb
jpackage --type deb \
    --dest "$OUT_DIR" \
    --name SiliconPlayer \
    --app-version "$VERSION" \
    --icon packaging/SiliconPlayer.png \
    --linux-shortcut \
    --app-image "$APP_PKG"
DEB="$(ls "$OUT_DIR"/siliconplayer_*.deb | head -1)"
OUT="$OUT_DIR/SiliconPlayer-$VERSION-$SHA-x86_64.deb"
mv "$DEB" "$OUT"
echo "wrote $OUT"
