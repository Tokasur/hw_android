#!/usr/bin/env bash
# Stages the game data for packaging:
#   share/hedgewars/Data  ->  android/data/src/main/assets/Data
# plus the touch-interface button sprites that only exist in the historical
# Android port, minus build-system files, and writes Data.manifest (one
# relative path per line) so DataInstaller can stream-copy without slow
# AssetManager.list() calls.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$(dirname "$HERE")"
REPO_ROOT="$(dirname "$ANDROID_DIR")"
SRC="$REPO_ROOT/share/hedgewars/Data"
OLD_BUTTONS="$REPO_ROOT/project_files/Android-build/SDL-android-project/assets/Data/Graphics/Buttons"
DST="$ANDROID_DIR/data/src/main/assets"

echo "== Staging Data -> $DST"
rm -rf "$DST/Data" "$DST/Data.manifest"
mkdir -p "$DST"

rsync -a \
    --exclude 'CMakeLists.txt' \
    --exclude '*.cmake' \
    --exclude 'CMakeFiles' \
    "$SRC" "$DST/"

# uTouch.pas widgets need these sprites; they were never merged into share/
echo "== Adding touch button sprites from the historical port"
mkdir -p "$DST/Data/Graphics/Buttons"
cp "$OLD_BUTTONS"/*.png "$DST/Data/Graphics/Buttons/"
rm -f "$DST/Data/Graphics/Buttons/forwardjump_old.png"

# prune empty directories (AssetManager silently drops them anyway)
find "$DST/Data" -type d -empty -delete

echo "== Writing manifest"
( cd "$DST" && find Data -type f | LC_ALL=C sort > Data.manifest )
echo "== $(wc -l < "$DST/Data.manifest") files, $(du -sh "$DST/Data" | cut -f1)"
