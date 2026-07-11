/*
 * Hedgewars Android launcher shim.
 *
 * SDLActivity loads libmain.so and calls SDL_main() with the arguments
 * provided by GameActivity.getArguments(). The Free Pascal engine lives in
 * libhwengine.so (built separately by android/engine/build-engine.sh); it is
 * dlopen'd here so that the app skeleton stays buildable and testable even
 * without the engine, and its exported RunEngine() drives the whole game.
 */
#include <dlfcn.h>
#include <android/log.h>
#include "SDL_main.h"

#define TAG "HWMain"

int SDL_main(int argc, char *argv[])
{
    void *engine = dlopen("libhwengine.so", RTLD_NOW | RTLD_GLOBAL);
    if (engine == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "dlopen(libhwengine.so): %s", dlerror());
        return 1;
    }

    int (*run_engine)(int, char **) = (int (*)(int, char **)) dlsym(engine, "RunEngine");
    if (run_engine == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "dlsym(RunEngine): %s", dlerror());
        return 1;
    }

    __android_log_print(ANDROID_LOG_INFO, TAG, "starting engine (argc=%d)", argc);
    int result = run_engine(argc, argv);
    __android_log_print(ANDROID_LOG_INFO, TAG, "engine finished: %d", result);
    return result;
}
