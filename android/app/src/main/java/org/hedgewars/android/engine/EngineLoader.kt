package org.hedgewars.android.engine

/**
 * Loads the native engine stack in dependency order.
 *
 * libmain.so (the SDL_main shim) additionally dlopens libhwengine.so at game
 * start, but loading everything eagerly here surfaces packaging problems as
 * immediate, readable UnsatisfiedLinkErrors instead of a mid-game dlopen
 * failure.
 */
object EngineLoader {
    val libraries = arrayOf(
        "SDL2",
        "SDL2_image",
        "SDL2_mixer",
        "SDL2_ttf",
        "SDL2_net",
        "lua",
        "physfs",
        "physlayer",
        "hwengine_future",
        "hwengine",
    )

    @Volatile
    private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        for (lib in libraries) {
            System.loadLibrary(lib)
        }
        loaded = true
    }
}
