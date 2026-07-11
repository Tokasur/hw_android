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
#include <signal.h>
#include <unwind.h>
#include <stdint.h>
#include <ucontext.h>
#include <android/log.h>
#include "SDL_main.h"

#define TAG "HWMain"

/* ---- diagnostic SIGSEGV backtrace ------------------------------------------
 * The emulator's crash_dump times out under software emulation, so no tombstone
 * is written. Install a lightweight handler that unwinds and logs each frame's
 * module + offset immediately, so libhwengine.so offsets can be addr2line'd.
 * Compiled in only when HW_CRASH_BACKTRACE is defined.
 */
#ifdef HW_CRASH_BACKTRACE
struct bt_state { int count; };

static _Unwind_Reason_Code bt_trace(struct _Unwind_Context *ctx, void *arg)
{
    struct bt_state *st = (struct bt_state *) arg;
    uintptr_t pc = _Unwind_GetIP(ctx);
    if (pc) {
        Dl_info info;
        if (dladdr((void *) pc, &info) && info.dli_fname) {
            uintptr_t base = (uintptr_t) info.dli_fbase;
            __android_log_print(ANDROID_LOG_FATAL, "HWCrash",
                "#%02d pc %012zx  %s (offset %zx)",
                st->count, pc, info.dli_fname, pc - base);
        } else {
            __android_log_print(ANDROID_LOG_FATAL, "HWCrash", "#%02d pc %012zx  ?", st->count, pc);
        }
    }
    if (++st->count >= 32) return _URC_END_OF_STACK;
    return _URC_NO_REASON;
}

static void hw_log_pc(const char *what, uintptr_t pc)
{
    Dl_info info;
    if (pc && dladdr((void *) pc, &info) && info.dli_fname)
        __android_log_print(ANDROID_LOG_FATAL, "HWCrash", "%s pc %012zx  %s (offset %zx)",
            what, pc, info.dli_fname, pc - (uintptr_t) info.dli_fbase);
    else
        __android_log_print(ANDROID_LOG_FATAL, "HWCrash", "%s pc %012zx  ?", what, pc);
}

static void hw_segv_handler(int sig, siginfo_t *si, void *uc)
{
    __android_log_print(ANDROID_LOG_FATAL, "HWCrash",
        "signal %d at addr %p — backtrace:", sig, si ? si->si_addr : 0);
    /* The exact crashing instruction, from the saved register context. */
    if (uc) {
        ucontext_t *u = (ucontext_t *) uc;
#if defined(__x86_64__)
        hw_log_pc("CRASH", (uintptr_t) u->uc_mcontext.gregs[REG_RIP]);
#elif defined(__aarch64__)
        hw_log_pc("CRASH", (uintptr_t) u->uc_mcontext.pc);
#elif defined(__arm__)
        hw_log_pc("CRASH", (uintptr_t) u->uc_mcontext.arm_pc);
#endif
    }
    struct bt_state st = { 0 };
    _Unwind_Backtrace(bt_trace, &st);
    /* restore default and re-raise so the OS still handles it */
    signal(sig, SIG_DFL);
    raise(sig);
}

static void hw_install_crash_handler(void)
{
    struct sigaction sa = { 0 };
    sa.sa_sigaction = hw_segv_handler;
    sa.sa_flags = SA_SIGINFO;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGSEGV, &sa, NULL);
    sigaction(SIGABRT, &sa, NULL);
}
#endif

int SDL_main(int argc, char *argv[])
{
#ifdef HW_CRASH_BACKTRACE
    hw_install_crash_handler();
#endif
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
