#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
KOFFICE_DIR=$(dirname "$SCRIPT_DIR")
REPO_ROOT=$(dirname "$KOFFICE_DIR")
UPSTREAM_DIR="$KOFFICE_DIR/upstream"
PATCH_DIR="$KOFFICE_DIR/patches"
EXPECTED_COMMIT=b91cb7428e620f5c34c2ff94d7f8ce2a7d494c62

test "$(git -C "$UPSTREAM_DIR" rev-parse HEAD)" = "$EXPECTED_COMMIT"
test "$(git -C "$REPO_ROOT" ls-files --stage koffice/upstream | awk '{print $1}')" = 160000

git -C "$UPSTREAM_DIR" diff --check
for patch_file in "$PATCH_DIR"/*.patch; do
    git -C "$UPSTREAM_DIR" apply --reverse --check "$patch_file"
done

grep -q 'if (isPdfDocument())' "$UPSTREAM_DIR/android/lib/src/main/java/org/libreoffice/androidlib/LOActivity.java"
grep -q 'permission=readonly' "$UPSTREAM_DIR/android/lib/src/main/java/org/libreoffice/androidlib/LOActivity.java"
grep -q 'app.isPdfDocument = function' "$UPSTREAM_DIR/browser/src/docstatefunctions.js"
grep -q '!app.isPdfDocument()' "$UPSTREAM_DIR/browser/src/canvas/sections/CommentSection.ts"

if [ -f "$UPSTREAM_DIR/android/variables.gradle" ] && [ -f "$UPSTREAM_DIR/android/app/appSettings.gradle" ]; then
    "$UPSTREAM_DIR/android/gradlew" -p "$UPSTREAM_DIR/android" :lib:compileDebugJavaWithJavac
else
    echo "Gradle compile skipped: run the Linux engine/configure build first."
fi

echo "KOffice source verification passed."