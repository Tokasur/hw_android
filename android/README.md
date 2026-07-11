# Hedgewars for Android

A modern Android port of [Hedgewars](https://www.hedgewars.org), the free
turn-based artillery strategy game. The original Free Pascal engine —
gameplay, AI, Lua missions, touch interface — runs natively; a new
Kotlin/Jetpack Compose frontend replaces the desktop Qt frontend.

**v1 scope:** single player against the CPU (quick games, training,
challenges, scenarios, campaigns) and local hotseat multiplayer (2–8 teams on
one device), with touch controls and gamepad support. Online multiplayer is
not part of v1 (the engine does not support it on mobile yet); the groundwork
and the path to it are described below.

| | |
|---|---|
| minSdk | 21 (Android 5.0, 2014) |
| targetSdk / compileSdk | 36 (Play Store requirement from Aug 31, 2026) |
| ABIs | arm64-v8a, armeabi-v7a, x86_64 |
| Native libs | 16 KB page-size aligned (Play requirement) |
| Distribution | AAB with an install-time asset pack (Play) **or** self-contained APK (sideload) |

---

## Architecture

```
┌───────────────────────────── app process ─────────────────────────────┐
│  Kotlin / Compose frontend                                            │
│  · menus, team editor, missions, settings (org.hedgewars.android)     │
│  · GameConnection: loopback TCP server, engine IPC protocol           │
│  · ConfigSerializer: eaddteam/eaddhh/e$…  command stream              │
└──────────────────────────────┬────────────────────────────────────────┘
                               │ 127.0.0.1:<port>  (1-byte length framing)
┌──────────────────────────────┴───────────────── :game process ────────┐
│  GameActivity (SDLActivity subclass)                                  │
│  libmain.so → dlopen(libhwengine.so) → RunEngine(argc, argv)          │
│  Free Pascal engine + SDL2/_image/_mixer/_ttf/_net,                   │
│  Lua 5.1, PhysicsFS, physlayer, lib-hwengine-future (Rust)            │
└───────────────────────────────────────────────────────────────────────┘
```

* The engine is compiled **unmodified in spirit** (a handful of bit-rot fixes,
  see `git log hedgewars/`) by FPC 3.2.2 cross-compilers for the three ABIs,
  then linked with the NDK's `clang`/`ld.lld` with
  `-z max-page-size=16384`.
* The frontend implements the same IPC protocol as the desktop Qt frontend
  (`QTfrontend/game.cpp`): the engine receives only display/audio options on
  the command line and asks for the whole game setup over the socket.
* Each match runs in a separate `:game` process that is killed afterwards, so
  Pascal global state never leaks between games.
* Game data (218 MB) ships as an install-time Play asset pack (`:data`
  module) or inside the APK (`-PdataInApp`), and is copied to `filesDir/Data`
  on first launch.

## Building

Everything is scripted and pinned; the only host requirements are a JDK (17+),
Python 3, `curl`, `git`, `make`, `cc`, and an `apt`-based system for the two
`apt-get install` conveniences (Free Pascal, GNU ARM assembler) — otherwise
install FPC 3.2.2 and `binutils-arm-linux-gnueabi` manually.

```bash
cd android

# 1. Android SDK + NDK r28 + FPC 3.2.2 + cross-compilers (+ smoke test)
./scripts/bootstrap-toolchain.sh all          # ~10 min, ~6 GB

# 2. SDL2 family sources (libsdl.org + GitHub for vendored deps)
./scripts/fetch-sdl.sh

# 3. SDL2 stack + Lua + PhysicsFS + physlayer + Rust helper, all 3 ABIs
./scripts/build-native-deps.sh                # ~15 min

# 4. The Pascal engine → libhwengine.so, all 3 ABIs
./engine/build-engine.sh

# 5. Stage the 218 MB game data (+ touch button sprites)
./scripts/prepare-data.sh

# 6a. Self-contained APK (sideload / out-of-store distribution)
./gradlew :app:assembleRelease -PdataInApp
#    → app/build/outputs/apk/release/app-release.apk  (~260 MB)

# 6b. Play Store bundle (install-time asset pack)
./gradlew :app:bundleRelease
#    → app/build/outputs/bundle/release/app-release.aab

# Unit tests (IPC framing, config serialization, binds format)
./gradlew :app:testDebugUnitTest
```

`TOOLCHAIN_DIR` (default `~/hedgewars-android-toolchain`) relocates the
toolchain. If the Gradle wrapper cannot download a distribution in your
environment, use a locally installed Gradle 8.14+.

### Protocol end-to-end test (no device needed)

Builds the *desktop* engine from the same sources and drives it with the same
IPC implementation the app uses (map preview generation, headless):

```bash
./scripts/host-e2e-test.sh
# OK: alpha/desktop preview received (5872 land pixels, hog limit 32)
```

### Installing the APK

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

Without a configured keystore, release builds are signed with the debug key —
installable everywhere, fine for testing and out-of-store sharing. For real
releases create `android/keystore.properties`:

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

and generate the keystore once with:

```bash
keytool -genkeypair -v -keystore release.keystore -alias hedgewars \
        -keyalg RSA -keysize 4096 -validity 10000
```

## Controls

**Touch** (engine `uTouch.pas`): drag to scroll, pinch to zoom; on-screen
arrows walk, the crosshair aims, the fire button shoots (hold for power);
tap your hedgehog or the corner button for the weapon menu; tap to target
air strikes; dedicated buttons for jumps, weapon timer and pause.

**Gamepad** (defaults, remappable via `settings.ini` `[Binds]`):

| Input | Action |
|---|---|
| D-pad | walk / aim |
| X | fire (hold for power) |
| A / B | high jump / long jump (backflip) |
| Y | weapon menu |
| L1 | precise aim |
| R1 | weapon timer (3 s) |
| Start | pause |
| Right stick click | switch hedgehog |

Connect the controller **before** starting a match (the engine enumerates
joysticks at startup).

## Play Store checklist (for a future release)

* `targetSdk 36` ✔, AAB ✔, 16 KB alignment ✔ (`zipalign -c -P 16 4 …`),
  install-time asset pack ≤ 1.5 GB ✔ (218 MB).
* `INTERNET` permission is required **only** for the loopback IPC socket;
  data-safety form: no data collected, no data shared.
* Replace the placeholder `applicationId org.hedgewars.android` if you do not
  control the `org.hedgewars` namespace, set up Play App Signing, bump
  `versionCode` (`major*10000+minor*100+patch`).
* Hedgewars is **GPL-2.0**: keep the in-app "About & licenses" screen and the
  public source repository link up to date (Play policy allows GPL apps; the
  store listing should link to the source too).
* Content rating: cartoon violence (comparable titles rate PEGI 7 / ESRB E10+).

## Known limitations / v2 path

* **Online multiplayer**: the engine pauses when backgrounded and the mobile
  build has no net-game loop (`hwengine.pas` "Mobile doesn't support online
  multiplayer yet"). The protocol layer to build against lives in
  `project_files/frontlib/net/netconn.c` and `rust/hedgewars-network-protocol`;
  the Kotlin IPC layer here already isolates `TL`-vs-`TN` so a future
  `NetGameConnection` slots in beside `GameConnection`.
* Data is copied out of the APK on first launch (~2×218 MB on disk). A
  zero-copy `PHYSFS_Io` mount of the APK is the planned optimization.
* Hand-drawn maps, demo playback/recording UI and the video recorder are not
  exposed.
* GLES 1.1 renderer (as on iOS); fine everywhere, but Vulkan-only future
  devices would need the engine's GL2 path ported to GLES2.

## Repository layout

```
android/
  scripts/bootstrap-toolchain.sh   SDK/NDK/FPC cross-toolchain (+ smoke test)
  scripts/fetch-sdl.sh             pinned SDL2 family sources
  scripts/build-native-deps.sh     SDL2 stack + lua/physfs/physlayer + Rust lib
  scripts/prepare-data.sh          stages share/hedgewars/Data (+ Buttons)
  scripts/host-e2e-test.sh         desktop-engine IPC end-to-end test
  engine/build-engine.sh           FPC → libhwengine.so (3 ABIs, 16 KB aligned)
  app/                             Kotlin/Compose frontend + SDL Java glue
  data/                            install-time asset pack module
```

Engine patches are intentionally minimal — `git log --oneline -- hedgewars/`
shows every change with its rationale.
