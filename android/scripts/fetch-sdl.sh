#!/usr/bin/env bash
# Downloads and unpacks the pinned SDL2 family source releases (from libsdl.org)
# into android/third_party/ (gitignored). Also refreshes the committed copy of
# SDL's Java glue (org/libsdl/app) in the app module.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$(dirname "$HERE")"
THIRD_PARTY="$ANDROID_DIR/third_party"

SDL2_VER=2.30.11
SDL2_IMAGE_VER=2.8.8
SDL2_MIXER_VER=2.8.1
SDL2_TTF_VER=2.24.0
SDL2_NET_VER=2.2.0

# Plain tarballs (no vendored deps needed)
declare -A URLS=(
    ["SDL2-$SDL2_VER"]="https://www.libsdl.org/release/SDL2-$SDL2_VER.tar.gz"
    ["SDL2_image-$SDL2_IMAGE_VER"]="https://www.libsdl.org/projects/SDL_image/release/SDL2_image-$SDL2_IMAGE_VER.tar.gz"
    ["SDL2_net-$SDL2_NET_VER"]="https://www.libsdl.org/projects/SDL_net/release/SDL2_net-$SDL2_NET_VER.tar.gz"
)
# Git checkouts with submodules (mixer needs external/ogg+opus+opusfile for
# OPUS, ttf needs external/freetype; libsdl.org tarballs don't include them)
declare -A GITS=(
    ["SDL2_mixer-$SDL2_MIXER_VER"]="https://github.com/libsdl-org/SDL_mixer.git release-$SDL2_MIXER_VER"
    ["SDL2_ttf-$SDL2_TTF_VER"]="https://github.com/libsdl-org/SDL_ttf.git release-$SDL2_TTF_VER"
)

mkdir -p "$THIRD_PARTY"
for name in "${!URLS[@]}"; do
    if [ -d "$THIRD_PARTY/$name" ]; then
        echo "== $name already present"
        continue
    fi
    echo "== Fetching $name"
    curl -fL --retry 4 --retry-delay 3 -o "$THIRD_PARTY/$name.tar.gz" "${URLS[$name]}"
    tar -xzf "$THIRD_PARTY/$name.tar.gz" -C "$THIRD_PARTY"
    rm "$THIRD_PARTY/$name.tar.gz"
done
for name in "${!GITS[@]}"; do
    if [ -d "$THIRD_PARTY/$name" ]; then
        echo "== $name already present"
        continue
    fi
    read -r url tag <<< "${GITS[$name]}"
    echo "== Cloning $name ($tag)"
    git clone --depth 1 --branch "$tag" --recurse-submodules --shallow-submodules \
        "$url" "$THIRD_PARTY/$name"
done

# Unversioned symlinks so build scripts don't need the version numbers
ln -sfn "SDL2-$SDL2_VER"             "$THIRD_PARTY/SDL2"
ln -sfn "SDL2_image-$SDL2_IMAGE_VER" "$THIRD_PARTY/SDL2_image"
ln -sfn "SDL2_mixer-$SDL2_MIXER_VER" "$THIRD_PARTY/SDL2_mixer"
ln -sfn "SDL2_ttf-$SDL2_TTF_VER"     "$THIRD_PARTY/SDL2_ttf"
ln -sfn "SDL2_net-$SDL2_NET_VER"     "$THIRD_PARTY/SDL2_net"

# Refresh the committed SDL Java glue (zlib license, see Licenses screen)
GLUE_SRC="$THIRD_PARTY/SDL2/android-project/app/src/main/java/org/libsdl/app"
GLUE_DST="$ANDROID_DIR/app/src/main/java/org/libsdl/app"
mkdir -p "$GLUE_DST"
cp "$GLUE_SRC"/*.java "$GLUE_DST/"
echo "== SDL Java glue copied to $GLUE_DST"
# The repo carries deliberate patches on top of upstream glue (e.g. SDLActivity.
# sendCommand made protected for GameActivity). Refreshing is legitimate on an
# SDL version bump, but never let it silently clobber those patches.
if ! git -C "$ANDROID_DIR" diff --quiet -- "$GLUE_DST" 2>/dev/null; then
    echo "== WARNING: copied glue differs from the committed version (local patches overwritten)."
    echo "==          Review:  git diff -- $GLUE_DST"
    echo "==          Restore: git checkout -- $GLUE_DST"
fi
echo "== Done"
