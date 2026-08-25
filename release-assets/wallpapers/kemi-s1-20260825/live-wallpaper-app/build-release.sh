#!/usr/bin/env bash
set -euo pipefail

: "${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
: "${KEMI_WALLPAPER_KEYSTORE:?Set KEMI_WALLPAPER_KEYSTORE}"
: "${KEMI_WALLPAPER_STORE_PASSWORD:?Set KEMI_WALLPAPER_STORE_PASSWORD}"
: "${KEMI_WALLPAPER_KEY_ALIAS:?Set KEMI_WALLPAPER_KEY_ALIAS}"
: "${KEMI_WALLPAPER_KEY_PASSWORD:?Set KEMI_WALLPAPER_KEY_PASSWORD}"

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_TOOLS_VERSION="${BUILD_TOOLS_VERSION:-35.0.0}"
PLATFORM_VERSION="${PLATFORM_VERSION:-31}"
BUILD_TOOLS="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION"
ANDROID_JAR="$ANDROID_SDK_ROOT/platforms/android-$PLATFORM_VERSION/android.jar"
BUILD_DIR="$APP_DIR/build/release"
OUTPUT_DIR="$APP_DIR/release"
OUTPUT_APK="$OUTPUT_DIR/双屏壁纸-v1.1.1-release.apk"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$OUTPUT_DIR"

javac -source 8 -target 8 \
  -classpath "$ANDROID_JAR" \
  -d "$BUILD_DIR/classes" \
  $(find "$APP_DIR/src" -name '*.java' -print)

"$BUILD_TOOLS/d8" \
  --min-api 26 \
  --output "$BUILD_DIR/dex" \
  $(find "$BUILD_DIR/classes" -name '*.class' -print)

"$BUILD_TOOLS/aapt2" compile \
  --dir "$APP_DIR/res" \
  -o "$BUILD_DIR/resources.zip"

"$BUILD_TOOLS/aapt2" link \
  -o "$BUILD_DIR/unsigned.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP_DIR/AndroidManifest.xml" \
  --min-sdk-version 26 \
  --target-sdk-version 31 \
  --version-code 111 \
  --version-name 1.1.1 \
  -A "$APP_DIR/assets" \
  "$BUILD_DIR/resources.zip"

(cd "$BUILD_DIR/dex" && zip -q -j "$BUILD_DIR/unsigned.apk" classes.dex)
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
"$BUILD_TOOLS/apksigner" sign \
  --ks "$KEMI_WALLPAPER_KEYSTORE" \
  --ks-key-alias "$KEMI_WALLPAPER_KEY_ALIAS" \
  --ks-pass "pass:$KEMI_WALLPAPER_STORE_PASSWORD" \
  --key-pass "pass:$KEMI_WALLPAPER_KEY_PASSWORD" \
  --out "$OUTPUT_APK" \
  "$BUILD_DIR/aligned.apk"

"$BUILD_TOOLS/apksigner" verify --verbose --print-certs "$OUTPUT_APK"
echo "Built: $OUTPUT_APK"
