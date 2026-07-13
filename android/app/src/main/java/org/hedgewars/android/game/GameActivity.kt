package org.hedgewars.android.game

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Matrix
import android.opengl.EGL14
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.MotionEvent
import android.view.WindowManager
import org.hedgewars.android.data.CampaignStore
import org.hedgewars.android.engine.EngineLoader
import org.hedgewars.android.engine.EngineOutcome
import org.hedgewars.android.engine.GameConnection
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

    override fun onCreate(savedInstanceState: Bundle?) {
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

        // Deliver the back button to the engine as the "ac_back" key (bound
        // to the quit-confirmation) instead of finishing the activity.
        android.system.Os.setenv("SDL_ANDROID_TRAP_BACK_BUTTON", "1", true)
        // Belt-and-suspenders: also hand SDL the orientation hint so its own
        // bookkeeping agrees with the forced request above (SDL_HINT_ORIENTATIONS
        // == "SDL_IOS_ORIENTATIONS", read from the environment by SDL_GetHint).
        android.system.Os.setenv("SDL_IOS_ORIENTATIONS", "LandscapeLeft LandscapeRight", true)

        val config = intent.getStringArrayExtra(EXTRA_CONFIG)?.toList() ?: emptyList()
        val varStore = intent.getStringExtra(EXTRA_CAMPAIGN_TEAM)?.let { team ->
            CampaignStore(this, team, intent.getStringExtra(EXTRA_CAMPAIGN_SCOPE) ?: "")
        }
        EngineOutcome.markRunning(this)
        val conn = GameConnection(
            configCommands = { config },
            listener = object : GameConnection.Listener {
                override fun onEngineError(message: String) {
                    EngineOutcome.markError(this@GameActivity, message)
                    runOnUiThread { if (!isFinishing) finishAndRemoveTask() }
                }
                override fun onGameFinished(interrupted: Boolean) {
                    EngineOutcome.markFinished(this@GameActivity)
                    // Deterministic return to the menu at match end instead of
                    // relying on SDL's own teardown ordering.
                    runOnUiThread { if (!isFinishing) finishAndRemoveTask() }
                }
            },
            varStore = varStore,
        )
        connection = conn
        conn.start()
        Log.i(TAG, "IPC server listening on 127.0.0.1:${conn.port}")

        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        EngineOutcome.markAbortedByUser(this)
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
        connection?.close()
        EngineOutcome.markAbortedByUser(this)
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

        fun intent(
            context: Context,
            engineArgs: List<String>,
            config: List<String>,
            /** Fixed render-buffer size; 0x0 renders at native resolution. */
            renderW: Int = 0,
            renderH: Int = 0,
            campaignTeam: String? = null,
            campaignScope: String? = null,
        ): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_ENGINE_ARGS, engineArgs.toTypedArray())
                .putExtra(EXTRA_CONFIG, config.toTypedArray())
                .putExtra(EXTRA_RENDER_W, renderW)
                .putExtra(EXTRA_RENDER_H, renderH)
                .putExtra(EXTRA_CAMPAIGN_TEAM, campaignTeam)
                .putExtra(EXTRA_CAMPAIGN_SCOPE, campaignScope)
    }
}
