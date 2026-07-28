#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
KOFFICE_DIR=$(dirname "$SCRIPT_DIR")
UPSTREAM_DIR="$KOFFICE_DIR/upstream"
SDK_DIR=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
NDK_VERSION=${KOFFICE_NDK_VERSION:-28.2.13676358}
JOBS=${KOFFICE_JOBS:-$(getconf _NPROCESSORS_ONLN)}

if [ "$(uname -s)" != "Linux" ]; then
    echo "The Collabora native Android engine must be built on Linux." >&2
    exit 1
fi

if [ "$(uname -m)" != "x86_64" ]; then
    echo "This reproducible build script currently supports an x86_64 Linux host." >&2
    exit 1
fi

if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR" ]; then
    echo "Set ANDROID_SDK_ROOT to an installed Android SDK." >&2
    exit 1
fi

NDK_DIR=${ANDROID_NDK_HOME:-$SDK_DIR/ndk/$NDK_VERSION}
if [ ! -d "$NDK_DIR" ]; then
    echo "Android NDK not found: $NDK_DIR" >&2
    echo "Install NDK $NDK_VERSION or set ANDROID_NDK_HOME." >&2
    exit 1
fi

"$SCRIPT_DIR/prepare-upstream.sh" --full

cd "$UPSTREAM_DIR/engine"
printf '%s\n' \
    '--build=x86_64-unknown-linux-gnu' \
    "--with-android-ndk=$NDK_DIR" \
    "--with-android-sdk=$SDK_DIR" \
    '--enable-sal-log' \
    '--with-distro=CPAndroidAarch64' \
    '--enable-dbgutil' > autogen.input
./autogen.sh
make -j"$JOBS"

cd "$UPSTREAM_DIR"
./autogen.sh
./configure \
    --enable-androidapp \
    --with-lo-builddir="$UPSTREAM_DIR/engine" \
    --enable-debug \
    --with-android-abi=arm64-v8a \
    --with-app-name=KOffice \
    --with-app-package-name=org.kemi.koffice \
    --with-vendor=KEMI
make -j"$JOBS"

cd "$UPSTREAM_DIR/android"
./gradlew assembleDebug

echo "KOffice Android build completed."