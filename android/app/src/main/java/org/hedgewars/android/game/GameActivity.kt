package org.hedgewars.android.game

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.WindowManager
import org.hedgewars.android.data.CampaignStore
import org.hedgewars.android.engine.EngineLoader
import org.hedgewars.android.engine.EngineOutcome
import org.hedgewars.android.engine.GameConnection
import org.libsdl.app.SDLActivity

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

    override fun onCreate(savedInstanceState: Bundle?) {
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
        const val EXTRA_CAMPAIGN_TEAM = "org.hedgewars.android.CAMPAIGN_TEAM"
        const val EXTRA_CAMPAIGN_SCOPE = "org.hedgewars.android.CAMPAIGN_SCOPE"

        fun intent(
            context: Context,
            engineArgs: List<String>,
            config: List<String>,
            campaignTeam: String? = null,
            campaignScope: String? = null,
        ): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_ENGINE_ARGS, engineArgs.toTypedArray())
                .putExtra(EXTRA_CONFIG, config.toTypedArray())
                .putExtra(EXTRA_CAMPAIGN_TEAM, campaignTeam)
                .putExtra(EXTRA_CAMPAIGN_SCOPE, campaignScope)
    }
}
