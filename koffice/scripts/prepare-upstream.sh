#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
KOFFICE_DIR=$(dirname "$SCRIPT_DIR")
REPO_ROOT=$(dirname "$KOFFICE_DIR")
UPSTREAM_DIR="$KOFFICE_DIR/upstream"
PATCH_DIR="$KOFFICE_DIR/patches"
EXPECTED_COMMIT=b91cb7428e620f5c34c2ff94d7f8ce2a7d494c62

case "${1:-}" in
    "") ;;
    --full) ;;
    *)
        echo "Usage: $0 [--full]" >&2
        exit 2
        ;;
esac

git -C "$REPO_ROOT" submodule sync -- koffice/upstream
git -C "$REPO_ROOT" submodule update --init --depth 1 -- koffice/upstream

actual_commit=$(git -C "$UPSTREAM_DIR" rev-parse HEAD)
if [ "$actual_commit" != "$EXPECTED_COMMIT" ]; then
    echo "Unexpected upstream commit: $actual_commit" >&2
    echo "Expected: $EXPECTED_COMMIT" >&2
    exit 1
fi

if [ "${1:-}" = "--full" ] && git -C "$UPSTREAM_DIR" sparse-checkout list >/dev/null 2>&1; then
    git -C "$UPSTREAM_DIR" sparse-checkout disable
fi

for patch_file in "$PATCH_DIR"/*.patch; do
    if git -C "$UPSTREAM_DIR" apply --reverse --check "$patch_file" >/dev/null 2>&1; then
        echo "Already applied: $(basename "$patch_file")"
    elif git -C "$UPSTREAM_DIR" apply --check "$patch_file"; then
        git -C "$UPSTREAM_DIR" apply "$patch_file"
        echo "Applied: $(basename "$patch_file")"
    else
        echo "Patch does not apply cleanly: $patch_file" >&2
        exit 1
    fi
done

echo "KOffice upstream ready at $actual_commit"