#!/usr/bin/env bash
# Record a match and replay it, against a desktop build of the engine.
#
# Same host build recipe as host-e2e-test.sh, plus -dDEBUGFILE so the engine
# writes Logs/game0.log — that log is the whole point: "Desync detected",
# [Cmd] lines and the 'N' checksum frames are what the diff reads.
#
#   host-replay-loop.sh record <out.hwd>   play a bot-vs-bot match, record it
#   host-replay-loop.sh replay <in.hwd>    replay it through the IPC socket
#   host-replay-loop.sh file   <in.hwd>    replay it via the engine's own file
#                                          loader (no frontend at all)
#   host-replay-loop.sh cycle              record then replay, then diff logs
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$(dirname "$HERE")"
REPO_ROOT="$(dirname "$ANDROID_DIR")"
BUILD="$ANDROID_DIR/native/host-engine-sync"
WORK="${HW_REPLAY_WORK:-$BUILD/work}"

mkdir -p "$BUILD" "$WORK"

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
    -e "s/\${HEDGEWARS_VERSION}/$version-host-sync/" \
    -e "s/\${HEDGEWARS_REVISION}/r0/" \
    -e "s/\${HEDGEWARS_HASH}/sync/" \
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
ln -sfn "$(ldconfig -p | awk '/liblua5\.1\.so/{print $NF; exit}')" "$BUILD/liblua.so"

# --- host engine build -------------------------------------------------------
# Rebuilt whenever an engine source is newer than the binary: this script is
# the inner loop of a debugging session, a stale binary would waste a cycle.
newest_src=$(find "$REPO_ROOT/hedgewars" -name '*.pas' -newer "$BUILD/hwengine" -print -quit 2>/dev/null || true)
if [ ! -x "$BUILD/hwengine" ] || [ -n "$newest_src" ]; then
    echo "== Building desktop engine (host FPC, DEBUGFILE${HW_SYNCDEBUG:+ + SYNCDEBUG})"
    ( cd "$BUILD" && \
      fpc -O2 -B -dDEBUGFILE ${HW_SYNCDEBUG:+-dSYNCDEBUG -gl} \
        -Fu"$REPO_ROOT/hedgewars" \
        -Fi"$REPO_ROOT/hedgewars" -Fi"$BUILD" \
        -Fl"$BUILD" \
        -o"$BUILD/hwengine" \
        "$REPO_ROOT/hedgewars/hwengine.pas" ) > "$BUILD/fpc.log" 2>&1 \
        || { tail -30 "$BUILD/fpc.log"; exit 1; }
fi

export LD_LIBRARY_PATH="$BUILD"
exec python3 "$HERE/host-replay-harness.py" \
    --engine "$BUILD/hwengine" \
    --data "$REPO_ROOT/share/hedgewars/Data" \
    --work "$WORK" \
    "$@"
