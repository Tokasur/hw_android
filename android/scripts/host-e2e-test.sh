#!/usr/bin/env bash
# End-to-end IPC protocol validation against a real engine, no display needed.
#
# Builds the desktop engine (same sources, host FPC + system SDL2), then runs
# it in --landpreview mode against a Python harness that speaks the exact
# same protocol as the Android frontend (PreviewConnection.kt): it must
# deliver a 256x128 1-bit preview + hedgehog-limit byte.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$(dirname "$HERE")"
REPO_ROOT="$(dirname "$ANDROID_DIR")"
BUILD="$ANDROID_DIR/native/host-engine"

mkdir -p "$BUILD"

# --- host build of lib-hwengine-future (Rust) --------------------------------
if [ ! -f "$BUILD/libhwengine_future.so" ]; then
    echo "== Building lib-hwengine-future (host)"
    cargo build --release --quiet \
        --manifest-path "$REPO_ROOT/rust/lib-hwengine-future/Cargo.toml" \
        --target-dir "$ANDROID_DIR/native/rust-host"
    cp "$ANDROID_DIR/native/rust-host/release/libhwengine_future.so" "$BUILD/"
fi

# --- config.inc --------------------------------------------------------------
proto=$(sed -n 's/^set(HEDGEWARS_PROTO_VER \([0-9]*\))$/\1/p' "$REPO_ROOT/CMakeLists.txt")
version=$(sed -n 's/^project(hedgewars VERSION \([0-9.]*\))$/\1/p' "$REPO_ROOT/CMakeLists.txt")
sed -e "s/\${HEDGEWARS_PROTO_VER}/$proto/" \
    -e "s/\${HEDGEWARS_VERSION}/$version-host-test/" \
    -e "s/\${HEDGEWARS_REVISION}/r0/" \
    -e "s/\${HEDGEWARS_HASH}/test/" \
    -e "s|\${HEDGEWARS_FULL_DATADIR}|$REPO_ROOT/share/hedgewars|" \
    -e "s/\${FONTS_DIRS_ARRAY}/array [0..1] of PChar = (nil, nil);/" \
    "$REPO_ROOT/hedgewars/config.inc.in" > "$BUILD/config.inc"

# --- host physlayer + lua alias ----------------------------------------------
if [ ! -f "$BUILD/libphyslayer.so" ]; then
    echo "== Building physlayer (host)"
    cc -shared -fPIC -O2 -o "$BUILD/libphyslayer.so" \
        -I"$REPO_ROOT/misc/libphyslayer" \
        -I/usr/include/SDL2 -I/usr/include/lua5.1 \
        "$REPO_ROOT/misc/libphyslayer/physfscompat.c" \
        "$REPO_ROOT/misc/libphyslayer/physfsrwops.c" \
        "$REPO_ROOT/misc/libphyslayer/physfslualoader.c" \
        "$REPO_ROOT/misc/libphyslayer/hwpacksmounter.c" \
        -lSDL2 -llua5.1 -lphysfs
fi
# FPC links -llua; Ubuntu ships liblua5.1.so
ln -sfn "$(ldconfig -p | awk '/liblua5\.1\.so/{print $NF; exit}')" "$BUILD/liblua.so"

# --- host engine build (executable, not library) -----------------------------
if [ ! -x "$BUILD/hwengine" ]; then
    echo "== Building desktop engine (host FPC)"
    ( cd "$BUILD" && \
      fpc -O2 -B \
        -Fu"$REPO_ROOT/hedgewars" \
        -Fi"$REPO_ROOT/hedgewars" -Fi"$BUILD" \
        -Fl"$BUILD" \
        -o"$BUILD/hwengine" \
        "$REPO_ROOT/hedgewars/hwengine.pas" ) > "$BUILD/fpc.log" 2>&1 \
        || { tail -25 "$BUILD/fpc.log"; exit 1; }
fi

# --- run the protocol harness -------------------------------------------------
echo "== Running land-preview E2E"
LD_LIBRARY_PATH="$BUILD" python3 "$HERE/host-e2e-harness.py" \
    "$BUILD/hwengine" "$REPO_ROOT/share/hedgewars/Data"
