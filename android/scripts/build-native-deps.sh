#!/usr/bin/env bash
# Builds all native C dependencies for every ABI and stages the resulting
# .so files into android/app/src/main/jniLibs/<abi>/ for Gradle to package.
#
#   SDL2, SDL2_image, SDL2_mixer (OGG+OPUS), SDL2_ttf, SDL2_net   (third_party/)
#   lua5.1, physfs, physlayer, main                               (repo sources)
#
# Per-ABI install prefixes end up in android/native/<abi> — the engine build
# (android/engine/build-engine.sh) links against them.
#
# Usage: build-native-deps.sh [abi ...]     (default: arm64-v8a armeabi-v7a x86_64)
# Env:   TOOLCHAIN_DIR (default: $HOME/hedgewars-android-toolchain)

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$(dirname "$HERE")"
REPO_ROOT="$(dirname "$ANDROID_DIR")"
THIRD_PARTY="$ANDROID_DIR/third_party"
TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-$HOME/hedgewars-android-toolchain}"

ANDROID_API=21
ABIS=("${@:-arm64-v8a}")
[ $# -eq 0 ] && ABIS=(arm64-v8a armeabi-v7a x86_64)

NDK=$(ls -d "$TOOLCHAIN_DIR"/sdk/ndk/* | sort -V | tail -1)
TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake"
[ -f "$TOOLCHAIN_FILE" ] || { echo "NDK toolchain file not found"; exit 1; }
CMAKE_BIN=$(command -v cmake)
NPROC=$(nproc)

log() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }

build_one() { # build_one <abi> <src-dir> <name> <extra cmake args...>
    local abi="$1" src="$2" name="$3"; shift 3
    local bdir="$ANDROID_DIR/native/build/$abi/$name"
    local prefix="$ANDROID_DIR/native/$abi"
    log "[$abi] $name"
    mkdir -p "$bdir"
    "$CMAKE_BIN" -S "$src" -B "$bdir" -G Ninja \
        -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN_FILE" \
        -DANDROID_ABI="$abi" \
        -DANDROID_PLATFORM="android-$ANDROID_API" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$prefix" \
        -DCMAKE_PREFIX_PATH="$prefix" \
        -DCMAKE_FIND_ROOT_PATH="$prefix" \
        -DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
        -DBUILD_SHARED_LIBS=ON \
        "$@" > "$bdir.configure.log" 2>&1 \
        || { tail -30 "$bdir.configure.log"; exit 1; }
    "$CMAKE_BIN" --build "$bdir" -j"$NPROC" > "$bdir.build.log" 2>&1 \
        || { tail -30 "$bdir.build.log"; exit 1; }
    "$CMAKE_BIN" --install "$bdir" > /dev/null
}

# --- Rust: lib-hwengine-future (land/collision helpers used by the engine) --
rust_target_for_abi() {
    case "$1" in
        arm64-v8a)   echo aarch64-linux-android ;;
        armeabi-v7a) echo armv7-linux-androideabi ;;
        x86_64)      echo x86_64-linux-android ;;
    esac
}
clang_for_abi() {
    local llvm="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
    case "$1" in
        arm64-v8a)   echo "$llvm/aarch64-linux-android$ANDROID_API-clang" ;;
        armeabi-v7a) echo "$llvm/armv7a-linux-androideabi$ANDROID_API-clang" ;;
        x86_64)      echo "$llvm/x86_64-linux-android$ANDROID_API-clang" ;;
    esac
}

build_rust_future() { # build_rust_future <abi>
    local abi="$1"
    local target; target=$(rust_target_for_abi "$abi")
    local linker; linker=$(clang_for_abi "$abi")
    log "[$abi] lib-hwengine-future (Rust, $target)"
    rustup target add "$target" >/dev/null 2>&1 || rustup target add "$target"
    local envname; envname=$(echo "$target" | tr 'a-z-' 'A-Z_')
    env "CARGO_TARGET_${envname}_LINKER=$linker" \
        RUSTFLAGS="-C link-arg=-Wl,-z,max-page-size=16384" \
        cargo build --release --quiet \
        --manifest-path "$REPO_ROOT/rust/lib-hwengine-future/Cargo.toml" \
        --target "$target" \
        --target-dir "$ANDROID_DIR/native/rust" \
        || exit 1
    cp "$ANDROID_DIR/native/rust/$target/release/libhwengine_future.so" \
       "$ANDROID_DIR/native/$abi/lib/"
}

for abi in "${ABIS[@]}"; do
    prefix="$ANDROID_DIR/native/$abi"
    mkdir -p "$prefix"

    build_one "$abi" "$THIRD_PARTY/SDL2" SDL2 \
        -DSDL_SHARED=ON -DSDL_STATIC=OFF -DSDL_TEST=OFF

    build_one "$abi" "$THIRD_PARTY/SDL2_image" SDL2_image \
        -DSDL2IMAGE_SAMPLES=OFF -DSDL2IMAGE_VENDORED=OFF \
        -DSDL2IMAGE_BACKEND_STB=ON \
        -DSDL2IMAGE_AVIF=OFF -DSDL2IMAGE_JXL=OFF -DSDL2IMAGE_TIF=OFF \
        -DSDL2IMAGE_WEBP=OFF

    build_one "$abi" "$THIRD_PARTY/SDL2_mixer" SDL2_mixer \
        -DSDL2MIXER_SAMPLES=OFF -DSDL2MIXER_VENDORED=ON \
        -DSDL2MIXER_DEPS_SHARED=OFF \
        -DSDL2MIXER_VORBIS=STB -DSDL2MIXER_OPUS=ON \
        -DSDL2MIXER_FLAC=OFF -DSDL2MIXER_MOD=OFF -DSDL2MIXER_MIDI=OFF \
        -DSDL2MIXER_WAVPACK=OFF -DSDL2MIXER_MP3=OFF

    build_one "$abi" "$THIRD_PARTY/SDL2_ttf" SDL2_ttf \
        -DSDL2TTF_SAMPLES=OFF -DSDL2TTF_VENDORED=ON -DSDL2TTF_HARFBUZZ=OFF

    build_one "$abi" "$THIRD_PARTY/SDL2_net" SDL2_net

    build_one "$abi" "$ANDROID_DIR/app/src/main/cpp" hw_support \
        -DHW_REPO_ROOT="$REPO_ROOT" -DHW_DEPS_PREFIX="$prefix"

    build_rust_future "$abi"

    # Stage every runtime .so for Gradle packaging
    jni="$ANDROID_DIR/app/src/main/jniLibs/$abi"
    mkdir -p "$jni"
    for so in SDL2 SDL2_image SDL2_mixer SDL2_ttf SDL2_net \
              lua physfs physlayer main hwengine_future; do
        cp "$prefix/lib/lib${so}.so" "$jni/"
    done
    log "[$abi] staged $(ls "$jni" | wc -l) libraries into jniLibs"
done

log "Done."
