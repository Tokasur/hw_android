package org.hedgewars.android.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saved replays: the *.hwd demo files the port keeps for local matches.
 *
 * Same layout and naming as the desktop (HWForm::GetRecord): a raw engine
 * command stream, named "<timestamp>.<proto>.hwd", living in a Demos/
 * directory under the user prefix — so a file pulled off the phone plays on
 * the desktop and vice versa, as long as the protocol version matches.
 */
class DemosRepository(private val dir: File) {
    constructor(context: Context) : this(File(GamePaths(context).userDir, "Demos"))

    data class Demo(val file: File) {
        /** "2026-07-30_14-05.60.hwd" -> "2026-07-30 14-05". */
        val displayName: String
            get() = file.name.removeSuffix(".$PROTO.$EXTENSION").replace('_', ' ')
        val sizeBytes: Long get() = file.length()
        val modified: Long get() = file.lastModified()
    }

    /** Most recent first. */
    fun list(): List<Demo> =
        dir.listFiles { f -> f.isFile && f.extension.equals(EXTENSION, ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.map { Demo(it) }
            ?: emptyList()

    fun delete(demo: Demo): Boolean {
        localeFile(demo.file).delete()
        return demo.file.delete()
    }

    /**
     * The engine locale a demo was recorded with, or null when we don't know
     * (a demo from before 0.3.0, or one copied in from the desktop).
     *
     * It has to be replayed with that same locale: loading a Lua script folds
     * the localization file into the state checksum (uScript.pas
     * ScriptLocaleReader), so a match recorded in French and replayed in
     * English desynchronizes as soon as a game style is involved.
     */
    fun recordedLocale(demo: Demo): String? =
        runCatching { localeFile(demo.file).readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }

    private fun localeFile(demo: File) = File(demo.parentFile, "${demo.name}.$LOCALE_EXTENSION")

    /**
     * Promotes the recording the game process left behind into a real replay:
     * flips the leading game-type token from local ("TL") to demo ("TD"),
     * writes it under a timestamped name and drops the temporary file.
     *
     * Returns null when there is nothing to save.
     */
    fun saveLastDemo(temp: File, at: Date = Date(), locale: String? = null): File? {
        val raw = runCatching { temp.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        dir.mkdirs()
        val out = uniqueFile(stamp(at))
        return runCatching {
            out.writeBytes(tlToTd(raw))
            // Remembered beside the demo rather than inside it: the stream is
            // the desktop's byte for byte, and must stay playable there.
            if (locale != null) runCatching { localeFile(out).writeText(locale) }
            temp.delete()
            out
        }.getOrNull()
    }

    fun sweepTemp(temp: File) {
        runCatching { temp.delete() }
    }

    private fun stamp(at: Date) = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(at)

    private fun uniqueFile(stamp: String): File {
        val first = File(dir, "$stamp.$PROTO.$EXTENSION")
        if (!first.exists()) return first
        // Two matches within the same minute: the desktop would overwrite.
        var n = 2
        while (true) {
            val candidate = File(dir, "${stamp}_$n.$PROTO.$EXTENSION")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    companion object {
        const val EXTENSION = "hwd"

        /** Sidecar holding the engine locale a demo was recorded with. */
        const val LOCALE_EXTENSION = "locale"

        /** Engine/frontend protocol version (CMakeLists HEDGEWARS_PROTO_VER). */
        const val PROTO = 60

        private val TL = byteArrayOf(0x02, 'T'.code.toByte(), 'L'.code.toByte())
        private val TD = byteArrayOf(0x02, 'T'.code.toByte(), 'D'.code.toByte())

        /**
         * Turns a recorded local game into a demo by rewriting the game-type
         * token, which is the very first frame of the stream (length byte
         * 0x02, then "TL"). Only the FIRST occurrence is touched: the same
         * three bytes can occur by chance inside the binary sync data, where
         * the desktop's replace-all would corrupt the recording.
         */
        fun tlToTd(bytes: ByteArray): ByteArray {
            val at = indexOf(bytes, TL)
            if (at < 0) return bytes
            val out = bytes.copyOf()
            TD.copyInto(out, at)
            return out
        }

        private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
            outer@ for (i in 0..haystack.size - needle.size) {
                for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
                return i
            }
            return -1
        }
    }
}
