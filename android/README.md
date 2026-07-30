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

## What the frontend offers

* **Quick game** against the CPU and **local multiplayer** for 2–8 teams on one
  device, each seat human or CPU (5 AI levels) — pick which of your teams takes
  a seat, its clan colour and its role.
* **Teams**: create and edit teams (8 hedgehog names, hat, grave, fort, voice,
  flag); a team with no fort of its own gets a different one in every forts
  match, as the desktop does.
* **Maps**: random, maze, perlin, **forts** (with the desktop's fort-distance
  slider) or any named map, plus theme and seed.
* **Rules**: the desktop's 17 game schemes and 13 weapon sets, editable, plus
  the multiplayer **game styles** (a style's `.cfg` pins its scheme and weapon
  set, as on the desktop).
* **Missions**: training, challenges, scenarios and campaigns, with campaign
  progress persisted per team.
* **Downloadable content**: an in-app manager for the packs published on
  <https://hedgewars.org/content.html> — install, remove, and their maps,
  themes, hats, forts, voices and scripts appear in every picker (the desktop
  frontend has no such manager).
* **End-of-match results**: the winner, the medalled ranking, the notable
  moments and the clans' health curves — the engine streams all of it and the
  desktop shows the same page.
* **Settings**: sound/music, render scale for the in-game touch UI, gamepad
  binds, and the interface language (system, English or French; the engine's
  own locale follows).

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

# Unit tests: IPC framing, config serialization, scheme/weapon-set formats,
# binds, pack index, DLC catalog parsing, end-of-match stats, demo recording
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

**Gamepad** (standard Xbox-style layout via SDL's game controller API —
any pad SDL can map gets the exact same bindings; defaults remappable via
`settings.ini` `[Binds]`):

| Input | Action |
|---|---|
| D-pad / left stick | walk / aim |
| Right stick | move the camera |
| Right stick click | confirm the target of aimed weapons (teleport, homing bee, air strikes) |
| X | fire (hold for power) — in the weapon menu: pick |
| A | high jump — in the weapon menu: pick |
| B | long jump (backflip) — in the weapon menu: close |
| Y | weapon menu |
| While the menu is open | D-pad/left stick browse the weapons |
| R1 / R2 | cycle the grenade fuse (1–5 s) |
| L1 | cycle grenade bounciness |
| L2 (held) | precise aim |
| Start | pause |
| Left stick click | switch hedgehog |

Connect the controller **before** starting a match (the engine enumerates
controllers at startup).

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
* Replays record and play back faithfully since 0.3.0. They were off in 0.2.9
  because every replayed turn reported "Desync detected": the cause was not the
  recording but the engine itself, which lost the sign of its fixed-point
  numbers on every Pascal-to-Rust call (`hwf_raw`/`hwf_with_sign` took a Rust
  `bool`, which is read as a full register, while Free Pascal only wrote its
  one-byte `boolean`). Flags crossing that boundary are 32-bit now. See
  [docs/online-readiness.md](docs/online-readiness.md) — the same determinism
  is what online play will rely on. Demos recorded by an older build do not
  replay, and 0.3.0 engines cannot play across the network with older ones.
* Hand-drawn maps and the video recorder are not exposed.
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
