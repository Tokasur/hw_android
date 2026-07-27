package org.hedgewars.android.engine

import android.content.Context
import java.io.File
import org.hedgewars.android.data.GamePaths

/**
 * Records how the last engine run ended so the frontend can show an error
 * screen instead of silently returning to the menu.
 *
 * The engine and its IPC server live in a separate ":game" process while the
 * menu lives in the main process, so the outcome is persisted to a small FILE
 * (SharedPreferences do not sync across processes). The ":game" process writes
 * it; the menu process reads it on resume. A hard native crash (SIGABRT) can
 * never write "ok"/"error", so a state left at "running" is treated as a crash.
 */
object EngineOutcome {
    private const val STATE_RUNNING = "running"
    private const val STATE_OK = "ok"
    private const val STATE_ERROR = "error"

    private fun file(context: Context) = File(context.filesDir, "last_outcome")

    fun markRunning(context: Context) = write(context, STATE_RUNNING, null)
    fun markFinished(context: Context) = write(context, STATE_OK, null)
    fun markError(context: Context, message: String?) = write(context, STATE_ERROR, message)

    /**
     * The game activity is being destroyed in an orderly way (user backed out,
     * swiped the task away, system reclaimed it). If nothing more specific was
     * recorded, downgrade "running" to "ok" so the menu does not show a scary
     * "engine stopped unexpectedly" dialog for a manual quit. A recorded error
     * is kept as-is.
     */
    fun markAbortedByUser(context: Context) {
        val state = runCatching { file(context).readText() }.getOrNull()
            ?.split('\n', limit = 2)?.firstOrNull()
        if (state == STATE_RUNNING) write(context, STATE_OK, null)
    }

    private fun write(context: Context, state: String, message: String?) {
        runCatching {
            file(context).writeText(state + (message?.let { "\n$it" } ?: ""))
        }
    }

    /**
     * How the last run failed: [hardCrash] is a process death that never got
     * to report anything (state still "running" — native crash or kill),
     * as opposed to an error the engine itself reported over IPC.
     */
    data class EngineFailure(val message: String, val hardCrash: Boolean)

    /** Returns the failure to show once, or null. Clears the state. */
    fun consumeError(context: Context): EngineFailure? {
        val f = file(context)
        val content = runCatching { f.readText() }.getOrNull() ?: return null
        val lines = content.split('\n', limit = 2)
        val state = lines.firstOrNull()
        runCatching { f.writeText(STATE_OK) }
        return when (state) {
            STATE_ERROR -> EngineFailure(
                lines.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: "The game engine reported an error.",
                hardCrash = false,
            )
            STATE_RUNNING -> EngineFailure("The game engine stopped unexpectedly.", hardCrash = true)
            else -> null
        }
    }

    /** Most recent engine log written to <user>/Logs (needs -dDEBUGFILE). */
    fun readEngineLog(context: Context): String? {
        val logs = File(GamePaths(context).userDir, "Logs")
        val file = logs.listFiles { f -> f.name.startsWith("game") && f.extension == "log" }
            ?.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { file.readText().takeLast(20_000) }.getOrNull()
    }
}
