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
        ScaledSDLSurface(context, intent.getFloatExtra(EXTRA_UI_SCALE, 1f).coerceIn(1f, 4f))

    /**
     * SDL surface with a reduced fixed-size buffer that Android upscales to
     * the full view. The engine's touch buttons and HUD are sized in raw
     * pixels, so rendering at view-size / [scale] makes them [scale]x bigger
     * physically (and cuts the pixels to render by scale^2).
     */
    private class ScaledSDLSurface(context: Context, private val scale: Float) :
        SDLSurface(context) {

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w > 0 && h > 0 && scale > 1.001f) {
                val rw = ((w / scale).toInt() / 2) * 2
                val rh = ((h / scale).toInt() / 2) * 2
                if (rw >= 2 && rh >= 2) {
                    Log.i(TAG, "render buffer ${rw}x$rh for view ${w}x$h (scale $scale)")
                    holder.setFixedSize(rw, rh)
                }
            }
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
                override fun onEngineError(message: String) = EngineOutcome.markError(this@GameActivity, message)
                override fun onGameFinished(interrupted: Boolean) = EngineOutcome.markFinished(this@GameActivity)
            },
            varStore = varStore,
        )
        connection = conn
        conn.start()
        Log.i(TAG, "IPC server listening on 127.0.0.1:${conn.port}")

        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        connection?.close()
        super.onDestroy()
        // A fresh :game process per match: Pascal globals never leak between
        // games, and the next launch reloads everything cleanly.
        Process.killProcess(Process.myPid())
    }

    companion object {
        private const val TAG = "HWGameActivity"
        const val EXTRA_ENGINE_ARGS = "org.hedgewars.android.ENGINE_ARGS"
        const val EXTRA_CONFIG = "org.hedgewars.android.CONFIG"
        const val EXTRA_UI_SCALE = "org.hedgewars.android.UI_SCALE"
        const val EXTRA_CAMPAIGN_TEAM = "org.hedgewars.android.CAMPAIGN_TEAM"
        const val EXTRA_CAMPAIGN_SCOPE = "org.hedgewars.android.CAMPAIGN_SCOPE"

        fun intent(
            context: Context,
            engineArgs: List<String>,
            config: List<String>,
            uiScale: Float = 1f,
            campaignTeam: String? = null,
            campaignScope: String? = null,
        ): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_ENGINE_ARGS, engineArgs.toTypedArray())
                .putExtra(EXTRA_CONFIG, config.toTypedArray())
                .putExtra(EXTRA_UI_SCALE, uiScale)
                .putExtra(EXTRA_CAMPAIGN_TEAM, campaignTeam)
                .putExtra(EXTRA_CAMPAIGN_SCOPE, campaignScope)
    }
}
