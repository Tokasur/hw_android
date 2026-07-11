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
        // gl4es (fixed-function GL ES 1.1 -> ES 2.0). Loaded before the engine
        // so its symbols are in the app linker namespace when the engine
        // dlopens "libgl4es.so" (see gles11.pp).
        "gl4es",
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
