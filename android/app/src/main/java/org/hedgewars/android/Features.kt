package org.hedgewars.android

/** Switches for work that is finished but not fit to ship yet. */
object Features {

    /**
     * Replay recording and playback (engine demos, *.hwd).
     *
     * On since 0.3.0. It used to desynchronize at every replayed turn, which
     * turned out not to be a replay problem at all: the engine lost the sign
     * of its fixed-point numbers whenever Pascal called into Rust, so no two
     * runs of the same input agreed (see android/docs/online-readiness.md).
     */
    const val REPLAYS = true
}
