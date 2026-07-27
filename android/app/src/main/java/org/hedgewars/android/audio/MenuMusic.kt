package org.hedgewars.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.UserPrefs

/**
 * Looping main-theme player for the frontend menus.
 *
 * Owned by MainActivity (started in onStart, stopped in onStop). Matches run
 * in the separate ":game" process with the engine's own music, and bringing
 * GameActivity to the front stops MainActivity — so menu music can never
 * bleed into a match. The theme file is part of the installed game data
 * (InstallGate guarantees it exists before any menu renders).
 *
 * Every path is defensive: menu music must never take a menu down with it.
 */
object MenuMusic {
    private const val TAG = "HWMenuMusic"
    private var player: MediaPlayer? = null

    fun start(context: Context) {
        if (!UserPrefs(context).menuMusic) return
        if (runCatching { player?.isPlaying == true }.getOrDefault(false)) return
        stop()
        runCatching {
            val theme = File(GamePaths(context).dataDir, "Music/main_theme.ogg")
            if (!theme.isFile) {
                Log.w(TAG, "main theme not found at ${theme.path}")
                return
            }
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(theme.absolutePath)
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        }.onFailure {
            Log.w(TAG, "menu music unavailable: $it")
            stop()
        }
    }

    fun stop() {
        runCatching { player?.release() }
        player = null
    }
}
