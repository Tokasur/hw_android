package org.hedgewars.android.engine

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File

/**
 * Explains WHY the ":game" process died, straight from the system's exit-info
 * records (API 30+). A hard native crash can't leave anything in our own
 * logs — the process is gone before any handler runs — but the kernel/AMS
 * record survives, including (API 31+) the native tombstone with the signal,
 * fault address and backtrace. Surfacing it through the error dialog's
 * "Share log" turns "the game just closed" reports into actionable ones.
 */
object GameProcessExitInfo {
    private const val GAME_PROCESS = "org.hedgewars.android:game"
    private const val SIGKILL = 9
    private const val MAX_TRACE_CHARS = 40_000

    /**
     * Report for the most recent unhandled abnormal exit of the game process,
     * or null. Each record is reported at most once (timestamp marker file);
     * our own orderly SIGKILL (GameActivity.onDestroy kills the process after
     * every match) is not abnormal and is skipped.
     */
    fun report(context: Context): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exits = try {
            am.getHistoricalProcessExitReasons(context.packageName, 0, 16)
        } catch (e: Exception) {
            return null
        }
        val marker = File(context.filesDir, "last_exit_handled")
        val lastHandled = runCatching { marker.readText().trim().toLong() }.getOrDefault(0L)
        val exit = exits.firstOrNull { it.processName == GAME_PROCESS && it.timestamp > lastHandled }
            ?: return null
        runCatching { marker.writeText(exit.timestamp.toString()) }

        val interesting = when (exit.reason) {
            ApplicationExitInfo.REASON_CRASH,
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_LOW_MEMORY,
            -> true
            ApplicationExitInfo.REASON_SIGNALED -> exit.status != SIGKILL
            else -> false
        }
        if (!interesting) return null

        val sb = StringBuilder()
        sb.append("=== Game process exit info (system record) ===\n")
        sb.append(exit.toString()).append('\n')
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching {
                exit.traceInputStream?.use { stream ->
                    // For native crashes this is the tombstone as a binary
                    // proto; its string fields (signal, abort message, frame
                    // symbols, library paths) are what matters — extract the
                    // printable runs, like the `strings` tool would.
                    val text = printableRuns(stream.readBytes())
                    if (text.isNotBlank()) {
                        sb.append("--- trace/tombstone (printable strings) ---\n")
                        sb.append(text.takeLast(MAX_TRACE_CHARS)).append('\n')
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun printableRuns(bytes: ByteArray, minRun: Int = 4): String {
        val sb = StringBuilder()
        val run = StringBuilder()
        for (b in bytes) {
            val c = b.toInt().toChar()
            if (c == '\n' || (c.code in 0x20..0x7e)) {
                run.append(c)
            } else {
                if (run.length >= minRun) sb.append(run).append('\n')
                run.setLength(0)
            }
        }
        if (run.length >= minRun) sb.append(run).append('\n')
        return sb.toString()
    }
}
