#!/usr/bin/env bash
# Cross-compiles the Hedgewars Free Pascal engine to libhwengine.so for
# Android and stages it into android/app/src/main/jniLibs/<abi>/.
#
# Pipeline (proven by bootstrap-toolchain.sh's smoke stage):
#   1. config.inc is generated from hedgewars/config.inc.in
#   2. ppcross<cpu> -Tandroid -Cn  compiles all units to objects and writes
#      ppas.sh + link<pid>.res, without linking
#   3. ppas.sh runs ${BINUTILSPREFIX}ld — our ld.lld wrapper, which adds the
#      NDK sysroot, patches FPC's linker script for lld, and forces
#      -z max-page-size=16384 (Play Store 16 KB requirement)
#
# Prereqs: bootstrap-toolchain.sh all  &&  build-native-deps.sh (SDL2 .so
# import libraries must exist under android/native/<abi>/lib).
#
# Usage: build-engine.sh [abi ...]      (default: arm64-v8a armeabi-v7a x86_64)
# Env:   TOOLCHAIN_DIR (default: $HOME/hedgewars-android-toolchain)

set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ANDROID_DIR="$(dirname "$HERE")"
REPO_ROOT="$(dirname "$ANDROID_DIR")"
TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-$HOME/hedgewars-android-toolchain}"
FPC_VER=3.2.2
FPC_PREFIX="$TOOLCHAIN_DIR/fpc-android/lib/fpc/$FPC_VER"
WRAPPERS="$TOOLCHAIN_DIR/binutils-wrappers"
NDK=$(ls -d "$TOOLCHAIN_DIR"/sdk/ndk/* | sort -V | tail -1)
LLVM="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

ABIS=("${@:-arm64-v8a}")
[ $# -eq 0 ] && ABIS=(arm64-v8a armeabi-v7a x86_64)

log() { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# --- 1. config.inc ----------------------------------------------------------
gen_config_inc() {
    local out="$1"
    local proto version revision hash
    proto=$(sed -n 's/^set(HEDGEWARS_PROTO_VER \([0-9]*\))$/\1/p' "$REPO_ROOT/CMakeLists.txt")
    version=$(sed -n 's/^project(hedgewars VERSION \([0-9.]*\))$/\1/p' "$REPO_ROOT/CMakeLists.txt")
    revision=$(git -C "$REPO_ROOT" rev-list --count HEAD 2>/dev/null || echo 0)
    hash=$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)
    [ -n "$proto" ] || die "could not extract HEDGEWARS_PROTO_VER"
    sed -e "s/\${HEDGEWARS_PROTO_VER}/$proto/" \
        -e "s/\${HEDGEWARS_VERSION}/$version-android/" \
        -e "s/\${HEDGEWARS_REVISION}/r$revision/" \
        -e "s/\${HEDGEWARS_HASH}/$hash/" \
        -e "s|\${HEDGEWARS_FULL_DATADIR}|.|" \
        -e "s/\${FONTS_DIRS_ARRAY}/array [0..1] of PChar = (nil, nil);/" \
        "$REPO_ROOT/hedgewars/config.inc.in" > "$out/config.inc"
}

# --- 2+3. compile + link per ABI -------------------------------------------
for abi in "${ABIS[@]}"; do
    case "$abi" in
        arm64-v8a)   cpu=aarch64; ppc=ppcrossa64; cpuopt="" ;;
        # VFPV2 (not V3): FPC 3.2.2 emits unencodable vmov.f64 immediates
        # with -CfVFPV3 (e.g. 2^-11 in uAIAmmoTests); VFPv2 loads such
        # constants from the literal pool instead and is a strict subset of
        # the armeabi-v7a baseline FPU (VFPv3-D16).
        armeabi-v7a) cpu=arm;     ppc=ppcrossarm; cpuopt="-CpARMV7A -CfVFPV2" ;;
        x86_64)      cpu=x86_64;  ppc=ppcrossx64; cpuopt="" ;;
        *) die "unknown abi: $abi" ;;
    esac
    units="$FPC_PREFIX/units/$cpu-android"
    deps="$ANDROID_DIR/native/$abi/lib"
    [ -d "$deps" ] || die "native deps missing for $abi — run build-native-deps.sh first"
    out="$ANDROID_DIR/engine/build/$abi"
    rm -rf "$out"; mkdir -p "$out"
    gen_config_inc "$out"

    log "[$abi] FPC compile (hwLibrary.pas)"
    ( cd "$out" && \
      "$FPC_PREFIX/$ppc" -Tandroid $cpuopt -Cn -O2 -Xs \
        -FD"$WRAPPERS" \
        -Fu"$units/"'*' \
        -Fu"$REPO_ROOT/hedgewars" \
        -Fu"$REPO_ROOT/project_files/Android-build" \
        -Fi"$REPO_ROOT/hedgewars" -Fi"$out" \
        -Fl"$deps" \
        -FE"$out" -FU"$out" \
        -o"$out/libhwengine.so" \
        "$REPO_ROOT/hedgewars/hwLibrary.pas" ) \
        > "$out/fpc.log" 2>&1 \
        || { tail -30 "$out/fpc.log"; die "FPC compile failed for $abi"; }

    log "[$abi] link (ppas.sh via ld.lld wrapper)"
    ( cd "$out" && sh ./ppas.sh ) > "$out/link.log" 2>&1 \
        || { tail -30 "$out/link.log"; die "link failed for $abi"; }

    log "[$abi] verify exports + 16 KB alignment"
    "$LLVM/llvm-nm" -D "$out/libhwengine.so" | grep -q " T RunEngine" \
        || die "RunEngine not exported ($abi)"
    "$LLVM/llvm-nm" -D "$out/libhwengine.so" \
        | grep -q "Java_org_hedgewars_hedgeroid_EngineProtocol_PascalExports_HWGenLandPreview" \
        || die "JNI land preview export missing ($abi)"
    "$LLVM/llvm-readelf" -l "$out/libhwengine.so" | grep -q "0x4000$" \
        || die "16 KB page alignment missing ($abi)"

    jni="$ANDROID_DIR/app/src/main/jniLibs/$abi"
    mkdir -p "$jni"
    cp "$out/libhwengine.so" "$jni/"
    log "[$abi] staged libhwengine.so ($(du -h "$out/libhwengine.so" | cut -f1))"
done

log "Done."
