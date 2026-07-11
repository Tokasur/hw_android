# Hedgewars Android — native support libraries.
#
# Builds, from sources bundled in this repository:
#   * liblua5.1.so    (misc/liblua      — Lua 5.1, engine scripting)
#   * libphysfs.so    (misc/libphysfs   — PhysicsFS 2.1, VFS for game data)
#   * libphyslayer.so (misc/libphyslayer— Hedgewars PhysFS/Lua/SDL glue)
#   * libmain.so      (hedgewars_main.c — SDL_main shim that dlopens the engine)
#
# The SDL2 family is built separately by android/scripts/build-native-deps.sh
# (their upstream CMake projects) and imported here from the per-ABI install
# prefix passed as -DHW_DEPS_PREFIX.
#
# libhwengine.so itself is produced by android/engine/build-engine.sh (FPC).

cmake_minimum_required(VERSION 3.22)
project(hedgewars_android C)

if(NOT DEFINED HW_REPO_ROOT)
    get_filename_component(HW_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.." ABSOLUTE)
endif()
if(NOT DEFINED HW_DEPS_PREFIX)
    message(FATAL_ERROR "Pass -DHW_DEPS_PREFIX=<per-abi install prefix with SDL2>")
endif()

list(APPEND CMAKE_PREFIX_PATH "${HW_DEPS_PREFIX}")
find_package(SDL2 REQUIRED CONFIG)

set(CMAKE_C_VISIBILITY_PRESET default)
add_compile_options(-O2 -fsigned-char)
# 16 KB page compatibility (Play requirement for targetSdk 35+)
add_link_options("-Wl,-z,max-page-size=16384")

# --- Lua 5.1 (module name lua5.1, like the historical port) ----------------
set(LUA_DIR "${HW_REPO_ROOT}/misc/liblua")
set(LUA_SRCS
    lapi.c lauxlib.c lbaselib.c lcode.c ldblib.c ldebug.c ldo.c ldump.c
    lfunc.c lgc.c linit.c liolib.c llex.c lmathlib.c lmem.c loadlib.c
    lobject.c lopcodes.c loslib.c lparser.c lstate.c lstring.c lstrlib.c
    ltable.c ltablib.c ltm.c lundump.c lvm.c lzio.c)
list(TRANSFORM LUA_SRCS PREPEND "${LUA_DIR}/")
add_library(lua5.1 SHARED ${LUA_SRCS})
# The engine's LuaPas.pas does {$linklib lua} -> DT_NEEDED liblua.so
set_target_properties(lua5.1 PROPERTIES OUTPUT_NAME "lua")
target_compile_definitions(lua5.1 PRIVATE ANDROID LUA_USE_POSIX LUA_USE_DLOPEN)
target_include_directories(lua5.1 PUBLIC "${LUA_DIR}")
target_link_libraries(lua5.1 PRIVATE dl)

# --- PhysicsFS 2.1 ----------------------------------------------------------
set(PHYSFS_DIR "${HW_REPO_ROOT}/misc/libphysfs")
set(PHYSFS_SRCS
    physfs.c physfs_byteorder.c physfs_unicode.c
    platform_posix.c platform_unix.c platform_macosx.c platform_windows.c
    archiver_dir.c archiver_unpacked.c archiver_grp.c archiver_hog.c
    archiver_lzma.c archiver_mvl.c archiver_qpak.c archiver_wad.c
    archiver_zip.c archiver_iso9660.c)
list(TRANSFORM PHYSFS_SRCS PREPEND "${PHYSFS_DIR}/")
add_library(physfs SHARED ${PHYSFS_SRCS})
target_compile_definitions(physfs PRIVATE
    PHYSFS_NO_CDROM_SUPPORT PHYSFS_SUPPORTS_ZIP)
target_include_directories(physfs PUBLIC "${PHYSFS_DIR}")
target_link_libraries(physfs PRIVATE z)

# --- physlayer (Hedgewars glue: PhysFS <-> SDL RWops / Lua loader / packs) --
set(PHYSLAYER_DIR "${HW_REPO_ROOT}/misc/libphyslayer")
add_library(physlayer SHARED
    "${PHYSLAYER_DIR}/physfscompat.c"
    "${PHYSLAYER_DIR}/physfsrwops.c"
    "${PHYSLAYER_DIR}/physfslualoader.c"
    "${PHYSLAYER_DIR}/hwpacksmounter.c")
target_include_directories(physlayer PUBLIC "${PHYSLAYER_DIR}")
target_link_libraries(physlayer PRIVATE SDL2::SDL2 lua5.1 physfs)

# --- SDL_main shim ----------------------------------------------------------
add_library(main SHARED hedgewars_main.c)
target_link_libraries(main PRIVATE SDL2::SDL2 dl log)

install(TARGETS lua5.1 physfs physlayer main LIBRARY DESTINATION lib)
