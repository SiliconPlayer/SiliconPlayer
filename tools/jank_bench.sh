#!/bin/bash
# Builds the benchmark target + test APK, installs both, pushes audio
# fixtures, then runs the UiJankBenchmark suite (or a single test).
# Usage: tools/jank_bench.sh [TestMethod]  (e.g. tools/jank_bench.sh scrollFileBrowser)
set -euo pipefail
cd "$(dirname "$0")/.."
METHOD="${1:-}"
FIXTURES="${JANK_FIXTURES:-/home/flopster101/Music/SyncedMusic}"

./gradlew :app:assembleOptimizedDebug :baselineprofile:assembleAndroidTest

TARGET_APK=$(ls app/build/outputs/apk/optimizedDebug/*x86_64*.apk 2>/dev/null | head -n 1)
if [ -z "$TARGET_APK" ]; then
    TARGET_APK=$(ls app/build/outputs/apk/optimizedDebug/*.apk | head -n 1)
fi
TEST_APK=$(find baselineprofile/build/outputs/apk/androidTest -name "*.apk" | head -n 1)
echo "Installing $TARGET_APK and $TEST_APK"
adb install -r "$TARGET_APK" > /dev/null
adb install -r "$TEST_APK" > /dev/null

echo "Pushing fixtures from $FIXTURES"
adb shell mkdir -p /sdcard/Music/jank
for f in "$FIXTURES"/*.mp3 "$FIXTURES"/*.flac; do
    [ -e "$f" ] || continue
    adb push "$f" /sdcard/Music/jank/ > /dev/null
done
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/jank > /dev/null || true

ARGS=""
if [ -n "$METHOD" ]; then
    ARGS="-e class com.flopster101.siliconplayer.baselineprofile.UiJankBenchmark#$METHOD"
fi
# shellcheck disable=SC2086
adb shell am instrument -w -r $ARGS \
    com.flopster101.siliconplayer.baselineprofile.test/androidx.test.runner.AndroidJUnitRunner
