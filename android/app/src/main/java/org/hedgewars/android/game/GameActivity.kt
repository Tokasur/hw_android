package org.hedgewars.android.game

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.WindowManager
import org.hedgewars.android.engine.EngineLoader
import org.libsdl.app.SDLActivity

/**
 * Hosts the Free Pascal engine through SDL.
 *
 * Runs in the separate ":game" process (see AndroidManifest) so every match
 * starts from a pristine engine state; the process is killed in onDestroy.
 * The frontend passes the full engine command line through the Intent, and
 * the engine connects back to the frontend's loopback IPC socket.
 */
class GameActivity : SDLActivity() {

    override fun getLibraries(): Array<String> = EngineLoader.libraries + "main"

    override fun getArguments(): Array<String> =
        intent.getStringArrayExtra(EXTRA_ENGINE_ARGS) ?: emptyArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        // A fresh :game process per match: Pascal globals never leak between
        // games, and the next launch reloads everything cleanly.
        Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_ENGINE_ARGS = "org.hedgewars.android.ENGINE_ARGS"

        fun intent(context: Context, engineArgs: List<String>): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_ENGINE_ARGS, engineArgs.toTypedArray())
    }
}
