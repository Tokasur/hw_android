package org.hedgewars.android.engine

import android.content.Context
import androidx.core.content.edit
import java.io.File
import org.hedgewars.android.data.GamePaths

/**
 * Records how the last engine run ended so the frontend can show an error
 * screen instead of silently returning to the menu.
 *
 * The engine and the game UI live in a separate ":game" process, so the
 * outcome is persisted through SharedPreferences and read back when the menu
 * process resumes. A hard native crash (SIGABRT) never sends an IPC 'E', so
 * the frontend also treats "socket closed before a clean finish" as a failure.
 */
object EngineOutcome {
    private const val PREFS = "engine_outcome"
    private const val KEY_STATE = "state"
    private const val KEY_MESSAGE = "message"

    const val STATE_RUNNING = "running"
    const val STATE_OK = "ok"
    const val STATE_ERROR = "error"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun markRunning(context: Context) =
        prefs(context).edit { putString(KEY_STATE, STATE_RUNNING); remove(KEY_MESSAGE) }

    fun markFinished(context: Context) =
        prefs(context).edit { putString(KEY_STATE, STATE_OK) }

    fun markError(context: Context, message: String?) =
        prefs(context).edit {
            putString(KEY_STATE, STATE_ERROR)
            if (message != null) putString(KEY_MESSAGE, message)
        }

    /** Returns an error message to show once, or null. Clears the state. */
    fun consumeError(context: Context): String? {
        val p = prefs(context)
        val state = p.getString(KEY_STATE, STATE_OK)
        if (state != STATE_ERROR && state != STATE_RUNNING) return null
        // STATE_RUNNING here means the game process died without reporting a
        // clean finish (a native crash) — surface it too.
        val msg = p.getString(KEY_MESSAGE, null)
        p.edit { putString(KEY_STATE, STATE_OK) }
        return msg ?: "The game engine stopped unexpectedly."
    }

    /** Most recent engine log written to <user>/Logs (needs -dDEBUGFILE). */
    fun readEngineLog(context: Context): String? {
        val logs = File(GamePaths(context).userDir, "Logs")
        val file = logs.listFiles { f -> f.name.startsWith("game") && f.extension == "log" }
            ?.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { file.readText().takeLast(20_000) }.getOrNull()
    }
}
