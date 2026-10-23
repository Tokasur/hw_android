# Hedgewars Android

Android port of Hedgewars, the free turn-based artillery strategy game, based on hedgewars/hw.

This repository is an Android port of Hedgewars forked from hedgewars/hw. The Free Pascal engine runs natively on Android behind a Kotlin and Jetpack Compose frontend. The upstream desktop README is preserved at doc/README.upstream.md.

[![License: GPL v2](https://img.shields.io/badge/License-GPL_v2-blue.svg)](COPYING)
[![Assets: GFDL 1.2](https://img.shields.io/badge/Assets-GFDL_1.2-lightgrey.svg)](COPYING)
[![minSdk 21](https://img.shields.io/badge/minSdk-21-green.svg)](android/app/build.gradle.kts)
[![targetSdk 36](https://img.shields.io/badge/targetSdk-36-green.svg)](android/app/build.gradle.kts)
[![NDK r28c](https://img.shields.io/badge/NDK-r28c-orange.svg)](android/scripts/bootstrap-toolchain.sh)
[![Latest Release](https://img.shields.io/github/v/release/Tokasur/hw_android?label=release)](https://github.com/Tokasur/hw_android/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/Tokasur/hw_android/total?label=downloads)](https://github.com/Tokasur/hw_android/releases/latest)

Repository state at time of writing: HEAD 8fbcfe050e2eacdd947c4ba9262f93e57b4113cd dated Thu Jul 30 22:45:29 2026 +0200, origin https://github.com/Mark44928/hw_android.git, upstream https://github.com/Tokasur/hw_android.git, versionName 0.3.0 and versionCode 300 in android/app/build.gradle.kts, remote release tags 0.2.8 and 0.2.9 visible through the GitHub releases page. Local clones may show no local tags because tags were never fetched into this checkout.

## Contents

- [1 Header](#1-header)
- [2 Overview and feature set](#2-overview-and-feature-set)
- [3 Core architecture](#3-core-architecture)
- [4 Touch and controller input matrix](#4-touch-and-controller-input-matrix)
- [5 Requirements](#5-requirements)
- [6 Build guide: Android Studio](#6-build-guide-android-studio)
- [7 Build guide: CLI and Termux](#7-build-guide-cli-and-termux)
- [8 Installation and sideloading](#8-installation-and-sideloading)
- [9 Assets, data paths and DLC](#9-assets-data-paths-and-dlc)
- [10 Audio and SDL quirks](#10-audio-and-sdl-quirks)
- [11 Configuration and settings reference](#11-configuration-and-settings-reference)
- [12 Known issues and limitations](#12-known-issues-and-limitations)
- [13 Project layout](#13-project-layout)
- [14 Contributing and debugging](#14-contributing-and-debugging)
- [15 License and credits](#15-license-and-credits)
- [Notes on web versus repository discrepancies](#notes-on-web-versus-repository-discrepancies)

## 1 Header

Covered by the title block, badge row, and table of contents above. The badges read their values from android/app/build.gradle.kts for minSdk 21 with targetSdk 36 and compileSdk 36, from android/scripts/bootstrap-toolchain.sh for NDK r28c package ndk;28.2.13676358, from android/scripts/build-native-deps.sh and android/engine/build-engine.sh for ABIs arm64-v8a with armeabi-v7a and x86_64, from .github/workflows/ci.yml for CI status, and from https://github.com/Tokasur/hw_android/releases/latest for release and download counts. License badges point at COPYING. The upstream desktop text lives at doc/README.upstream.md.

## 2 Overview and feature set

All features below were verified in files under android/app/src/main/java/org/hedgewars/android plus engine sources under hedgewars and data under share/hedgewars/Data.

Quick game against CPU and local hotseat multiplayer for 2 through 8 teams on one device. Verified in android/app/src/main/java/org/hedgewars/android/ui/newgame/NewGameScreen.kt with team slots carrying color index and hog count, and in android/app/src/main/java/org/hedgewars/android/config/GameConfig.kt with default theme Nature and UUID braced seed. Each seat is human or CPU.

Team selection with 5 AI difficulty levels. Verified in android/app/src/main/java/org/hedgewars/android/config/Team.kt where difficulty 0 means human and 1 through 5 means CPU with 1 strongest, and in android/app/src/main/res/values/strings.xml with labels Brutal and Aggressive and Average and Weak and Very weak and Human. Team editor stores 8 hedgehog names per team, hat, grave, fort, voicepack, flag, verified in the same Team.kt file with MAX_HOGS 8.

Maps cover random and maze and perlin and forts and named maps, plus theme and seed. Verified in android/app/src/main/java/org/hedgewars/android/config/GameConfig.kt enum MapGen with REGULAR 0 and MAZE 1 and PERLIN 2 and FORTS 4, with DRAWN 3 absent and documented as unsupported. Fort distance uses feature_size, verified in android/app/src/main/java/org/hedgewars/android/config/ConfigSerializer.kt with e$feature_size and e$mapgen and e$template_filter.

Rules use 17 schemes and 13 weapon sets with full desktop parity plus editing and duplication. Verified in android/app/src/main/java/org/hedgewars/android/config/Scheme.kt PRESETS with Default plus 16 further entries ending in Racer and Sudden Death, and in android/app/src/main/java/org/hedgewars/android/config/WeaponSet.kt PRESETS with Default plus 12 further entries ending in HedgeEditor, SIZE 60, LOADOUT max 9, other fields max 8. Editors live in android/app/src/main/java/org/hedgewars/android/ui/schemes and android/app/src/main/java/org/hedgewars/android/ui/weapons.

Multiplayer game styles with scheme and weapon enforcement like desktop. Verified in android/app/src/main/java/org/hedgewars/android/data/MissionsRepository.kt GameStyle with script path Scripts/Multiplayer plus scheme and weapons triple, LOCKED constant, cfg line 1 scheme with line 2 weapons, star means free choice, missing file means locked. Shipped scripts live in share/hedgewars/Data/Scripts/Multiplayer and include Racer and Highlander and Shoppa family entries.

Missions cover training and challenges and scenarios and campaigns with campaign variable persistence. Verified in android/app/src/main/java/org/hedgewars/android/data/MissionsRepository.kt training and challenges and scenarios and campaigns functions, and in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt CampaignStore wiring for V and v variables.

DLC manager for packs published at https://hedgewars.org/content.html with install and remove. Verified in android/app/src/main/java/org/hedgewars/android/data/CatalogRepository.kt with default catalog URL and timeouts, in android/app/src/main/java/org/hedgewars/android/data/DlcCatalog.kt parser for dlc_type cells and hwp or zip anchors, in android/app/src/main/java/org/hedgewars/android/data/PacksRepository.kt installing to user Data with atomic rename, and in android/app/src/main/java/org/hedgewars/android/ui/dlc/DlcScreen.kt. Installed maps and themes and hats and forts and voices and scripts appear in pickers through android/app/src/main/java/org/hedgewars/android/data/PackContentIndex.kt.

End-of-match results with winner sentence and medal ranking and details and health graphs. Verified in android/app/src/main/java/org/hedgewars/android/config/StatsReport.kt with winnerText and rankings and details and healthSeries and graphTitle and fromReplay, and in android/app/src/main/java/org/hedgewars/android/ui/stats/StatsScreen.kt with drawable resources under android/app/src/main/res/drawable-nodpi for ranking and graph and medals.

Fort mode with distinct fort per clan. Verified in android/app/src/main/java/org/hedgewars/android/config/FortPicker.kt with per-clan distinct assignment and fallback Plane, and in android/app/src/main/java/org/hedgewars/android/config/Team.kt with FORT_RANDOM star marker and FORT_FALLBACK Plane.

Gamepad support through SDL game controller API with defaults remappable through settings.ini Binds. Verified in android/app/src/main/java/org/hedgewars/android/data/BindsWriter.kt and hedgewars/uInputHandler.pas.

Menu music toggle with seamless rotation handling. Verified in android/app/src/main/java/org/hedgewars/android/audio/MenuMusic.kt with MediaPlayer looping and in android/app/src/main/java/org/hedgewars/android/MainActivity.kt with isChangingConfigurations guard.

Interface language system with English and French where the engine locale follows. Verified in android/app/src/main/java/org/hedgewars/android/data/AppLocale.kt with SUPPORTED empty plus en plus fr, in android/app/src/main/res/xml/locales_config.xml with en and fr, in android/app/src/main/res/values/strings.xml and android/app/src/main/res/values-fr/strings.xml, and in android/app/src/main/java/org/hedgewars/android/engine/EngineArgs.kt localeFileFor.

Touch plus controller Controls screen. Verified in android/app/src/main/java/org/hedgewars/android/ui/about/ControlsScreen.kt with Touch and Jumping and Gamepad and Ammo menu and Weapon sections backed by android/app/src/main/res/values/strings.xml rows.

Replay limitation: recording and playback are implemented and enabled since 0.3.0 through android/app/src/main/java/org/hedgewars/android/Features.kt REPLAYS true, but demos from builds before 0.3.0 do not replay and 0.3.0 engines cannot play network games with older engines, documented in android/docs/online-readiness.md. The 0.2.9 release notes stated replays were written but disabled because of desync.

## 3 Core architecture

Two processes. The app process hosts Kotlin and Compose menus. The game process hosts SDLActivity with the native engine. Communication uses a loopback TCP socket with the same IPC protocol as QTfrontend/game.cpp.

```text
app process                         loopback 127.0.0.1     game process
Kotlin Compose frontend             1-byte length framing  GameActivity extends SDLActivity
 menus plus team editor             port injected          libmain.so SDL_main
 missions plus settings             into engine args       dlopen libhwengine.so
 GameConnection server lives        ------------------>    RunEngine in Pascal
 in game process, not menu          config stream          Pascal engine plus SDL2 family
 ConfigSerializer builds            TL or TD first frame   Lua 5.1 plus PhysicsFS plus physlayer
 eaddteam and eaddhh stream                               Rust hwengine_future plus gl4es
 StatsParser consumes i frames                            Data under filesDir Data
 DemoRecorder writes hwd                                  user prefix under filesDir user
```

Language and toolchain map.

| Directory | Language | Compiler | Output artifact |
|---|---|---|---|
| android/app/src/main/java/org/hedgewars/android | Kotlin | Kotlin 2.1.21 through AGP 8.11.1 with Gradle 8.14.3, JVM target 17 per android/app/build.gradle.kts and android/gradle/libs.versions.toml | app-release.apk and app-release.aab under app/build/outputs per android/README.md |
| android/app/src/main/java/org/libsdl/app | Java | Same Kotlin and AGP toolchain, Java 17 per android/app/build.gradle.kts | SDLActivity and SDLSurface classes packaged in the same APK |
| android/app/src/main/java/org/hedgewars/hedgeroid/EngineProtocol | Java | Same toolchain | PascalExports JNI declarations bound to libhwengine.so |
| android/app/src/main/cpp | C | NDK clang through android.toolchain.cmake per android/scripts/build-native-deps.sh, CMake 3.22.1 per android/scripts/bootstrap-toolchain.sh | liblua.so from lua5.1 target plus libphysfs.so plus libphyslayer.so plus libmain.so per android/app/src/main/cpp/CMakeLists.txt |
| hedgewars plus project_files/Android-build include path | Free Pascal | FPC 3.2.2 cross compilers ppcrossa64 plus ppcrossarm plus ppcrossx64 with -Tandroid -Cn per android/engine/build-engine.sh | libhwengine.so per ABI staged into android/app/src/main/jniLibs |
| rust/lib-hwengine-future | Rust | cargo with per-ABI linker from NDK LLVM plus RUSTFLAGS max-page-size per android/scripts/build-native-deps.sh | libhwengine_future.so per ABI |
| android/third_party/gl4es-shared plus gl4es checkout | C | NDK clang with NOX11 plus NO_GBM plus DEFAULT_ES=2 plus ANDROID per android/third_party/gl4es-shared/CMakeLists.txt | libgl4es.so per ABI |
| third_party SDL2 family | C and CMake | NDK CMake toolchain per android/scripts/build-native-deps.sh | libSDL2.so plus libSDL2_image.so plus libSDL2_mixer.so plus libSDL2_ttf.so plus libSDL2_net.so per ABI |
| share/hedgewars/Data | Data files plus Lua | Staged by android/scripts/prepare-data.sh with rsync and Data.manifest | filesDir Data after first-launch copy by android/app/src/main/java/org/hedgewars/android/data/DataInstaller.kt |

Engine-to-Android JNI bridge.

Load order is defined in android/app/src/main/java/org/hedgewars/android/engine/EngineLoader.kt libraries array as SDL2 then SDL2_image then SDL2_mixer then SDL2_ttf then SDL2_net then lua then physfs then physlayer then hwengine_future then gl4es then hwengine. GameActivity appends main in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt getLibraries. SDLActivity loads each through SDL.loadLibrary in android/app/src/main/java/org/libsdl/app/SDLActivity.java. libmain.so then dlopens libhwengine.so at game start in android/app/src/main/cpp/hedgewars_main.c, which keeps the app skeleton buildable without the engine.

Entry chain: MainActivity launches GameActivity through GameLauncher intent in android/app/src/main/java/org/hedgewars/android/game/GameLauncher.kt. GameActivity starts GameConnection on 127.0.0.1 with an ephemeral port in android/app/src/main/java/org/hedgewars/android/engine/GameConnection.kt, injects the bound port into --port in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt getArguments, SDLActivity loads native libraries and calls SDL_main with those arguments, hedgewars_main.c dlopens libhwengine.so and calls RunEngine from hedgewars/hwengine.pas, RunEngine parses args in hedgewars/ArgParsers.pas and connects back to the loopback server through hedgewars/uIO.pas.

JNI table. Java and Kotlin signatures are the declaration side, native symbols are the Pascal export side in hedgewars/hwLibrary.pas.

| Java and Kotlin declaration | Native symbol | Purpose |
|---|---|---|
| org.hedgewars.hedgeroid.EngineProtocol.PascalExports.HWversionInfoNetProto returning int per android/app/src/main/java/org/hedgewars/hedgeroid/EngineProtocol/PascalExports.java | JNI_HW_versionInfoNet exported as Java prefix plus HWversionInfoNetProto per hedgewars/hwLibrary.pas | Return cNetProtoVersion, currently 60 per CMakeLists.txt |
| org.hedgewars.hedgeroid.EngineProtocol.PascalExports.HWversionInfoVersion returning String per the same PascalExports.java | JNI_HW_versionInfoVersion exported as Java prefix plus HWversionInfoVersion per hedgewars/hwLibrary.pas | Return cVersionString for display and diagnostics |
| org.hedgewars.hedgeroid.EngineProtocol.PascalExports.HWGenLandPreview with int port per the same PascalExports.java | JNI_HW_GenLandPreview exported as Java prefix plus HWGenLandPreview per hedgewars/hwLibrary.pas | Generate map preview into the loopback socket for the NewGameScreen preview path |
| org.hedgewars.hedgeroid.EngineProtocol.PascalExports.HWgetNumberOfWeapons returning int per the same PascalExports.java | HW_getNumberOfweapons exported under Java prefix per hedgewars/hwLibrary.pas | Return ord high TAmmoType, backing the 60-slot weapon UI |
| org.hedgewars.hedgeroid.EngineProtocol.PascalExports.HWgetMaxNumberOfHogs returning int per the same PascalExports.java | HW_getMaxNumberOfHogs exported under Java prefix per hedgewars/hwLibrary.pas | Return cMaxHHIndex plus 1 |
| org.hedgewars.hedgeroid.EngineProtocol.PascalExports.HWgetMaxNumberOfTeams returning int per the same PascalExports.java | HW_getMaxNumberOfTeams exported under Java prefix per hedgewars/hwLibrary.pas | Return cMaxTeams |
| SDL_main in android/app/src/main/cpp/hedgewars_main.c | RunEngine in hedgewars/hwengine.pas and hedgewars/hwLibrary.pas exports | Main engine entry reached through libmain.so dlopen |

Arguments and data directory passing: EngineArgs in android/app/src/main/java/org/hedgewars/android/engine/EngineArgs.kt sends --internal first with --port plus --prefix64 plus --user-prefix64 base64 plus --locale plus --width plus --height plus --fullscreen-width plus --fullscreen-height plus --fullscreen plus optional --nosound plus --nomusic plus --showfps plus --low-quality plus optional --landpreview. ArgParsers in hedgewars/ArgParsers.pas decodes --prefix64 into PathPrefix and --user-prefix64 into UserPathPrefix. GamePaths in android/app/src/main/java/org/hedgewars/android/data/GamePaths.kt defines dataDir as files Data and userDir as files user. GameLauncher passes absolute paths in android/app/src/main/java/org/hedgewars/android/game/GameLauncher.kt.

Input injection: touch arrives through SDL finger events into hedgewars/uTouch.pas onTouchDown with onTouchMotion and onTouchUp. Gamepad arrives through SDL game controller axes and buttons mapped by BindsWriter settings.ini Binds into hedgewars/uInputHandler.pas. Quit and pause also arrive through GameConnection send of eforcequit and epause in android/app/src/main/java/org/hedgewars/android/engine/GameConnection.kt and android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt.

Engine reporting: q means finished, Q means interrupted, E means fatal error, i plus kind plus text means stats, H means halted, m means demo invalid, V and v mean mission variable fetch with store, all handled in android/app/src/main/java/org/hedgewars/android/engine/GameConnection.kt run loop. GameActivity persists outcome through android/app/src/main/java/org/hedgewars/android/engine/EngineOutcome.kt file last_outcome and records through android/app/src/main/java/org/hedgewars/android/engine/MatchRecords.kt files last_stats and last_demo.hwd.

Thread model and lifecycle: IPC runs on thread hw-game-ipc in android/app/src/main/java/org/hedgewars/android/engine/GameConnection.kt. Quit and resume use worker threads hw-quit and hw-resume in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt because socket I/O is forbidden on the main thread. Each match runs in a separate game process declared in android/app/src/main/AndroidManifest.xml. GameActivity kills its process in onDestroy with Process.killProcess, finishes on onStop so backgrounding ends the match, rejects ghost recreation after process death, and rejects reuse on onNewIntent. Menu music stops when GameActivity foregrounds because MainActivity onStop fires, verified in android/app/src/main/java/org/hedgewars/android/MainActivity.kt and android/app/src/main/java/org/hedgewars/android/audio/MenuMusic.kt.

Pascal engine build into Gradle: android/engine/build-engine.sh generates config.inc from hedgewars/config.inc.in using HEDGEWARS_PROTO_VER 60 and project version 1.1.0 from CMakeLists.txt, compiles hedgewars/hwLibrary.pas with ppcross plus -Tandroid plus -Cn plus -O2 plus -Xs plus -dDEBUGFILE plus -FD wrappers plus -Fu units and hedgewars and project_files/Android-build plus -Fl deps plus -FE and -FU out, links with sh ./ppas.sh through an ld.lld wrapper that adds the NDK sysroot and forces -z max-page-size=16384, verifies RunEngine export and HWGenLandPreview export and 0x4000 alignment with llvm-nm and llvm-readelf, then copies libhwengine.so into android/app/src/main/jniLibs per ABI for Gradle packaging. The C support libraries are built by android/scripts/build-native-deps.sh target hw_support with -DHW_REPO_ROOT and -DHW_DEPS_PREFIX pointing at android/app/src/main/cpp/CMakeLists.txt.

## 4 Touch and controller input matrix

Touch behavior below is implemented in hedgewars/uTouch.pas with widget geometry in hedgewars/uWorld.pas. Strings quoted here are the user-facing Controls rows in android/app/src/main/res/values/strings.xml as shown by android/app/src/main/java/org/hedgewars/android/ui/about/ControlsScreen.kt.

| Control | Gesture and position | Engine action | Behavior notes |
|---|---|---|---|
| Move left arrow | Tap and hold bottom-left arrowWidget at screen left minus half width plus quarter frame offset per hedgewars/uWorld.pas | Plus left on down, minus left on release per hedgewars/uTouch.pas | Hold keeps walking. Hit area uses SetActiveWithPadding around the sprite. |
| Move right arrow | Tap and hold arrowRight right of arrowLeft per hedgewars/uWorld.pas | Plus right on down, minus right on release per hedgewars/uTouch.pas | Hold keeps walking. Fire button sits right of this arrow with a 0.3 width gap to reduce misclicks per hedgewars/uWorld.pas comment. |
| Aim widget and crosshair | Drag the crosshair or use arrowUp and arrowDown widgets that appear above the jump button per hedgewars/uWorld.pas | Plus up and plus down with ProcessTouch angle tracking per hedgewars/uTouch.pas | Crosshair drag calls aim with world conversion. Release sends minus up and minus down. Up and down arrows are hidden until aiming context shows them. |
| Fire button | Tap or hold fireButton right of move-right arrow per hedgewars/uWorld.pas | Plus attack on down, minus attack on release per hedgewars/uTouch.pas | Tap shoots. Hold and release charges power. Hit area padded. |
| Jump button short tap | Short tap on jumpWidget center-bottom per hedgewars/uWorld.pas | hjump per hedgewars/uTouch.pas onTouchClick | Hops up and slightly forward over small gaps per strings row controls_jump_short_out. Threshold under clickTime 200 per hedgewars/uTouch.pas. |
| Jump button second tap mid-air | Tap again while airborne per strings row controls_jump_double_out | Engine double jump path triggered by second hjump | Backflip jumps higher and backwards per strings text. No separate onTouchDoubleClick action because onTouchDoubleClick is an empty stub per hedgewars/uTouch.pas. |
| Jump button long press | Long press on jumpWidget per hedgewars/uTouch.pas onTouchLongClick | ljump per hedgewars/uTouch.pas | Flatter longer forward leap per strings row controls_jump_long_out. Movement beyond 30 px counts as moved per fingerHasMoved. |
| Ammo menu open | Tap own hedgehog or AMWidget briefcase at center-left per hedgewars/uTouch.pas and hedgewars/uWorld.pas | Sets bShowAmmoMenu true, no engine command yet | Tap detection uses half RectSize 48 around hog and widget, with baseRectSize 96 per hedgewars/uTouch.pas. |
| Ammo menu pick | Tap inside AmmoRect per hedgewars/uTouch.pas | Moves CursorPoint to tap then sends put | In-menu put selects the highlighted weapon. Tap outside sets bShowAmmoMenu false and closes. |
| Target placement | Tap ground when targetting armed per hedgewars/uTouch.pas | put on utilityWidget release or tap path, plus targetted flag | Armed only for single finger with NeedTarget ammo and locally driven team. After targeting, caption PressTarget shows. Utility widget shows timer or target or switch sprite per hedgewars/uWorld.pas. |
| Weapon timer | Tap utilityWidget above arrowLeft per hedgewars/uWorld.pas | Slash timer with tmp mod 5 plus 1 per hedgewars/uTouch.pas | Only when current ammo has ammoprop_Timerable. Cycles fuse 1 through 5 seconds. |
| Bounciness | Tap utilityWidget2 beside utilityWidget per hedgewars/uWorld.pas | Plus precise then timer with bounciness index per hedgewars/uTouch.pas, cleared by ProcessTouch sending minus precise | Only when current ammo has ammoprop_SetBounce. Uses HHGetBouncinessMsg. |
| Switch hedgehog in Switch context | Release utilityWidget when CurAmmoGear is amSwitch per hedgewars/uTouch.pas | switch | Contextual. Outside choose-target and Switch contexts the same tap is a no-op diagnostic console line. |
| Camera pan | Single-finger drag on empty terrain per hedgewars/uTouch.pas | CursorPoint moves opposite finger when moveCursor true, follows when false | Direction flips when ammo menu or targetting is active so cursor follows finger. Replay and externally driven teams use the corrected branch per code comment. |
| Pinch zoom | Two fingers, pinch distance delta per hedgewars/uTouch.pas | ZoomValue equals baseZoomValue minus zoom times cMinMaxZoomLevelDelta, clamped | Limits cMaxZoomLevel 0.5 with cMinZoomLevel 3.5, or 1.0 with 3.0 in the low-res branch, per hedgewars/uVariables.pas. No persisted default beyond cDefaultZoomLevel init. Reset path is absent in the touch unit and therefore returns to default on new turn and new match. |
| Pause button | Tap pauseButton flush at top-right per hedgewars/uWorld.pas | pause per hedgewars/uTouch.pas | Debounced to one toggle per 300 ms using RealTicks and lastPauseToggle because Pixel 9 delivered double taps. |
| Quit Yes and No dialog | Back button or gamepad Select opens Android AlertDialog in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt, not the engine overlay | Yes sends eforcequit on worker thread, No and cancel send epause resume | Engine gsConfirm tap path in hedgewars/uTouch.pas confirms quit on any tap, but Android intercepts Back before SDL so the engine overlay is never the primary path. Dialog keys: A confirms, B or Select cancels, back dismisses. |
| Chat | This repository contains no touch chat widget; chat frames s and b are only logged in android/app/src/main/java/org/hedgewars/android/engine/GameConnection.kt | Logged through Log.d, not shown | Documented absence. Desktop chat has no touch equivalent here. |

Gamepad bindings exactly as coded in android/app/src/main/java/org/hedgewars/android/data/BindsWriter.kt GAMEPAD_BINDS with SDL semantic numbering documented in the same file.

| Gamepad input | Engine action | Behavior notes |
|---|---|---|
| D-pad up j0b11 | Plus up | Walk and aim up. Duplicated on left stick Y negative j0a1d. |
| D-pad down j0b12 | Plus down | Walk and aim down. Duplicated on left stick Y positive j0a1u. |
| D-pad left j0b13 | Plus left | Walk left. Duplicated on left stick X negative j0a0d. |
| D-pad right j0b14 | Plus right | Walk right. Duplicated on left stick X positive j0a0u. |
| Left stick X and Y | Same four walk and aim commands as D-pad | One command may bind to several keys on MOBILE because rebind displacement is disabled in hedgewars/uInputHandler.pas. Each key appears at most once. |
| Right stick X j0a2u and j0a2d | Plus cur_r and plus cur_l | Move camera horizontally. |
| Right stick Y j0a3u and j0a3d | Plus cur_d and plus cur_u | Move camera vertically. Android Y points down, engine names positive direction u, documented in BindsWriter. |
| X j0b2 | Plus attack | Fire with hold for power. In weapon menu, picks. |
| A j0b0 | hjump | High jump. In weapon menu, picks. Second press mid-air backflips. |
| B j0b1 | ljump | Long jump forward. In weapon menu, closes. |
| Y j0b3 | ammomenu | Opens weapon menu. In menu, Y also closes through B or Y path per strings. |
| D-pad and left stick while menu open | Browse weapons | Menu browsing uses the same walk binds. |
| R1 j0b10 | timer_u | Cycle grenade fuse 1 through 5 seconds. Self-guards on non-applicable weapons. |
| R2 j0a5u | timer_u | Same fuse cycle on trigger positive half. Trigger rest is 0 so negative half never fires. |
| L1 j0b9 | Plus bounce | Cycle bounciness. Self-guards on non-applicable weapons. |
| L2 j0a4u held | Plus precise | Precise aim while held. |
| Start j0b6 | pause | Pause and resume. |
| Left stick click j0b7 | switch | Switch hedgehog. |
| Right stick click j0b8 | put | Confirm target for teleport and homing bee and air strikes. No-op outside choose-target, picks inside weapon menu. |
| Back key ac_back | quit | Always written as quit equals ac_back in settings.ini even when gamepad disabled, per BindsWriter. Opens engine quit path. |
| Select and system Back | Android quit dialog, not engine key | Handled in GameActivity dispatchKeyEvent and back callback, never forwarded to SDL. |

Multi-touch rules, hit areas, anti-misclick spacing, customization.

Finger store starts at length 4 and doubles as needed in hedgewars/uTouch.pas addFinger. Finger IDs pack left on delete. moveCursor true means empty-area drag pans the world. buttonsDown counts pressed widgets. Aiming crosshair short-circuits motion handling. Two-finger pinch only runs when buttonsDown is 0 and pointerCount is 2. Click versus long press uses clickTime 200 and movement threshold 30. Hit testing uses isOnRect with active rect plus padding through SetActiveWithPadding and isOnWidget show flag, with crosshair and hog tested at half RectSize 48. Fire button gap of 0.3 arrow widths is the deliberate anti-misclick spacing. Pause uses 300 ms debounce. Targeting is disabled for ExtDriven teams and replays. Customization has two channels: in-game UI scale through Settings uiScale auto plus 1.0 plus 1.5 plus 2.0 plus 2.5 in android/app/src/main/java/org/hedgewars/android/ui/settings/SettingsScreen.kt backed by android/app/src/main/java/org/hedgewars/android/data/UserPrefs.kt, which changes render buffer size in android/app/src/main/java/org/hedgewars/android/game/GameLauncher.kt and therefore physical button size; gamepad mapping is enabled or disabled through Settings gamepad toggle in the same files, with full remapping only by editing the generated settings.ini Binds section because on-device rebinding UI is absent.

## 5 Requirements

Hard requirements taken from build files. Commands that install them are in Section 7.

| Requirement | Version or value | Source |
|---|---|---|
| JDK | 17, sourceCompatibility 17 with targetCompatibility 17 with jvmTarget 17 | android/app/build.gradle.kts compileOptions and kotlinOptions |
| Gradle | 8.14.3 distribution | android/gradle/wrapper/gradle-wrapper.properties distributionUrl |
| Android Gradle Plugin | 8.11.1 | android/gradle/libs.versions.toml agp |
| Kotlin | 2.1.21 with compose and serialization plugins | android/gradle/libs.versions.toml kotlin |
| Android SDK platform | android-36 | android/scripts/bootstrap-toolchain.sh ANDROID_PLATFORM and android/app/build.gradle.kts compileSdk 36 |
| Android build-tools | 36.0.0 | android/scripts/bootstrap-toolchain.sh ANDROID_BUILD_TOOLS |
| Android NDK | r28c package ndk;28.2.13676358 | android/scripts/bootstrap-toolchain.sh ANDROID_NDK_PKG |
| Android CMake | 3.22.1 SDK package, root CMake minimum 3.22 | android/scripts/bootstrap-toolchain.sh ANDROID_CMAKE_PKG and CMakeLists.txt plus android/app/src/main/cpp/CMakeLists.txt |
| Ninja | Any recent release, Termux provides 1.13.2 at time of audit | android/scripts/build-native-deps.sh -G Ninja, device has ninja installed |
| Free Pascal host | 3.2.2, apt or SourceForge fallback | android/scripts/bootstrap-toolchain.sh FPC_VER plus stage_fpc_host |
| Free Pascal cross | 3.2.2 built from source for aarch64-android plus arm-android plus x86_64-android | android/scripts/bootstrap-toolchain.sh stage_fpc_cross with CPU_TARGET cpu and OS_TARGET android |
| ARM assembler for 32-bit ARM | binutils-arm-linux-gnueabi preferred, clang integrated assembler fallback | android/scripts/bootstrap-toolchain.sh write_wrappers comment on pre-UAL divided syntax |
| Rust toolchain | Required because build-native-deps builds lib-hwengine-future with cargo for all 3 ABIs; rustc present on build host with rustup targets aarch64-linux-android plus armv7-linux-androideabi plus x86_64-linux-android | android/scripts/build-native-deps.sh build_rust_future |
| Lua and Rust use for Android | Lua 5.1 is used through misc/liblua compiled into liblua.so. Rust lib-hwengine-future is used through rust/lib-hwengine-future Cargo package. No other rust crate is built for Android; hedgewars-network-protocol and server crates are present but unused by the Android pipeline | android/app/src/main/cpp/CMakeLists.txt LUA_DIR plus android/scripts/build-native-deps.sh build_rust_future |
| Python | Python 3 for host harnesses | android/scripts/host-e2e-harness.py and android/scripts/host-replay-harness.py plus android/README.md host requirement |
| Host tools | curl plus git plus make plus cc plus unzip plus rsync | android/scripts/bootstrap-toolchain.sh fetch with curl, android/scripts/prepare-data.sh rsync, android/README.md host list |
| Disk and RAM guidance | Toolchain about 6 GB with about 10 minutes bootstrap, native deps about 15 minutes, staged Data 218 MB with APK about 260 MB, Gradle JVM -Xmx3g | android/README.md build section and android/gradle.properties org.gradle.jvmargs |
| Runtime minSdk | 21, Android 5.0 | android/app/build.gradle.kts minSdk and android/scripts/bootstrap-toolchain.sh ANDROID_API |
| Runtime target and compile | 36 | android/app/build.gradle.kts targetSdk and compileSdk |
| Runtime ABIs | arm64-v8a with armeabi-v7a with x86_64, no x86 32-bit, no mips | android/scripts/build-native-deps.sh ABIS and android/engine/build-engine.sh ABIS |
| OpenGL ES | GLES 1.1 fixed-function API through gl4es translating to ES 2.0 context, ES 2.0 requested explicitly | hedgewars/uRender.pas MOBILE comment plus hedgewars/uStore.pas gl4es comment plus android/third_party/gl4es-shared/CMakeLists.txt DEFAULT_ES=2 |
| Display | Landscape sensorLandscape, hardwareAccelerated true | android/app/src/main/AndroidManifest.xml GameActivity attributes |

This repository contains no ndkVersion pin and no abiFilters and no externalNativeBuild block and no productFlavors in android/app/build.gradle.kts. NDK selection is by latest directory under TOOLCHAIN_DIR sdk ndk in android/engine/build-engine.sh and android/scripts/bootstrap-toolchain.sh. ABI selection is by script arguments defaulting to all three ABIs. Signing config names are release when android/keystore.properties exists else debug fallback in android/app/build.gradle.kts.

## 6 Build guide: Android Studio

All steps derived from android/README.md and android/app/build.gradle.kts and android/scripts files, not executed on this device during the audit. Where a menu path is an IDE function rather than a repo file, it is labeled as IDE behavior.

1. Clone with the full history. Run git clone https://github.com/Tokasur/hw_android.git then open the android directory as the Gradle root because android/settings.gradle.kts defines the build with include app and data. Derived from android/settings.gradle.kts and android/README.md, not executed.
2. Run the toolchain and native pipeline once from a Linux or macOS terminal before opening Studio, because Studio alone does not provide FPC cross compilers or the SDL2 family. Run ./scripts/bootstrap-toolchain.sh all then ./scripts/fetch-sdl.sh then ./scripts/build-native-deps.sh then ./engine/build-engine.sh then ./scripts/prepare-data.sh with working directory android. Verified sequence in android/README.md building section.
3. Set local.properties in the android directory with sdk.dir pointing at the SDK under TOOLCHAIN_DIR. Sample content uses an absolute path to the SDK directory created by bootstrap. This file is absent from the repository and is read by the Android Gradle Plugin through standard SDK discovery. Derived from standard AGP behavior plus android/scripts/bootstrap-toolchain.sh TOOLCHAIN_DIR layout, not executed.
4. Open Android Studio and choose Open with path to the android directory. Wait for sync using Gradle 8.14.3 with AGP 8.11.1 and Kotlin 2.1.21 per android/gradle/wrapper/gradle-wrapper.properties and android/gradle/libs.versions.toml. Install SDK Platform android-36 and Build-Tools 36.0.0 and NDK r28c and CMake 3.22.1 when the SDK Manager prompts, matching android/scripts/bootstrap-toolchain.sh pins.
5. Select build variant release in Build with Variants. The only signing configs are release when android/keystore.properties exists else debug fallback per android/app/build.gradle.kts. Without android/keystore.properties the release APK signs with the debug key and remains installable for sideloading per the same file comment.
6. Run the app on a device with ABI arm64-v8a or armeabi-v7a or x86_64. minSdk is 21 per android/app/build.gradle.kts. GameActivity forces sensorLandscape per android/app/src/main/AndroidManifest.xml.
7. Produce a signed release APK with ./gradlew :app:assembleRelease -PdataInApp from the android directory. Output is app/build/outputs/apk/release/app-release.apk per android/README.md. The -PdataInApp flag bakes android/data/src/main/assets Data into the app per android/app/build.gradle.kts. Derived from android/README.md, not executed.
8. Produce a Play bundle with ./gradlew :app:bundleRelease from the android directory. Output is app/build/outputs/bundle/release/app-release.aab per android/README.md. This path uses the :data install-time asset pack hw_data per android/data/build.gradle.kts. Derived from android/README.md, not executed.
9. Run unit tests with ./gradlew :app:testDebugUnitTest from the android directory per android/README.md. Tests cover IPC framing and config serialization and schemes and weapon sets and binds and packs and stats and demos per android/app/src/test listing. Derived from android/README.md, not executed.
10. Create android/keystore.properties for real releases with storeFile plus storePassword plus keyAlias plus keyPassword keys per android/app/build.gradle.kts, then generate the keystore once with keytool -genkeypair -v -keystore release.keystore -alias hedgewars -keyalg RSA -keysize 4096 -validity 10000 per android/README.md. Derived from android/README.md, not executed.

Troubleshooting table derived from build files and workflow.

| Failure | Cause in this repository | Fix using repository paths |
|---|---|---|
| NDK toolchain file not found | TOOLCHAIN_DIR sdk ndk directory missing because bootstrap sdk stage never ran | Run ./scripts/bootstrap-toolchain.sh sdk with working directory android per android/scripts/bootstrap-toolchain.sh, then retry |
| native deps missing for ABI | android/native ABI lib directory absent because build-native-deps never ran | Run ./scripts/build-native-deps.sh with working directory android per android/scripts/build-native-deps.sh |
| Pass -DHW_DEPS_PREFIX error from CMake | Configuring android/app/src/main/cpp directly without per-ABI prefix | Always configure through android/scripts/build-native-deps.sh hw_support target which passes -DHW_DEPS_PREFIX and -DHW_REPO_ROOT |
| FPC compile failed for ABI or host fpc is not 3.2.2 | Host FPC missing or wrong version, or cross compilers missing | Run ./scripts/bootstrap-toolchain.sh fpc-host then ./scripts/bootstrap-toolchain.sh fpc-cross then ./scripts/bootstrap-toolchain.sh smoke per android/scripts/bootstrap-toolchain.sh |
| RunEngine not exported or 16 KB alignment missing | Link used wrong linker or skipped ld.lld wrapper | Rebuild through android/engine/build-engine.sh which uses ppas.sh plus ld.lld wrapper with -z max-page-size=16384 and verifies with llvm-nm and llvm-readelf |
| Game data assets not found | android/data/src/main/assets Data never staged | Run ./scripts/prepare-data.sh with working directory android per android/scripts/prepare-data.sh, which copies share/hedgewars/Data and Buttons sprites and writes Data.manifest |
| R8 minified release shows no game window | Minification breaks SDL launch gating on mHasFocus | Keep isMinifyEnabled false and isShrinkResources false per android/app/build.gradle.kts comment |
| Desktop CI passes but Android still fails | .github/workflows/ci.yml builds desktop Qt and engine only with no Android job | Follow android/README.md six-step pipeline instead of the GitHub workflow for Android artifacts |

## 7 Build guide: CLI and Termux

Linux and macOS CLI full sequence. All commands derived from android/README.md and android/scripts files, not executed during this audit except where the audit ran read-only inspections.

```bash
cd android

# 1. SDK plus NDK r28c plus FPC 3.2.2 plus cross compilers plus smoke test
# Time about 10 minutes, disk about 6 GB per android/README.md
./scripts/bootstrap-toolchain.sh all

# 2. SDL2 family sources into android/third_party, refreshes SDL Java glue
./scripts/fetch-sdl.sh

# 3. SDL2 stack plus Lua plus PhysicsFS plus physlayer plus Rust helper, all 3 ABIs
# Time about 15 minutes per android/README.md
./scripts/build-native-deps.sh

# 4. Pascal engine to libhwengine.so, all 3 ABIs, staged into jniLibs
./engine/build-engine.sh

# 5. Stage 218 MB game data plus touch button sprites plus Data.manifest
./scripts/prepare-data.sh

# 6a. Self-contained sideload APK
./gradlew :app:assembleRelease -PdataInApp
# Output app/build/outputs/apk/release/app-release.apk about 260 MB

# 6b. Play bundle with install-time asset pack
./gradlew :app:bundleRelease
# Output app/build/outputs/bundle/release/app-release.aab

# Unit tests
./gradlew :app:testDebugUnitTest
```

TOOLCHAIN_DIR relocation uses TOOLCHAIN_DIR as environment prefix, default HOME slash hedgewars-android-toolchain per android/scripts/bootstrap-toolchain.sh and android/engine/build-engine.sh and android/scripts/build-native-deps.sh. Sample override form is TOOLCHAIN_DIR equals HOME slash hedgewars-android-toolchain ./scripts/bootstrap-toolchain.sh all. If the wrapper cannot download Gradle, use a locally installed Gradle 8.14 or newer per android/README.md. Build a single ABI with ./engine/build-engine.sh arm64-v8a or ./scripts/build-native-deps.sh arm64-v8a because both scripts accept ABI arguments per their header comments.

Protocol end-to-end test without a device builds the desktop engine from the same sources and drives it with the same IPC implementation for headless map preview. Run ./scripts/host-e2e-test.sh with working directory android per android/README.md. Expected line is OK alpha slash desktop preview received per the same file. Replay determinism loop uses ./scripts/host-replay-loop.sh with android/scripts/host-replay-harness.py, compiling a desktop FPC engine without Qt or CMake and recording then replaying a bot game, documented in android/docs/online-readiness.md.

Termux on-device aarch64 build. Honest capability statement follows.

Installable with pkg on Termux at audit time: cmake 4.4.4 with ninja 1.13.2 with python 3.14.6 with nodejs 26.4.0 with rust 1.99.0 with android-tools 37.0.0p1, verified through apt list installed. Not installable as Termux packages: FPC cross compiler with command -v fpc failing, JDK with command -v java failing, Gradle distribution with no gradle package matched. Google NDK layout referenced by android/scripts/bootstrap-toolchain.sh and android/engine/build-engine.sh uses toolchains llvm prebuilt linux-x86_64, which is an x86_64-only host path and has no aarch64 Linux host toolchain in the pinned NDK r28c flow. The Rust Android std for aarch64-linux-android is present as a Termux package, but the Pascal-to-Rust engine link still needs the NDK clang target triple plus FPC ppcrossa64 -Tandroid, which the Termux environment does not provide through pkg.

Memory guidance for a 4 GB device: Gradle requests -Xmx3g in android/gradle.properties, which exceeds comfortable headroom alongside the OS and desktop. Close apps first, run one heavy step at a time, prefer single-ABI builds with ./scripts/build-native-deps.sh arm64-v8a and ./engine/build-engine.sh arm64-v8a, keep swap enabled, and expect thermal throttling on sustained all-core load. The CI workflow is desktop-only and therefore cannot serve as an on-device recipe.

Reproducibility from CI. The file .github/workflows/ci.yml is the reliable desktop recipe and is not the Android recipe. It checks out with actions/checkout v4, installs Qt on Linux with jurplel install-qt-action v4, installs desktop dependencies including fp-compiler and libsdl2 family and liblua5.1-dev and libphysfs-dev on Ubuntu 24.04 or qt6 plus fpc plus SDL DMGs on macOS Intel, configures with mkdir build then cmake plus BUILD_ARGS where BUILD_ARGS is -DNOSERVER=1 -DGL2=1 -DNOPNG=1 or -DNOSERVER=1 -DLUA_SYSTEM=0, builds with make VERBOSE=1, tests Linux with make test_verbose, installs on macOS with make install. The Android reproducible path is instead the six-step android/README.md pipeline above. Where the two disagree on versions, the Android scripts win for Android artifacts: NDK r28c with FPC 3.2.2 with SDL2 2.30.11 family with proto 60.

## 8 Installation and sideloading

Download from https://github.com/Tokasur/hw_android/releases/latest. Each release carries one uploaded APK plus the two automatic source archives that the GitHub UI counts toward Assets 3, verified through the GitHub releases API at audit time. Release 0.2.9 published Jul 30 13:15 ships hedgewars-v0.2.9.apk at 254602046 bytes with sha256 576a1a4299795045e006838c44ebab391e254a0d2b8a1cbec4a24c0f1e4fc39b and 25 downloads at that time. Release 0.2.8 published Jul 28 15:16 ships hedgewars-v0.2.8.apk at 254326199 bytes with sha256 f6cb625c5628e8b2ae4aee2e862aa1ab4c00d82e9dcaa3aab010c29b46208809. Verify the file you downloaded by matching name with size and checksum before installing. The repository versionName is already 0.3.0 in android/app/build.gradle.kts on HEAD, ahead of the latest published release tag.

Sideload commands derived from android/README.md and standard platform tooling, not executed against a device here.

```bash
adb install app/build/outputs/apk/release/app-release.apk
```

Use adb install -r for reinstall while keeping data, which preserves filesDir teams and settings. Use adb install-multiple only for split APKs; the self-contained -PdataInApp APK is a single file and does not need it. The Play path uses an AAB with install-time pack hw_data per android/data/build.gradle.kts and is installed by Play, not by manual splits.

Permissions on Android 8 and later: enable Install unknown apps for the browser or file manager used for sideloading, then allow per-app install on Android 12 and later through system Settings with Apps with Special app access. Play Protect may warn on unknown developer signatures; choose Install anyway only for builds from this repository or its releases page. For upgrade versus clean install, prefer upgrade to keep files user teams and DLC; use clean install with uninstall only when wiping corrupted Data. Signature mismatch INSTALL_FAILED_UPDATE_INCOMPATIBLE means the installed package signature differs from the new APK, which happens when moving between debug-signed sideload builds and a differently signed release because android/app/build.gradle.kts falls back to debug signing without android/keystore.properties. Fix by uninstalling the old package with adb uninstall org.hedgewars.android, accepting that filesDir data is removed, then installing the new APK.

First-launch behavior: InstallGate in android/app/src/main/java/org/hedgewars/android/ui/install/InstallGate.kt gates navigation until DataInstaller reports Ready. DataInstaller in android/app/src/main/java/org/hedgewars/android/data/DataInstaller.kt streams Data.manifest entries from assets into filesDir Data with a 256 KB buffer, deletes the old Data tree first, ensures user dirs, then writes files .data-version with v plus versionCode. Duration scales with about 218 MB of small files plus Button sprites plus manifest sorting in android/scripts/prepare-data.sh. Storage footprint is about twice 218 MB transiently during copy, then Data plus user prefix persist. Permissions requested are only INTERNET in android/app/src/main/AndroidManifest.xml for loopback IPC and catalog HTTPS, with gamepad and touchscreen marked required false. No storage permission is needed because both prefixes live in internal storage.

## 9 Assets, data paths and DLC

All paths below use GamePaths in android/app/src/main/java/org/hedgewars/android/data/GamePaths.kt rooted at context filesDir, plus MatchRecords and EngineOutcome and DemosRepository files. Resolved examples use package org.hedgewars.android from android/app/build.gradle.kts applicationId on Android 11 and later scoped storage where filesDir maps to data slash data slash org.hedgewars.android slash files.

| Purpose | Real path expression | Resolved example |
|---|---|---|
| Game data read by engine --prefix | GamePaths dataDir equals files slash Data per android/app/src/main/java/org/hedgewars/android/data/GamePaths.kt | data slash data slash org.hedgewars.android slash files slash Data |
| User prefix passed as --user-prefix | GamePaths userDir equals files slash user per the same file | data slash data slash org.hedgewars.android slash files slash user |
| User content mounted by engine | GamePaths userDataDir equals files slash user slash Data, engine mounts user prefix at virtual Config per code comment in the same file | data slash data slash org.hedgewars.android slash files slash user slash Data |
| Teams | GamePaths userTeamsDir equals files slash user slash Teams per the same file | data slash data slash org.hedgewars.android slash files slash user slash Teams |
| Engine settings with Binds | GamePaths settingsIni equals files slash user slash settings.ini per the same file | data slash data slash org.hedgewars.android slash files slash user slash settings.ini |
| Data version marker | GamePaths versionMarker equals files slash .data-version per the same file | data slash data slash org.hedgewars.android slash files slash .data-version |
| DLC packs | PacksRepository dir equals userDataDir with extension hwp per android/app/src/main/java/org/hedgewars/android/data/PacksRepository.kt | data slash data slash org.hedgewars.android slash files slash user slash Data slash NAME.hwp |
| Replays | DemosRepository dir equals files slash user slash Demos with extension hwd plus sidecar locale per android/app/src/main/java/org/hedgewars/android/data/DemosRepository.kt | data slash data slash org.hedgewars.android slash files slash user slash Demos slash DATE.PROTO.hwd |
| Last match stats JSON | MatchRecords statsFile equals filesDir slash last_stats per android/app/src/main/java/org/hedgewars/android/engine/MatchRecords.kt | data slash data slash org.hedgewars.android slash files slash last_stats |
| Last match temp demo | MatchRecords demoTemp equals filesDir slash last_demo.hwd per the same file | data slash data slash org.hedgewars.android slash files slash last_demo.hwd |
| Engine outcome | EngineOutcome file equals filesDir slash last_outcome per android/app/src/main/java/org/hedgewars/android/engine/EngineOutcome.kt | data slash data slash org.hedgewars.android slash files slash last_outcome |
| Engine logs | GamePaths userDir slash Logs with game prefix log, needs -dDEBUGFILE per android/app/src/main/java/org/hedgewars/android/engine/EngineOutcome.kt readEngineLog | data slash data slash org.hedgewars.android slash files slash user slash Logs slash game0.log |
| Screenshots | Virtual path Screenshots slash hw_ plus mapdump variant through MakeScreenshot per hedgewars/hwengine.pas | Physical location follows PhysFS user prefix mapping under files slash user |
| Staged APK assets | android slash data slash src slash main slash assets slash Data plus Data.manifest per android/scripts/prepare-data.sh | Packaged into APK with -PdataInApp per android/app/build.gradle.kts or into hw_data pack per android/data/build.gradle.kts |

Backup and restore through adb, derived from the paths above, not executed against a device here.

```bash
adb shell run-as org.hedgewars.android ls files/user/Teams
adb exec-out run-as org.hedgewars.android tar -c files/user/Teams files/user/settings.ini > teams-backup.tar
```

Restore by pushing the same files back under files slash user, then launching once so BindsWriter regenerates settings.ini Binds if needed per android/app/src/main/java/org/hedgewars/android/data/BindsWriter.kt. DLC packs can be backed up as files slash user slash Data slash NAME.hwp through the same run-as path.

Add custom content by hand: copy named map directories into Data slash Maps, themes with theme.cfg into Data slash Themes, forts as NAME L.png with optional R variant into Data slash Forts, voices into the voices tree, scripts into Scripts slash Multiplayer with optional cfg pinning, all resolved through android/app/src/main/java/org/hedgewars/android/data/MissionsRepository.kt plus PackContentIndex. Prefer the in-app DLC screen backed by android/app/src/main/java/org/hedgewars/android/ui/dlc/DlcScreen.kt, which fetches https://hedgewars.org/content.html through android/app/src/main/java/org/hedgewars/android/data/CatalogRepository.kt, parses with android/app/src/main/java/org/hedgewars/android/data/DlcCatalog.kt, installs atomically through android/app/src/main/java/org/hedgewars/android/data/PacksRepository.kt, and activates on the next match because each match uses a fresh engine process per android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt. Remove packs from the same screen, which deletes the hwp file. Fort names are case sensitive through PhysFS, so L.PNG uppercase variants are dropped from offers per MissionsRepository comment.

Clear data safely: use system Settings with Storage with Clear Cache for transient menu theme cache under cacheDir menu_theme prefix per android/app/src/main/java/org/hedgewars/android/audio/MenuMusic.kt. Use Clear Storage only when prepared to re-run the 218 MB first-launch copy and lose teams and DLC, because DataInstaller wipes files slash Data on reinstall per android/app/src/main/java/org/hedgewars/android/data/DataInstaller.kt while PacksRepository data survives app updates.

## 10 Audio and SDL quirks

Only quirks verified in files or comments. Each entry gives symptom with cause with workaround or setting plus file reference.

Engine mixer init is narrow and fixed. Symptom of silence or disabled audio after failed init is handled by disabling sound and music flags. Cause is Mix_OpenAudio failure or missing OPUS support. Workaround is automatic fallback: hedgewars/uSound.pas InitSound opens 44100 Hz with MIX_DEFAULT_FORMAT with 2 channels with 1024 chunk, then tries MIX_INIT_OGG or MIX_INIT_OPUS and falls back to OGG alone. Setting is in-app Sound effects and Music toggles backed by android/app/src/main/java/org/hedgewars/android/data/UserPrefs.kt sound and music, passed as --nosound and --nomusic in android/app/src/main/java/org/hedgewars/android/engine/EngineArgs.kt.

Menu music never bleeds into matches and never crashes menus. Symptom would be overlapping music or menu failure on missing theme. Cause is separate audio stacks for menus and matches. Workaround is architectural: android/app/src/main/java/org/hedgewars/android/audio/MenuMusic.kt uses MediaPlayer with USAGE_MEDIA and CONTENT_TYPE_MUSIC and looping, owned by MainActivity onStart and onStop, while matches run in the game process with engine mixer audio. Every path is defensive with runCatching and generation guards so missing files only log.

Rotation restarts menu music from the top on naive implementations. Cause is Activity recreation on rotation stopping a process-global player. Workaround is the isChangingConfigurations guard in android/app/src/main/java/org/hedgewars/android/MainActivity.kt onStop, leaving the player running across rotation because onStart is idempotent.

Pause toggle double fires on fast phones. Symptom is pause then instant resume on one tap, observed on Pixel 9. Cause is one physical tap delivered as two touch events. Workaround is 300 ms debounce with lastPauseToggle and RealTicks in hedgewars/uTouch.pas onTouchDown pauseButton branch.

Lua string GC crash under fortified strchr. Symptom is crash at Lua script load with FORTIFY strchr prevented read past end of buffer. Cause is clang -O2 rewriting Lua byte scans into checked calls that cannot see past flexible TString arrays under _FORTIFY_SOURCE=2. Workaround is -U_FORTIFY_SOURCE with -D_FORTIFY_SOURCE=0 on the lua5.1 target in android/app/src/main/cpp/CMakeLists.txt with a full comment.

EGL deadlock at match start. Symptom is forever-frozen onCreate with engine never starting. Cause is gl4es constructor calling eglGetDisplay inside dlopen while the render thread holds the EGL lock and waits on the linker. Workaround is EGL pre-init with eglGetDisplay plus eglInitialize on the Activity thread before SDL dlopens libgl4es.so in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt onCreate.

Mid-init surface resize crash on fast phones. Symptom is random process crash at match start on Pixel 9 while the emulator stays harmless. Cause is decor flipping windowed to immersive under a running engine plus buffer size recomputed from evolving view size. Workaround is single-owner decor: applyImmersiveDecor once before surface creation in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt, swallow SDL command 2 in sendCommand, fix render buffer once through ScaledSDLSurface holder.setFixedSize, and remap touch with Matrix scale by buffer over view ratio.

Orientation collapse into a corner. Symptom is 2138x1080 landscape frame painted into a 1080x2138 portrait surface. Cause is SDL calling setRequestedOrientation FULL_USER on resizable windows, obeying rotation lock. Workaround is sensorLandscape pin in setOrientationBis plus SDL_IOS_ORIENTATIONS LandscapeLeft LandscapeRight plus sensorLandscape manifest value in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt and android/app/src/main/AndroidManifest.xml.

Accelerometer as phantom joystick. Symptom is sharp device tilt walking the hedgehog when no pad is attached. Cause is SDL exposing the accelerometer as joystick 0 where gamepad binds would attach. Workaround is SDL_ACCELEROMETER_AS_JOYSTICK 0 plus SDL_ANDROID_TRAP_BACK_BUTTON 1 in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt onCreate.

Back button behavior. Symptom on API 33 and later is silent activity destruction because back routes through OnBackInvokedCallback, never KEYCODE_BACK. Cause is platform back dispatch change. Workaround registers OnBackInvokedCallback on API 33 plus dispatchKeyEvent plus onBackPressed on older releases, all funneling to the same AlertDialog in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt.

Background suspend and GL rebuild. Symptom after backgrounding is IPC connection lost after fullscreen reinit plus zombie game process breaking the next match. Cause is tearing down and rebuilding GL through gl4es. Workaround ends the match on onStop with finishAndRemoveTask plus FLAG_KEEP_SCREEN_ON during play plus fresh process kill in GameLauncher ensureFreshGameProcess, verified in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt and android/app/src/main/java/org/hedgewars/android/game/GameLauncher.kt. Engine also auto-pauses on focus loss through onFocusStateChanged and suspends minimized games to gsSuspend on MOBILE per hedgewars/hwengine.pas.

GLES context specifics. The engine uses GLES 1.1 fixed-function entry points resolved through gl4es on top of an explicitly requested ES 2.0 context, verified in hedgewars/uRender.pas, hedgewars/uStore.pas, android/third_party/gl4es-shared/CMakeLists.txt, android/app/src/main/java/org/hedgewars/android/engine/EngineLoader.kt. Teardown order is uTextures before GLUnit with single SDL window and context destroy in hedgewars/hwengine.pas. Immersive mode uses SYSTEM_UI_FLAG FULLSCREEN with HIDE_NAVIGATION with IMMERSIVE_STICKY with LAYOUT flags in GameActivity applyImmersiveDecor. Cutout handling beyond pauseButton flush-corner artwork is absent; the code comment in hedgewars/uWorld.pas directs future fixes to real window insets rather than blind margins.

SDL hints in use are exactly three environment settings in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt: SDL_ANDROID_TRAP_BACK_BUTTON with value 1, SDL_ACCELEROMETER_AS_JOYSTICK with value 0, SDL_IOS_ORIENTATIONS with LandscapeLeft LandscapeRight. This repository contains no SDL_HINT prefixed Kotlin calls. Audio focus interruption handling beyond engine auto-pause on focus loss is absent and documented as such.

## 11 Configuration and settings reference

User-visible settings live in android/app/src/main/java/org/hedgewars/android/ui/settings/SettingsScreen.kt backed by android/app/src/main/java/org/hedgewars/android/data/UserPrefs.kt file hedgewars, with engine locale following through android/app/src/main/java/org/hedgewars/android/data/AppLocale.kt and engine flags through android/app/src/main/java/org/hedgewars/android/engine/EngineArgs.kt.

| Screen row | Preference key | Default | Engine or app effect |
|---|---|---|---|
| Sound effects | sound in android/app/src/main/java/org/hedgewars/android/data/UserPrefs.kt | true | Adds or omits --nosound in EngineArgs; uSound disables effects when false |
| Music | music in the same file | true | Adds or omits --nomusic in EngineArgs; engine music disabled when false |
| Menu music | menuMusic in the same file | true | Starts or stops MediaPlayer looping main_theme.ogg in MenuMusic; in-game music unaffected |
| Reduced quality faster | lowQuality in the same file | false | Adds or omits --low-quality in EngineArgs; engine reduces quality flags including low-res zoom branch in uVariables |
| Show FPS | showFps in the same file | false | Adds or omits --showfps in EngineArgs; engine overlays frames per second |
| Buttons and HUD size | uiScale in the same file | auto | auto computes scale from landscape height targeting 480 px in quarter steps in GameLauncher uiScale; fixed values 1.0 with 1.5 with 2.0 with 2.5 coerce into 1 through 3; larger factor renders fewer pixels through ScaledSDLSurface so touch buttons grow |
| Enable default gamepad mapping | gamepadBinds in the same file | true | Controls whether BindsWriter writes GAMEPAD_BINDS into settings.ini Binds in GameLauncher launch path |
| Language | language in the same file with AppLocale SUPPORTED empty plus en plus fr | empty meaning system | Applies through LocaleManager on API 33 plus attachBaseContext wrap below 33; engine locale file follows through EngineArgs localeFileFor checking Data slash Locale slash LANG.txt with en.txt fallback |

Engine config keys sent per match are defined in android/app/src/main/java/org/hedgewars/android/config/ConfigSerializer.kt: TL first, escript, emap, etheme, eseed, e$gmflags from Scheme gameFlags gf constants, e$damagepct, e$turntime milliseconds, e$inithealth, e$sd_turns, e$casefreq, e$minestime milliseconds, e$minesnum, e$minedudpct, e$explosives, e$airmines, e$sentries, e$healthprob, e$hcaseamount, e$waterrise, e$healthdec, e$ropepct, e$getawaytime, e$worldedge, e$template_filter, e$feature_size, e$mapgen, e$maze_size for maze and perlin, e$scriptparam, then per team eammloadt with eammprob with eammdelay with eammreinf plus conditional eammstore, eaddteam with MD5 of Player plus color plus name, egrave, efort, evoicepack, eflag, repeated eaddhh with difficulty plus health plus hog name plus ehat. Mission games use TL plus esetmissteam plus eaddmisshh plus etheme Nature plus eseed plus escript. Team fields map 1:1 to Team.kt name with hogNames with grave with fort with voicepack with flag with hat with difficulty.

SharedPreferences file name is hedgewars with MODE_PRIVATE per UserPrefs. Engine settings.ini path is files slash user slash settings.ini per GamePaths, fully owned by the frontend with header generated by the Hedgewars Android frontend, section Binds, always quit equals ac_back, then gamepad binds when enabled per BindsWriter.

## 12 Known issues and limitations

Replay desync fixed in 0.3.0, incompatible with older demos. Symptom in 0.2.9 was every replayed turn reporting Desync detected with long games diverging from the recording, stated in the 0.2.9 release Known Issues fetched from https://github.com/Tokasur/hw_android/releases/tag/0.2.9. Cause was not the recorder but fixed-point sign loss on every Pascal-to-Rust call: hwf_raw and hwf_with_sign in rust/lib-hwengine-future/src/fpnum.rs took Rust bool read as a full register while Free Pascal wrote a one-byte boolean, leaving upper bits dirty and returning garbage sign masks, documented with measured value 0xFFFFFFFF_FFA2E400 instead of 0 in android/docs/online-readiness.md. Affected call sites included GetRandomf and rndSign in hedgewars/uRandom.pas and AngleSin with AngleCos with WithSign in hedgewars/uFloat.pas and constants in hedgewars/uConsts.pas. Fix uses 32-bit flags with LongBool on Pascal and u32 on Rust compared against zero, so upper halves clear on write. Recording path is android/app/src/main/java/org/hedgewars/android/engine/DemoRecorder.kt capturing frontend bytes verbatim plus non-control engine frames with length prefix, invalidated by m frame or size cap. Playback path hands raw bytes as rawConfig in android/app/src/main/java/org/hedgewars/android/engine/GameConnection.kt and flips leading TL to TD once in android/app/src/main/java/org/hedgewars/android/data/DemosRepository.kt tlToTd. Locale is recorded in a sidecar locale file and forced on replay because Lua ScriptLocaleReader folds localization into CheckSum per DemosRepository recordedLocale. Current switch is android/app/src/main/java/org/hedgewars/android/Features.kt REPLAYS true.

End-of-match race. Symptom since 0.2.9 was a sometimes missing results screen. Cause is the engine sending q then halting without waiting for the frontend. Workaround banks stats and outcome at hasFinalRankings about three seconds before q in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt onStats, writes through MatchRecords before the demo because the demo is allowed to lose the race, documented in android/README.md limitations.

Online multiplayer absent. The engine pauses when backgrounded and the mobile build has no net-game loop, stated in hedgewars/hwengine.pas comment Mobile does not support online multiplayer yet. The Kotlin IPC layer isolates local TL handling so a future NetGameConnection can sit beside GameConnection per android/README.md, with protocol groundwork in project_files/frontlib/net/netconn.c and rust/hedgewars-network-protocol. Remaining network risks are catalogued in android/docs/online-readiness.md: missing pack content is fatal in MakeFortsMap, mixed locales desync scripted styles, NetGetNextCmd exact-tick queue can stall, chat frames can reorder, single lastTurnChecksum assumes one turn owner, pause needs shared decision, hiTicks epochs need ordered delivery, local epause and eforcequit must never go on the wire.

Compared with desktop, hand-drawn maps are not exposed because MapGen DRAWN 3 is commented out in android/app/src/main/java/org/hedgewars/android/config/GameConfig.kt, and the video recorder is not exposed per android/README.md. Renderer is GLES 1.1 through gl4es rather than desktop GL2, so Vulkan-only future devices would need the GL2 path ported per the same file. Data is copied out of the APK on first launch at about twice 218 MB transiently; zero-copy PHYSFS mount is the planned optimization per the same file.

Untested ABIs and platforms: x86 32-bit and mips are absent from android/scripts/build-native-deps.sh and android/engine/build-engine.sh. The 32-bit ARM path uses -CfVFPV2 in the engine build but -CfVFPV3 in the bootstrap cross description, so ARM FPU behavior should be validated per ABI before release. macOS and Windows hosts are not covered by the Android scripts, which assume apt conveniences or manual FPC 3.2.2 plus binutils-arm-linux-gnueabi per android/README.md.

## 13 Project layout

Annotated tree of directories that matter, verified through find and ls during the audit.

```text
android/
  README.md                          Kotlin port overview with six-step pipeline
  settings.gradle.kts                Root hedgewars-android with include app and data
  build.gradle.kts                   Root plugin aliases, apply false
  gradle.properties                  JVM -Xmx3g with parallel and caching and AndroidX flags
  gradle/libs.versions.toml          AGP 8.11.1 with Kotlin 2.1.21 with Compose BOM
  gradle/wrapper/                    Gradle 8.14.3 distribution plus jar plus scripts
  gradlew with gradlew.bat           Wrapper entry points
  app/build.gradle.kts               App module with SDK 36 plus version 0.3.0 plus signing
  app/proguard-rules.pro             Proguard rules referenced by release build type
  app/src/main/AndroidManifest.xml   INTERNET plus gamepad feature plus two activities
  app/src/main/cpp/                  C support libraries plus SDL_main shim
  app/src/main/java/org/hedgewars/android/  Frontend Kotlin: config plus data plus engine plus game plus ui
  app/src/main/java/org/hedgewars/hedgeroid/  PascalExports JNI declarations
  app/src/main/java/org/libsdl/app/  SDL Java glue with SDLActivity and SDLSurface
  app/src/main/res/                 Drawables plus font plus strings EN and FR plus themes
  app/src/test/                     19 unit tests plus content_snapshot.html fixture
  data/build.gradle.kts              Asset pack hw_data install-time
  data/src/main/assets/              Staged Data plus Data.manifest after prepare-data
  docs/online-readiness.md           Determinism audit plus network risk list
  engine/build-engine.sh             FPC cross build for 3 ABIs into jniLibs
  scripts/bootstrap-toolchain.sh     SDK plus NDK r28c plus FPC 3.2.2 plus wrappers
  scripts/build-native-deps.sh       SDL2 family plus Lua plus physfs plus Rust plus gl4es
  scripts/fetch-sdl.sh               Pinned SDL2 2.30.11 family fetch plus glue refresh
  scripts/prepare-data.sh            Rsync Data plus Buttons plus manifest
  scripts/host-e2e-test.sh           Desktop IPC end-to-end test entry
  scripts/host-replay-loop.sh        Determinism record and replay loop entry
  third_party/gl4es-shared/          gl4es shared-library CMake wrapper
hedgewars/
  hwengine.pas                       Engine main with RunEngine plus lifecycle
  hwLibrary.pas                      Library exports plus Android JNI names
  ArgParsers.pas                     Command-line parsing including base64 prefixes
  options.inc                        MOBILE plus HWLIBRARY defines
  config.inc.in                      Version template filled by engine build script
  uTouch.pas                         Touch widgets plus gestures plus pinch zoom
  uWorld.pas                         Widget geometry plus rendering
  uInputHandler.pas                  Key binds with MOBILE-only channels
  uSound.pas                         SDL_mixer init 44100 stereo 1024
  uIO.pas                            Loopback IPC plus message framing
  uVariables.pas                     Globals including ZoomValue limits
share/hedgewars/Data/
  Forts plus Maps plus Themes plus Scripts slash Multiplayer plus Locale plus Music
rust/
  lib-hwengine-future/               cdylib hwengine_future used by Android build
  fpnum plus land2d plus landgen plus others  Engine math and land crates
  hedgewars-network-protocol/        Protocol crate present but unused by Android pipeline
doc/
  README.upstream.md                 Preserved upstream desktop README from root
  protocol.txt plus Release.txt plus SoundsMapping.txt  Historical docs
QTfrontend plus frontend-qt6 plus gameServer plus tests plus tools
  Desktop Qt frontends plus Haskell and Rust servers plus engine tests plus utilities
```

Engine patches stay minimal with rationale in git log under hedgewars per android/README.md.

## 14 Contributing and debugging

Branches and PR conventions as seen in this repository: work happens on topic branches with master as the push target for .github/workflows/ci.yml, history shows direct version commits such as 0.2.9 content plus Forts plus results in adadc6c74 and merge pull requests number 3 and 4 from Tokasur Claude port branches. Keep engine patches minimal under hedgewars with rationale in the commit message, following the existing git log style. Keep SDL Java glue refreshes reviewable because android/scripts/fetch-sdl.sh warns when copied glue differs from committed patches. Do not modify source with README-only tasks; the allowed files for documentation work are README.md and doc/README.upstream.md.

Log tags for adb logcat, taken from TAG constants in code.

```bash
adb logcat -s HWGameActivity:I HWGameConnection:I HWDataInstaller:I HWMenuMusic:I HWGameLauncher:I HWMain:I HWCrash:F
```

Meaning per file: HWGameActivity in android/app/src/main/java/org/hedgewars/android/game/GameActivity.kt for lifecycle plus IPC port plus ghost handling, HWGameConnection in android/app/src/main/java/org/hedgewars/android/engine/GameConnection.kt for config size plus replay bytes plus chat, HWDataInstaller in android/app/src/main/java/org/hedgewars/android/data/DataInstaller.kt for file counts, HWMenuMusic in android/app/src/main/java/org/hedgewars/android/audio/MenuMusic.kt for theme resolution, HWGameLauncher in android/app/src/main/java/org/hedgewars/android/game/GameLauncher.kt for render size plus stale process kills, HWMain in android/app/src/main/cpp/hedgewars_main.c for dlopen plus RunEngine start and finish, HWCrash only when HW_CRASH_BACKTRACE is defined in the same C file for SIGSEGV backtraces with module plus offset for addr2line.

Native debugging: attach lldb through Android Studio with Run with Debug with Native debugging on the game process, using unstripped objects under android slash engine slash build slash ABI per android/engine/build-engine.sh out directories and symbols in android slash native slash ABI. Use ndk-stack to symbolize tombstones with the same per-ABI libhwengine.so staged into android/app/src/main/jniLibs per android/engine/build-engine.sh. Enable the crash backtrace variant by configuring the hw_support build with -DHW_CRASH_BACKTRACE, which defines HW_CRASH_BACKTRACE in android/app/src/main/cpp/CMakeLists.txt and installs the _Unwind_Backtrace handler in android/app/src/main/cpp/hedgewars_main.c. Read the engine log through EngineOutcome readEngineLog, which returns the last 20000 characters of the newest game log under user slash Logs; that path only exists with -dDEBUGFILE builds because the engine build passes -dDEBUGFILE in android/engine/build-engine.sh.

Engine tests that apply to Android: JVM unit tests run with ./gradlew :app:testDebugUnitTest from directory android per android/README.md, covering EngineProtocol framing with MAX_MESSAGE 255, ConfigSerializer command order, SchemeRanges, WeaponSet SIZE 60, BindsWriter output, DemosRepository TL to TD flip, DlcCatalogParser against content_snapshot.html, PackContentIndex, StatsParser. Host protocol tests run without a device through ./scripts/host-e2e-test.sh for IPC preview and ./scripts/host-replay-loop.sh with record plus replay plus file plus cycle modes for determinism, both backed by Python harnesses in android/scripts. Desktop make test_verbose in .github/workflows/ci.yml does not cover the Android pipeline.

Bug reports should state HEAD hash with date, versionName 0.3.0 with versionCode 300 from android/app/build.gradle.kts, ABI from arm64-v8a with armeabi-v7a with x86_64, device with Android version, install source among sideload APK with -PdataInApp or Play AAB with hw_data, language setting among system with en with fr, uiScale value, gamepad toggle, plus attached HWGameActivity with HWGameConnection logcat slice and user slash Logs game log tail and last_outcome content from filesDir.

## 15 License and credits

Source license is GNU General Public License version 2, verified at COPYING head in COPYING. The upstream desktop README preserved at doc/README.upstream.md states the same split: source under GPL v2 with images and sounds under GNU Free Documentation License version 1.2. The in-app About screen states the same GPL v2 inclusion of engine with SDL2 with Lua with PhysicsFS in android/app/src/main/res/values/strings.xml about_gpl with French equivalent in android/app/src/main/res/values-fr/strings.xml.

Fonts are covered separately in Fonts_LICENSE.txt with Bitstream Vera terms plus Google Droid Apache terms plus bundled dejavusans_bold.ttf in android/app/src/main/res/font. Full contributor history lives in CREDITS with per-hat and per-artist attributions. Upstream is hedgewars/hw at https://hedgewars.org with source mirrors at https://github.com/hedgewars/hw, credited in doc/README.upstream.md contact section. This fork authorship is Tokasur/hw_android described as AI Android port of Hedgewars on its GitHub releases pages, with this checkout remote upstream https://github.com/Tokasur/hw_android.git and origin https://github.com/Mark44928/hw_android.git.

Third-party libraries actually bundled through android/scripts/build-native-deps.sh and android/app/src/main/cpp/CMakeLists.txt.

| Library | Version and origin | License note location |
|---|---|---|
| SDL2 | 2.30.11 tarball from libsdl.org per android/scripts/fetch-sdl.sh | See Licenses screen referenced in fetch-sdl glue comment plus upstream SDL zlib terms carried with third_party sources |
| SDL2_image | 2.8.8 tarball per the same script, STB backend without AVIF with JXL with TIF with WEBP per android/scripts/build-native-deps.sh | Same as above |
| SDL2_mixer | 2.8.1 git tag release-2.8.1 with submodules per the same script, VORBIS STB with OPUS on without FLAC with MOD with MIDI with WAVPACK with MP3 per android/scripts/build-native-deps.sh | Same as above |
| SDL2_ttf | 2.24.0 git tag release-2.24.0 with submodules per the same script, vendored without HARFBUZZ per android/scripts/build-native-deps.sh | Same as above |
| SDL2_net | 2.2.0 tarball per the same script | Same as above |
| Lua 5.1 | misc/liblua compiled as lua5.1 with output lua per android/app/src/main/cpp/CMakeLists.txt | Lua MIT terms live with misc/liblua sources |
| PhysicsFS 2.1 | misc/libphysfs compiled as physfs per the same CMake file | PhysicsFS zlib terms live with misc/libphysfs sources |
| physlayer | misc/libphyslayer with physfscompat plus physfsrwops plus physfslualoader plus hwpacksmounter per the same CMake file | Hedgewars GPL v2 as part of this repository |
| gl4es | Checkout built through android/third_party/gl4es-shared/CMakeLists.txt with NOX11 with ANDROID | gl4es MIT terms live with the gl4es checkout |
| Rust hwengine_future | rust/lib-hwengine-future version 0.1.0 cdylib per rust/lib-hwengine-future/Cargo.toml | Rust crate terms live with rust sources |
| DejaVu Sans Bold | android/app/src/main/res/font/dejavusans_bold.ttf | Fonts_LICENSE.txt |

No other attribution file is required for the Android build beyond COPYING with Fonts_LICENSE.txt with CREDITS with the in-app About and licenses screen in android/app/src/main/java/org/hedgewars/android/ui/about/AboutScreen.kt.

## Notes on web versus repository discrepancies

GitHub releases page at https://github.com/Tokasur/hw_android/releases lists 0.2.9 as Latest published Jul 30 with 0.2.8 published Jul 28, while this checkout HEAD already carries versionName 0.3.0 in android/app/build.gradle.kts with commits for reliable replays and replay camera. The release UI reports Assets 3 for 0.2.9, but asset file names failed to load in both parallel-search fetch and firecrawl scrape at audit time, so names with ABIs with checksums are documented as absent rather than invented.

Hedgewars wiki BuildingHedgewars at https://hedgewars.org/kb/BuildingHedgewars correctly links Android with iOS with Desktop, but the historical BuildingForAndroid page still references NDK r5b with JDK 6 with Ant with Qt4. The repository wins: NDK r28c with platform android-36 with build-tools 36.0.0 with FPC 3.2.2 with CMake 3.22.1 per android/scripts/bootstrap-toolchain.sh.

Free Pascal wiki Android page states cross compilers via ppcrossarm -Tandroid with NDK binutils, which matches the repository pipeline in android/engine/build-engine.sh using ppcrossa64 with ppcrossarm with ppcrossx64 plus -Tandroid plus -Cn plus ld.lld wrappers. SDL upstream README-android states SDK 35 or later with NDK r28c or later with min API 21, which matches this repository use of compileSdk 36 with NDK r28c with minSdk 21.

Local git state differs from the public page in one mechanical way: git tag and git show-ref report no local tags in this checkout, while ls-remote against upstream reports 0.2.8 with 0.2.9 with release. Fetch tags to reconcile.

Scratch ledger for this README lives at the Termux-safe path data slash data slash com.termux slash files slash usr slash tmp slash hw_facts.md instead of slash tmp slash hw_facts.md because Termux forbids FHS tmp paths.
