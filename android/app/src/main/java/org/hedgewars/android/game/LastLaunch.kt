package org.hedgewars.android.game

import android.content.Intent
import android.os.SystemClock

/**
 * Last match launch, kept in the menu process so a match that hard-crashes
 * right after starting can be relaunched automatically (the engine start
 * has a rare timing-dependent native crash on fast devices; a retry
 * succeeds). Only genuine crashes are retried — engine-reported errors
 * ("Some parameters not set", ...) would just fail again.
 */
object LastLaunch {
    /**
     * Auto-retry only crashes that happen right after launch: a launch
     * crash bounces back to the menu within seconds, so a small window is
     * enough. Anything later is a mid-game crash or the user coming back to
     * the app long after the fact — relaunching the old match then is
     * disorienting, never helpful (v0.2.5 used 3 min and users got thrown
     * back into matches they had already given up on).
     */
    private const val RETRY_WINDOW_MS = 45_000L
    private const val MAX_RETRIES = 2

    @Volatile private var intent: Intent? = null
    @Volatile private var launchedAt: Long = 0L
    @Volatile private var retries: Int = 0

    /** A fresh launch from a menu screen: new payload, retry budget reset. */
    fun record(launchIntent: Intent) {
        intent = launchIntent
        launchedAt = SystemClock.elapsedRealtime()
        retries = 0
    }

    /**
     * Consume one automatic retry of the last launch after a hard crash.
     * Null once the budget (2) is spent or the launch is too old.
     */
    fun takeRetry(): Intent? {
        val i = intent ?: return null
        if (retries >= MAX_RETRIES) return null
        if (SystemClock.elapsedRealtime() - launchedAt > RETRY_WINDOW_MS) return null
        retries++
        launchedAt = SystemClock.elapsedRealtime()
        return i
    }

    /**
     * Forget the last launch. Called whenever the menu settles — a clean
     * return from a match, or an error dialog already shown — so no stale
     * intent can ever fire a surprise relaunch later.
     */
    fun clear() {
        intent = null
        retries = 0
    }
}
