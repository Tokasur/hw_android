#!/usr/bin/env bash
# Hedgewars Android port — toolchain bootstrap.
#
# Installs everything needed to build the Android app and cross-compile the
# Free Pascal engine, into a self-contained toolchain directory:
#
#   * Android SDK command-line tools, platform 36, build-tools, NDK r28
#   * Free Pascal 3.2.2 host compiler (apt, with SourceForge fallback)
#   * Free Pascal 3.2.2 sources + cross-compilers for
#     aarch64-android, arm-android (ARMv7-A VFPv3) and x86_64-android
#   * clang-based binutils wrappers so FPC's external assembler/linker
#     calls are served by the NDK's LLVM toolchain (modern NDKs ship no
#     GNU binutils)
#
# Usage:  bootstrap-toolchain.sh [sdk|fpc-host|fpc-cross|smoke|all]
# Env:    TOOLCHAIN_DIR (default: $HOME/hedgewars-android-toolchain)
#
# The script is idempotent: completed stages are stamped and skipped.

set -euo pipefail

TOOLCHAIN_DIR="${TOOLCHAIN_DIR:-$HOME/hedgewars-android-toolchain}"
STAMPS="$TOOLCHAIN_DIR/.stamps"
DOWNLOADS="$TOOLCHAIN_DIR/downloads"

# ---- pinned versions -------------------------------------------------------
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP"
ANDROID_PLATFORM="platforms;android-36"
ANDROID_BUILD_TOOLS="build-tools;36.0.0"
ANDROID_NDK_PKG="ndk;28.2.13676358"          # NDK r28c (16 KB page alignment by default)
ANDROID_CMAKE_PKG="cmake;3.22.1"

FPC_VER="3.2.2"
FPC_SRC_URL="https://gitlab.com/freepascal.org/fpc/source/-/archive/release_3_2_2/source-release_3_2_2.tar.gz"
FPC_BIN_URL="https://sourceforge.net/projects/freepascal/files/Linux/3.2.2/fpc-3.2.2.x86_64-linux.tar/download"

ANDROID_API=21   # minSdkVersion — keep in sync with android/app/build.gradle.kts

log()  { printf '\n\033[1;34m== %s\033[0m\n' "$*"; }
die()  { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }
stamp_ok()  { [ -f "$STAMPS/$1" ]; }
stamp_set() { mkdir -p "$STAMPS"; touch "$STAMPS/$1"; }

fetch() { # fetch <url> <dest>
    local url="$1" dest="$2"
    [ -f "$dest" ] && return 0
    mkdir -p "$(dirname "$dest")"
    log "Downloading $(basename "$dest")"
    curl -fL --retry 4 --retry-delay 3 -o "$dest.part" "$url"
    mv "$dest.part" "$dest"
}

# ---------------------------------------------------------------------------
stage_sdk() {
    stamp_ok sdk && { log "SDK stage already done"; return 0; }
    log "Installing Android SDK + NDK into $TOOLCHAIN_DIR/sdk"
    local sdk="$TOOLCHAIN_DIR/sdk"
    mkdir -p "$sdk/cmdline-tools"
    fetch "$CMDLINE_TOOLS_URL" "$DOWNLOADS/$CMDLINE_TOOLS_ZIP"
    if [ ! -d "$sdk/cmdline-tools/latest" ]; then
        unzip -q "$DOWNLOADS/$CMDLINE_TOOLS_ZIP" -d "$sdk/cmdline-tools"
        mv "$sdk/cmdline-tools/cmdline-tools" "$sdk/cmdline-tools/latest"
    fi
    local sdkmanager="$sdk/cmdline-tools/latest/bin/sdkmanager"
    yes | "$sdkmanager" --sdk_root="$sdk" --licenses >/dev/null || true
    "$sdkmanager" --sdk_root="$sdk" \
        "platform-tools" "$ANDROID_PLATFORM" "$ANDROID_BUILD_TOOLS" \
        "$ANDROID_NDK_PKG" "$ANDROID_CMAKE_PKG"
    stamp_set sdk
}

# ---------------------------------------------------------------------------
stage_fpc_host() {
    stamp_ok fpc-host && { log "FPC host stage already done"; return 0; }
    if command -v fpc >/dev/null && fpc -iV | grep -q "^$FPC_VER"; then
        log "Host FPC $FPC_VER already present"
    elif command -v apt-get >/dev/null; then
        log "Installing host FPC $FPC_VER via apt"
        apt-get update -qq --allow-releaseinfo-change >/dev/null 2>&1 || true
        apt-get install -y -qq fpc make || die "apt install fpc failed"
    else
        log "Installing host FPC $FPC_VER from SourceForge tarball"
        fetch "$FPC_BIN_URL" "$DOWNLOADS/fpc-$FPC_VER.x86_64-linux.tar"
        local tmp; tmp=$(mktemp -d)
        tar -xf "$DOWNLOADS/fpc-$FPC_VER.x86_64-linux.tar" -C "$tmp"
        ( cd "$tmp"/fpc-*; echo "$TOOLCHAIN_DIR/fpc-host" | sh install.sh ) \
            || die "FPC binary install failed"
        export PATH="$TOOLCHAIN_DIR/fpc-host/bin:$PATH"
    fi
    fpc -iV | grep -q "^$FPC_VER" || die "host fpc is not $FPC_VER"
    stamp_set fpc-host
}

# ---------------------------------------------------------------------------
# clang-based binutils wrappers: FPC invokes ${BINUTILSPREFIX}as / ld; modern
# NDKs only ship LLVM, whose clang/lld accept (nearly) the same flags.
write_wrappers() {
    local ndk; ndk=$(ls -d "$TOOLCHAIN_DIR"/sdk/ndk/* | sort -V | tail -1)
    local llvm="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
    [ -x "$llvm/clang" ] || die "NDK clang not found under $ndk"
    local wrap="$TOOLCHAIN_DIR/binutils-wrappers"
    rm -rf "$wrap"; mkdir -p "$wrap"

    local triple prefix
    for abi in aarch64 arm x86_64; do
        case "$abi" in
            aarch64) triple="aarch64-linux-android$ANDROID_API"; prefix="aarch64-linux-android-" ;;
            arm)     triple="armv7a-linux-androideabi$ANDROID_API"; prefix="arm-linux-androideabi-" ;;
            x86_64)  triple="x86_64-linux-android$ANDROID_API"; prefix="x86_64-linux-android-" ;;
        esac
        # assembler:
        #  * arm: FPC emits pre-UAL "divided" syntax (strneb, ...) that clang's
        #    integrated assembler rejects -> use GNU as (binutils-arm-linux-gnueabi);
        #    it only produces plain ELF objects, which ld.lld links fine.
        #  * aarch64/x86_64: clang integrated assembler is gas-compatible, but
        #    gas-only driver flags must be forwarded with -Wa, (or dropped).
        if [ "$abi" = arm ] && command -v arm-linux-gnueabi-as >/dev/null; then
            cat > "$wrap/${prefix}as" <<EOF
#!/usr/bin/env bash
exec arm-linux-gnueabi-as "\$@"
EOF
            chmod +x "$wrap/${prefix}as"
        else
        cat > "$wrap/${prefix}as" <<EOF
#!/usr/bin/env bash
args=()
expect_defsym=0
for a in "\$@"; do
    if [ "\$expect_defsym" = 1 ]; then
        args+=("-Wa,-defsym,\$a"); expect_defsym=0; continue
    fi
    case "\$a" in
        --defsym)   expect_defsym=1 ;;     # gas flag: forward to integrated assembler
        --defsym=*) args+=("-Wa,-defsym,\${a#--defsym=}") ;;
        -meabi=*) ;;                       # gas-only, not understood by clang
        *) args+=("\$a") ;;
    esac
done
exec "$llvm/clang" --target=$triple -c -x assembler -Qunused-arguments "\${args[@]}"
EOF
        fi
        # linker: ld.lld with the NDK sysroot library dirs (for crtbegin_so.o,
        # -lc, -llog, ...), dropping GNU-ld flags lld rejects and forcing
        # 16 KB page alignment (Play requirement; FPC hardcodes 4 KB).
        local libtriple="${prefix%-}"      # sysroot dir: arm-linux-androideabi, not armv7a-...
        local sysroot="$ndk/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
        cat > "$wrap/${prefix}ld" <<EOF
#!/usr/bin/env bash
args=()
skip=0
prev=""
for a in "\$@"; do
    if [ "\$skip" = 1 ]; then skip=0; prev=""; continue; fi
    case "\$a" in
        -b) skip=1 ;;                      # -b elfXX-... : lld has no -b
        *)
            # FPC emits 'INSERT AFTER .data1' in its linker script; that
            # section never exists and ld.lld (unlike GNU ld) errors out.
            # Re-anchor the .fpc metadata block after the real .data.
            if [ "\$prev" = "-T" ] && [ -f "\$a" ]; then
                sed -i -e 's/^  \.data\([ ]*\):\$/  .fpcmeta\1:/' \
                       -e 's/^INSERT AFTER \.data1\$/INSERT AFTER .data/' "\$a"
            fi
            args+=("\$a")
            prev="\$a"
            ;;
    esac
done
exec "$llvm/ld.lld" \
    -L"$sysroot/usr/lib/$libtriple/$ANDROID_API" \
    -L"$sysroot/usr/lib/$libtriple" \
    "\${args[@]}" \
    -z max-page-size=16384
EOF
        for tool in ar strip objcopy objdump ranlib; do
            printf '#!/usr/bin/env bash\nexec "%s/llvm-%s" "$@"\n' "$llvm" "$tool" \
                > "$wrap/${prefix}${tool}"
        done
        chmod +x "$wrap/${prefix}"*
    done
    echo "$wrap"
}

stage_fpc_cross() {
    stamp_ok fpc-cross && { log "FPC cross stage already done"; return 0; }
    log "Building FPC $FPC_VER cross-compilers for android (aarch64, arm, x86_64)"
    fetch "$FPC_SRC_URL" "$DOWNLOADS/fpc-source-$FPC_VER.tar.gz"
    local src="$TOOLCHAIN_DIR/fpc-src"
    if [ ! -d "$src" ]; then
        mkdir -p "$src"
        tar -xzf "$DOWNLOADS/fpc-source-$FPC_VER.tar.gz" -C "$src" --strip-components=1
    fi
    local wrap; wrap=$(write_wrappers)
    local prefix="$TOOLCHAIN_DIR/fpc-android"
    local startpp; startpp=$(fpc -PB)   # path of the host ppcx64

    local cpu binprefix crossopt
    for cpu in aarch64 arm x86_64; do
        case "$cpu" in
            aarch64) binprefix="aarch64-linux-android-"; crossopt="" ;;
            arm)     binprefix="arm-linux-androideabi-";  crossopt="-CpARMV7A -CfVFPV3" ;;
            x86_64)  binprefix="x86_64-linux-android-";   crossopt="" ;;
        esac
        if [ -x "$prefix/lib/fpc/$FPC_VER/ppcross${cpu/aarch64/a64}" ] || \
           { [ "$cpu" = arm ] && [ -x "$prefix/lib/fpc/$FPC_VER/ppcrossarm" ]; } || \
           { [ "$cpu" = x86_64 ] && [ -x "$prefix/lib/fpc/$FPC_VER/ppcrossx64" ]; }; then
            log "cross $cpu-android already installed"; continue
        fi
        log "make crossinstall CPU_TARGET=$cpu OS_TARGET=android"
        make -C "$src" distclean >/dev/null 2>&1 || true
        make -C "$src" crossinstall -j"$(nproc)" \
            FPC="$startpp" \
            CPU_TARGET="$cpu" OS_TARGET=android \
            CROSSBINDIR="$wrap" BINUTILSPREFIX="$binprefix" \
            CROSSOPT="$crossopt" \
            INSTALL_PREFIX="$prefix" \
            || die "crossinstall for $cpu-android failed"
    done
    stamp_set fpc-cross
}

# ---------------------------------------------------------------------------
# Smoke test: compile a minimal Pascal shared library for each ABI with
# `fpc -Cn` (compile only) and link it with the NDK clang driver, proving the
# exact pipeline used by engine/build-engine.sh.
stage_smoke() {
    log "Smoke test: Pascal -> .so via FPC -Cn + NDK clang, all 3 ABIs"
    write_wrappers >/dev/null    # keep wrappers in sync with this script
    local ndk; ndk=$(ls -d "$TOOLCHAIN_DIR"/sdk/ndk/* | sort -V | tail -1)
    local llvm="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
    local prefix="$TOOLCHAIN_DIR/fpc-android"
    local tmp; tmp=$(mktemp -d)
    cat > "$tmp/hwsmoke.pas" <<'EOF'
library hwsmoke;
function smoke_add(a, b: LongInt): LongInt; cdecl;
begin
    smoke_add := a + b;
end;
exports smoke_add;
begin
end.
EOF
    local cpu ppc triple fpcopt
    for cpu in aarch64 arm x86_64; do
        case "$cpu" in
            aarch64) ppc=ppcrossa64; triple="aarch64-linux-android$ANDROID_API"; fpcopt="" ;;
            arm)     ppc=ppcrossarm; triple="armv7a-linux-androideabi$ANDROID_API"; fpcopt="-CpARMV7A -CfVFPV3" ;;
            x86_64)  ppc=ppcrossx64; triple="x86_64-linux-android$ANDROID_API"; fpcopt="" ;;
        esac
        local units="$prefix/lib/fpc/$FPC_VER/units/$cpu-android"
        local out="$tmp/$cpu"; mkdir -p "$out"
        log "smoke: $cpu-android"
        cp "$tmp/hwsmoke.pas" "$out/"
        ( cd "$out" && \
          "$prefix/lib/fpc/$FPC_VER/$ppc" -Tandroid $fpcopt -Cn -O2 \
            -FD"$TOOLCHAIN_DIR/binutils-wrappers" \
            -Fu"$units/rtl" \
            -o"libhwsmoke.so" hwsmoke.pas ) \
            || die "FPC compile failed for $cpu-android"
        # FPC (-Cn) leaves a ppas.sh calling ${BINUTILSPREFIX}ld — i.e. our
        # ld.lld wrapper, which adds the NDK sysroot and 16 KB page alignment.
        ( cd "$out" && sh ./ppas.sh ) || die "link (ppas.sh) failed for $cpu-android"
        "$llvm/llvm-nm" -D "$out/libhwsmoke.so" | grep -q ' T smoke_add' \
            || die "smoke_add not exported for $cpu-android"
        "$llvm/llvm-readelf" -l "$out/libhwsmoke.so" | grep -q "0x4000$" \
            || die "16 KB page alignment missing for $cpu-android"
        log "smoke OK: $cpu-android ($(du -h "$out/libhwsmoke.so" | cut -f1))"
    done
    rm -rf "$tmp"
    log "All smoke tests passed."
}

# ---------------------------------------------------------------------------
main() {
    mkdir -p "$TOOLCHAIN_DIR" "$DOWNLOADS"
    local what="${1:-all}"
    case "$what" in
        sdk)       stage_sdk ;;
        fpc-host)  stage_fpc_host ;;
        fpc-cross) stage_fpc_cross ;;
        smoke)     stage_smoke ;;
        all)       stage_sdk; stage_fpc_host; stage_fpc_cross; stage_smoke ;;
        *)         die "unknown stage: $what" ;;
    esac
    log "Done. TOOLCHAIN_DIR=$TOOLCHAIN_DIR"
}
main "$@"
