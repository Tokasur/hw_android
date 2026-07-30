package org.hedgewars.android.game

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Matrix
import android.opengl.EGL14
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import org.hedgewars.android.Features
import org.hedgewars.android.R
import org.hedgewars.android.config.StatsParser
import org.hedgewars.android.data.CampaignStore
import org.hedgewars.android.engine.DemoRecorder
import org.hedgewars.android.engine.EngineLoader
import org.hedgewars.android.engine.EngineOutcome
import org.hedgewars.android.engine.GameConnection
import org.hedgewars.android.engine.MatchRecords
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLSurface

/**
 * Hosts the Free Pascal engine through SDL.
 *
 * Runs in the separate ":game" process (see AndroidManifest) so every match
 * starts from a pristine engine state; the process is killed in onDestroy.
 *
 * The IPC server lives HERE, in the same process as the engine: the frontend
 * ships the ready-made config command stream through the Intent, this activity
 * opens the loopback server, injects its port into the engine command line and
 * feeds the config when the engine asks for it. Keeping the server in the game
 * process means the menu process being backgrounded/killed can never drop the
 * connection mid-match.
 */
class GameActivity : SDLActivity() {

    private var connection: GameConnection? = null
    private var quitDialog: AlertDialog? = null

    /** Collects the engine's end-of-match stat frames for the menu process. */
    private val statsParser = StatsParser()

    /** Set once the end-of-match stats have been written to disk. */
    private var statsBanked = false
    private var demoRecorder: DemoRecorder? = null
    private var replay = false
    private var localMatch = false

    /**
     * Set when the system re-created this activity after a process death
     * (crash): the task record survives a crash un-finished, and bringing
     * the app back to the front makes Android rebuild the activity with the
     * ORIGINAL match Intent — the dead match would silently restart. Such a
     * ghost is closed immediately and must not touch EngineOutcome (it
     * would overwrite the crash state the menu is about to report).
     */
    private var ghostRecreation = false

    override fun getLibraries(): Array<String> = EngineLoader.libraries + "main"

    override fun getArguments(): Array<String> {
        val base = intent.getStringArrayExtra(EXTRA_ENGINE_ARGS)?.toMutableList() ?: mutableListOf()
        // Replace the placeholder port with the one our server actually bound.
        val conn = connection ?: return base.toTypedArray()
        val i = base.indexOf("--port")
        if (i >= 0 && i + 1 < base.size) base[i + 1] = conn.port.toString()
        return base.toTypedArray()
    }

    // Hedgewars is a landscape game. SDL, seeing a resizable window with no
    // orientation hint, calls setRequestedOrientation(SCREEN_ORIENTATION_FULL_USER),
    // which obeys the device's rotation lock and can pin us to portrait — the
    // engine then paints its 2138x1080 landscape frame into a 1080x2138 portrait
    // surface, so the whole game collapses into a corner. Pin sensor-landscape
    // instead, ignoring whatever SDL computed from the (resizable) window.
    override fun setOrientationBis(w: Int, h: Int, resizable: Boolean, hint: String) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun createSDLSurface(context: Context): SDLSurface =
        ScaledSDLSurface(
            context,
            intent.getIntExtra(EXTRA_RENDER_W, 0),
            intent.getIntExtra(EXTRA_RENDER_H, 0),
        )

    /**
     * SDL surface with a reduced fixed-size buffer that Android upscales to
     * the full view. The engine's touch buttons and HUD are sized in raw
     * pixels, so rendering fewer pixels makes them physically bigger (and
     * cheaper to draw).
     *
     * The buffer size is decided ONCE, up front, from the real display size
     * (GameLauncher computes it; the engine gets the same numbers as --width/
     * --height). It is applied before the surface is first created and never
     * changed again: v0.2.1 recomputed it from the evolving view size (inset
     * layout first, immersive fullscreen a moment later), and that mid-flight
     * buffer swap raced the engine's EGL/gl4es init — fine on the slow
     * emulator, black screen on a real phone.
     */
    private class ScaledSDLSurface(context: Context, bufW: Int, bufH: Int) :
        SDLSurface(context) {

        init {
            if (bufW >= 2 && bufH >= 2) {
                Log.i(TAG, "fixed render buffer ${bufW}x$bufH")
                holder.setFixedSize(bufW, bufH)
            }
        }

        // Always fill the window. Without this the view can re-measure to the
        // fixed-size buffer's dimensions (observed: view collapsed to the
        // buffer size, the game shrinking to a quarter of the screen).
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(
                MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec),
            )
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            // SDLSurface.onTouch normalizes coordinates by the *buffer* size
            // (mWidth/mHeight); with a fixed-size buffer the incoming view
            // coordinates must be shrunk to buffer space first, or every
            // touch lands beyond the bottom-right of the game.
            val vw = width.toFloat()
            val vh = height.toFloat()
            if (vw > 0f && vh > 0f && mWidth > 2f && (mWidth != vw || mHeight != vh)) {
                val copy = MotionEvent.obtain(ev)
                copy.transform(Matrix().apply { setScale(mWidth / vw, mHeight / vh) })
                val handled = super.dispatchTouchEvent(copy)
                copy.recycle()
                return handled
            }
            return super.dispatchTouchEvent(ev)
        }
    }

    /**
     * The exact decor state SDL would apply for a fullscreen window
     * (SDLActivity's COMMAND_CHANGE_WINDOW_STYLE handler), applied by US,
     * once, BEFORE the surface exists. SDL's own style commands are swallowed
     * in [sendCommand]: SDLActivity.onCreate first forces a WINDOWED style
     * and the engine requests fullscreen only after it has already started,
     * so the decor flipped windowed->immersive underneath a running engine —
     * the layout change destroyed/recreated the game surface in the middle
     * of EGL/gl4es init. On the slow emulator init always lost that race
     * harmlessly; on a fast phone (Pixel 9) it randomly crashed the process
     * at match start. One owner, one decor state, zero mid-init resizes.
     */
    private fun applyImmersiveDecor() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
    }

    // COMMAND_CHANGE_WINDOW_STYLE == 2 (package-private in SDLActivity).
    // Both SDL window-style requests — the windowed one from
    // SDLActivity.onCreate and the fullscreen one the engine triggers after
    // startup — are no-ops: the decor is already in its final state.
    override fun sendCommand(command: Int, data: Any?): Boolean {
        if (command == 2) return true
        return super.sendCommand(command, data)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // IMMERSIVE_STICKY re-hides on its own; re-assert on focus regain as
        // we bypassed SDL's own rehide bookkeeping.
        if (hasFocus) applyImmersiveDecor()
    }

    // Back and gamepad Select open our quit dialog, and must never reach SDL:
    // in the engine they only toggle a confirm overlay whose "press Y" answer
    // no gamepad or touch screen can give. Depending on the Android release
    // back arrives here as a key event, through the callback below, or both —
    // [showQuitDialog] is idempotent, so either path is safe.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT
        ) {
            if (event.action == KeyEvent.ACTION_UP) showQuitDialog()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * System back. On API 33+ the framework routes back through
     * OnBackInvokedCallback and never sends a KEYCODE_BACK event nor calls
     * onBackPressed — without registering here, back silently destroyed the
     * activity mid-match (the menu then read the still-"running" outcome as a
     * crash and auto-relaunched a new game).
     */
    private val backCallback =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.window.OnBackInvokedCallback { showQuitDialog() }
        } else {
            null
        }

    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() {
        // Pre-33 path (and gesture back on older releases).
        showQuitDialog()
    }

    private fun showQuitDialog() {
        if (isFinishing || quitDialog?.isShowing == true) return
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.game_quit_title)
            .setPositiveButton(R.string.game_quit_yes) { _, _ -> quitMatch() }
            .setNegativeButton(R.string.game_quit_no) { _, _ -> resumeMatch() }
            .setOnCancelListener { resumeMatch() }
            .create()
        // The dialog window owns every key while shown (the running game sees
        // none of them): A confirms, B or Select cancels, back dismisses by
        // itself. Touch just taps the buttons.
        dialog.setOnKeyListener { d, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> {
                    d.dismiss()
                    quitMatch()
                    true
                }
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                    d.cancel()
                    true
                }
                else -> false
            }
        }
        quitDialog = dialog
        dialog.show()
    }

    /**
     * Undoes the pause the engine applies whenever it loses focus (uWorld.pas
     * onFocusStateChanged) — it never resumes on its own, so without this the
     * match stays frozen after the player answers "no". 'pause' is a toggle
     * and the engine is necessarily paused while the dialog is up.
     */
    private fun resumeMatch() {
        val conn = connection ?: return
        Thread({ conn.send("epause") }, "hw-resume").start()
    }

    private fun quitMatch() {
        // The desktop frontend's abort: one IPC command, the engine answers
        // 'Q' and the normal finished path returns to the menu. Socket I/O is
        // forbidden on the main thread; if the engine is already unreachable,
        // fall back to closing the activity (onStop marks the outcome ok).
        Thread({
            if (connection?.send("eforcequit") != true) {
                runOnUiThread { if (!isFinishing) finishAndRemoveTask() }
            }
        }, "hw-quit").start()
    }

    /**
     * Hands the finished match over to the menu process: its statistics, and
     * the recording the player may want to keep. Called on the IPC thread.
     */
    private fun publishRecords() {
        if (!localMatch) return
        val report = statsParser.build(fromReplay = replay)
        // A replay that stopped short of the real end only carries the
        // per-turn health lines, no ranking — showing that as "results" would
        // be misleading, so it just returns to the menu (as the desktop does).
        if (report != null && (!replay || report.rankings.isNotEmpty())) {
            MatchRecords.writeStats(this, report)
        }
        // Mark the outcome before spending time on the demo: the engine halts
        // this whole process on its own schedule shortly after 'q', and an
        // outcome still "running" at that moment makes the menu report a
        // crash and sweep the stats written just above. The demo is the one
        // artifact worth risking to the race.
        EngineOutcome.markFinished(this)
        demoRecorder?.snapshot()?.let { MatchRecords.writeDemo(this, it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // A saved state means the system is resurrecting a match whose
        // process died — a legitimate launch from GameLauncher always starts
        // fresh (null). Close the ghost before any engine/IPC setup; the
        // engine never starts (the activity finishes before SDL sees a
        // ready surface with focus).
        if (savedInstanceState != null) {
            ghostRecreation = true
            Log.w(TAG, "re-created after process death; closing instead of restarting the old match")
            super.onCreate(savedInstanceState)
            finishAndRemoveTask()
            return
        }

        // Pre-initialize EGL on this thread BEFORE SDL dlopens libgl4es.so.
        // gl4es's ELF constructor calls eglGetDisplay(); running that inside
        // dlopen can deadlock — the dlopen holds the linker lock while EGL's
        // first-time init wants to dlopen the vendor driver, and the render
        // thread doing its own EGL init holds the EGL lock while waiting on
        // the linker (classic AB-BA; observed as a forever-frozen onCreate,
        // the engine never starting and the game window never composing).
        // With EGL already initialized, the constructor takes the fast path
        // and never re-enters the linker.
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display != EGL14.EGL_NO_DISPLAY) {
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)
        }

        // Final fullscreen decor BEFORE the surface is created, so the first
        // surface is born at its final size (see applyImmersiveDecor).
        applyImmersiveDecor()
        mFullscreenModeActive = true

        // Deliver the back button to the engine as the "ac_back" key (bound
        // to the quit-confirmation) instead of finishing the activity.
        android.system.Os.setenv("SDL_ANDROID_TRAP_BACK_BUTTON", "1", true)
        // Without a physical pad SDL exposes the accelerometer as joystick 0;
        // the gamepad binds would attach to its axes and a sharp tilt of the
        // device could walk the hedgehog. Real pads are game controllers now,
        // the accelerometer never is — drop it entirely.
        android.system.Os.setenv("SDL_ACCELEROMETER_AS_JOYSTICK", "0", true)
        // Belt-and-suspenders: also hand SDL the orientation hint so its own
        // bookkeeping agrees with the forced request above (SDL_HINT_ORIENTATIONS
        // == "SDL_IOS_ORIENTATIONS", read from the environment by SDL_GetHint).
        android.system.Os.setenv("SDL_IOS_ORIENTATIONS", "LandscapeLeft LandscapeRight", true)

        val config = intent.getStringArrayExtra(EXTRA_CONFIG)?.toList() ?: emptyList()
        val campaignTeam = intent.getStringExtra(EXTRA_CAMPAIGN_TEAM)
        val varStore = campaignTeam?.let { team ->
            CampaignStore(this, team, intent.getStringExtra(EXTRA_CAMPAIGN_SCOPE) ?: "")
        }

        // Records of the PREVIOUS match are none of this one's business.
        MatchRecords.sweep(this)

        // Watching a replay: the recorded stream replaces the config.
        val replayFile = intent.getStringExtra(EXTRA_REPLAY_PATH)?.let { java.io.File(it) }
        replay = replayFile != null
        // Stats and recordings are for local matches (and their replays) only:
        // a mission's Lua script answers 'v?' queries that no replay can
        // reproduce, and its stats belong to the mission, not to a scoreboard.
        localMatch = campaignTeam == null
        val recorder = if (Features.REPLAYS && localMatch && !replay) DemoRecorder() else null
        demoRecorder = recorder

        EngineOutcome.markRunning(this)
        val conn = GameConnection(
            configCommands = { config },
            listener = object : GameConnection.Listener {
                override fun onEngineError(message: String) {
                    // A quit in progress tears the IPC down under the engine;
                    // its dying "IPC connection lost" is expected collateral
                    // of leaving, not a match failure to report.
                    if (isFinishing) {
                        Log.w(TAG, "engine error ignored during teardown: $message")
                        return
                    }
                    EngineOutcome.markError(this@GameActivity, message)
                    runOnUiThread { if (!isFinishing) finishAndRemoveTask() }
                }
                override fun onStats(kind: Char, text: String) {
                    statsParser.feed(kind, text)
                    // The final rankings arrive ~3 s before the engine's 'q' —
                    // and the engine never waits for us: it halts itself right
                    // after 'q', taking the whole process down (the well-known
                    // native crash on exit). Bank the outcome and the stats
                    // the moment the match is provably over, so that losing
                    // the post-'q' race only ever costs the demo file.
                    if (!statsBanked && statsParser.hasFinalRankings) {
                        statsBanked = true
                        statsParser.build(fromReplay = replay)?.let {
                            MatchRecords.writeStats(this@GameActivity, it)
                        }
                        EngineOutcome.markFinished(this@GameActivity)
                    }
                }
                /**
                 * The engine closed the IPC without a 'q'. Two legitimate ways
                 * here besides a crash: a replay running out of recorded input
                 * ("End of input, halting now"), and a played match whose
                 * process died in the end-of-match race before 'q' was read —
                 * in both cases the final rankings already arrived, seconds
                 * earlier. Treat those as the clean end they are; only an EOF
                 * with no end-of-match stats is a real crash.
                 */
                override fun onEngineGone() {
                    if (!replay && !statsParser.hasFinalRankings) return
                    publishRecords()
                    EngineOutcome.markFinished(this@GameActivity)
                    runOnUiThread { if (!isFinishing) finishAndRemoveTask() }
                }
                override fun onGameFinished(interrupted: Boolean) {
                    // A match played to the end has stats to show and a demo
                    // worth keeping; a user quit ('Q') has neither. Both files
                    // are written HERE, on the IPC thread, before the activity
                    // finishes — onDestroy kills this process immediately.
                    if (!interrupted) publishRecords()
                    EngineOutcome.markFinished(this@GameActivity)
                    // Deterministic return to the menu at match end instead of
                    // relying on SDL's own teardown ordering.
                    runOnUiThread { if (!isFinishing) finishAndRemoveTask() }
                }
            },
            varStore = varStore,
            recorder = recorder,
            rawConfig = replayFile?.let { file -> { file.readBytes() } },
        )
        connection = conn
        conn.start()
        Log.i(TAG, "IPC server listening on 127.0.0.1:${conn.port}")

        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        backCallback?.let {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                it,
            )
        }
    }

    // Outcome bookkeeping across backgrounding: swiping the task away kills
    // this process WITHOUT onDestroy, so a "running" state would be
    // misreported as a crash by the menu. Downgrade to ok whenever the
    // activity leaves the screen (the engine auto-pauses then anyway) and
    // restore "running" when it comes back. A real crash kills the foreground
    // process with no onStop — the state stays "running" and the dialog
    // correctly fires.
    override fun onStart() {
        super.onStart()
        if (connection != null) EngineOutcome.markRunning(this)
    }

    override fun onStop() {
        if (!ghostRecreation) EngineOutcome.markAbortedByUser(this)
        // Leaving the game screen ends the match. Suspending the engine in the
        // background and resuming it later means tearing down and rebuilding
        // the whole GL state through gl4es — the path that reliably died on a
        // real phone ("IPC connection lost" after the fullscr reinit) and left
        // a zombie :game process that broke every following match (singleTask
        // re-entered the stale instance instead of starting a new engine).
        // Ending the match on the way out keeps every match hermetic: one
        // Fight! = one fresh process. The screen cannot turn off mid-game
        // (FLAG_KEEP_SCREEN_ON), so this only triggers on a deliberate exit.
        if (!isFinishing) finishAndRemoveTask()
        super.onStop()
    }

    // Safety net: if a new match Intent ever reaches a still-live instance
    // (singleTask), don't try to reuse the running engine — shut down; the
    // menu stays visible and the next Fight! gets a clean process.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.w(TAG, "new match intent hit a live game instance; shutting it down")
        if (!isFinishing) finishAndRemoveTask()
    }

    override fun onDestroy() {
        backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        quitDialog?.dismiss()
        quitDialog = null
        connection?.close()
        if (!ghostRecreation) EngineOutcome.markAbortedByUser(this)
        super.onDestroy()
        // A fresh :game process per match: Pascal globals never leak between
        // games, and the next launch reloads everything cleanly.
        Process.killProcess(Process.myPid())
    }

    companion object {
        private const val TAG = "HWGameActivity"
        const val EXTRA_ENGINE_ARGS = "org.hedgewars.android.ENGINE_ARGS"
        const val EXTRA_CONFIG = "org.hedgewars.android.CONFIG"
        const val EXTRA_RENDER_W = "org.hedgewars.android.RENDER_W"
        const val EXTRA_RENDER_H = "org.hedgewars.android.RENDER_H"
        const val EXTRA_CAMPAIGN_TEAM = "org.hedgewars.android.CAMPAIGN_TEAM"
        const val EXTRA_CAMPAIGN_SCOPE = "org.hedgewars.android.CAMPAIGN_SCOPE"
        const val EXTRA_REPLAY_PATH = "org.hedgewars.android.REPLAY_PATH"

        fun intent(
            context: Context,
            engineArgs: List<String>,
            config: List<String>,
            /** Fixed render-buffer size; 0x0 renders at native resolution. */
            renderW: Int = 0,
            renderH: Int = 0,
            campaignTeam: String? = null,
            campaignScope: String? = null,
            /** Demo file to replay instead of playing a new match. */
            replayPath: String? = null,
        ): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_ENGINE_ARGS, engineArgs.toTypedArray())
                .putExtra(EXTRA_CONFIG, config.toTypedArray())
                .putExtra(EXTRA_RENDER_W, renderW)
                .putExtra(EXTRA_RENDER_H, renderH)
                .putExtra(EXTRA_CAMPAIGN_TEAM, campaignTeam)
                .putExtra(EXTRA_CAMPAIGN_SCOPE, campaignScope)
                .putExtra(EXTRA_REPLAY_PATH, replayPath)
    }
}
