package org.hedgewars.android.engine

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json
import org.hedgewars.android.config.StatsReport

/**
 * What the last match left behind for the menu to pick up: its statistics and
 * its recording.
 *
 * Same channel as [EngineOutcome], for the same reason — the engine and its IPC
 * live in the ":game" process while the screens live in the main one, and
 * SharedPreferences do not sync across processes. Both files are written
 * ".tmp"-then-rename so the menu can never read a half-written record, and both
 * are written BEFORE the game activity finishes (onDestroy kills the process
 * on the spot).
 */
object MatchRecords {

    private val json = Json { ignoreUnknownKeys = true }

    private fun statsFile(context: Context) = File(context.filesDir, "last_stats")

    /** The unsaved recording of the last match, if it was recorded. */
    fun demoTemp(context: Context): File = File(context.filesDir, "last_demo.hwd")

    fun writeStats(context: Context, report: StatsReport) {
        write(statsFile(context)) { it.writeText(json.encodeToString(report)) }
    }

    fun writeDemo(context: Context, bytes: ByteArray) {
        write(demoTemp(context)) { it.writeBytes(bytes) }
    }

    /** Reads the stats of the last match and forgets them. */
    fun consumeStats(context: Context): StatsReport? {
        val file = statsFile(context)
        val text = runCatching { file.readText() }.getOrNull()
        runCatching { file.delete() }
        if (text.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<StatsReport>(text) }.getOrNull()
    }

    /** Drops both records — a match that failed leaves nothing to show. */
    fun sweep(context: Context) {
        runCatching { statsFile(context).delete() }
        runCatching { demoTemp(context).delete() }
    }

    private fun write(target: File, body: (File) -> Unit) {
        runCatching {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            body(tmp)
            if (!tmp.renameTo(target)) {
                tmp.delete()
            }
        }
    }
}
