#!/bin/sh
# Builds a single-file .AppImage from the release distributable.
# Usage: ./package-appimage.sh after :desktop:createReleaseDistributable.
set -eu
cd "$(dirname "$0")"

APP_PKG="build/compose/binaries/main-release/app/SiliconPlayer"
OUT_DIR="build/compose/binaries/main-release"
STAGE="build/appimage/AppDir"
TOOL="build/appimage-tool/appimagetool-x86_64.AppImage"
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

rm -rf "$STAGE"
mkdir -p "$STAGE/usr"
cp -a "$APP_PKG/." "$STAGE/usr/"

cp "$APP_PKG/lib/SiliconPlayer.png" "$STAGE/SiliconPlayer.png"

cat > "$STAGE/SiliconPlayer.desktop" <<'EOF'
[Desktop Entry]
Name=SiliconPlayer
Exec=SiliconPlayer
Icon=SiliconPlayer
Type=Application
Categories=AudioVideo;Audio;
Comment=Modern chiptune and module music player
StartupWMClass=SiliconPlayer
EOF

cat > "$STAGE/AppRun" <<'EOF'
#!/bin/sh
exec "$APPDIR/usr/bin/SiliconPlayer" "$@"
EOF
chmod +x "$STAGE/AppRun"

mkdir -p "$STAGE/usr/share/doc/siliconplayer"
about_texts="build/generated/about/main/texts"
if [ ! -d "$about_texts" ] || [ -z "$(ls -A "$about_texts")" ]; then
    echo "error: license texts missing at $about_texts (run :desktop:generateAboutVersions first)" >&2
    exit 1
fi
cp "$about_texts"/* "$STAGE/usr/share/doc/siliconplayer/"

if [ ! -x "$TOOL" ]; then
    mkdir -p "$(dirname "$TOOL")"
    curl -sSL -o "$TOOL" https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage
    chmod +x "$TOOL"
fi

ARCH=x86_64 "$TOOL" "$STAGE" "$OUT_DIR/SiliconPlayer-${VERSION}-${SHA}-x86_64.AppImage"
