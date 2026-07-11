package org.hedgewars.android.engine

import android.util.Base64

/**
 * Builds the engine command line (hedgewars/ArgParsers.pas).
 *
 * Only environment/display options travel on the command line; the whole game
 * setup goes through the IPC socket. Paths are passed base64-encoded
 * (--prefix64/--user-prefix64) to sidestep any encoding trouble.
 */
data class EngineArgs(
    val ipcPort: Int,
    val dataPrefix: String,
    val userPrefix: String,
    val width: Int,
    val height: Int,
    val localeFile: String = "en.txt",
    val landPreview: Boolean = false,
    val sound: Boolean = true,
    val music: Boolean = true,
    val showFps: Boolean = false,
    val lowQuality: Boolean = false,
) {
    fun toList(): List<String> {
        val args = mutableListOf(
            "--internal",              // must come first (enables --port)
            "--port", ipcPort.toString(),
            "--prefix64", b64(dataPrefix),
            "--user-prefix64", b64(userPrefix),
            "--locale", localeFile,
        )
        if (landPreview) {
            args += "--landpreview"
            return args
        }
        args += listOf(
            "--width", width.toString(),
            "--height", height.toString(),
            "--fullscreen-width", width.toString(),
            "--fullscreen-height", height.toString(),
            "--fullscreen",
        )
        if (!sound) args += "--nosound"
        if (!music) args += "--nomusic"
        if (showFps) args += "--showfps"
        if (lowQuality) args += "--low-quality"
        return args
    }

    private fun b64(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
}
