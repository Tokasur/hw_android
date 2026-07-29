package org.hedgewars.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.PackContentIndex
import org.hedgewars.android.data.UserPrefs

/**
 * Looping main-theme player for the frontend menus.
 *
 * Owned by MainActivity (started in onStart, stopped in onStop). Matches run
 * in the separate ":game" process with the engine's own music, and bringing
 * GameActivity to the front stops MainActivity — so menu music can never
 * bleed into a match.
 *
 * Downloaded content can replace the theme (e.g. the "old main theme" pack
 * ships Music/main_theme.ogg): user content wins over the shipped file, and
 * deleting the pack falls back to the shipped theme — mirroring how the
 * engine's PhysFS mounts resolve the same file in-game. Pack entries are
 * extracted once into the cache dir, keyed by size so a different pack
 * refreshes it.
 *
 * Every path is defensive: menu music must never take a menu down with it.
 */
object MenuMusic {
    private const val TAG = "HWMenuMusic"
    private const val THEME_REL = "Music/main_theme.ogg"
    private const val CACHE_PREFIX = "menu_theme_"
    private var player: MediaPlayer? = null
    private var generation = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    fun start(context: Context) {
        if (!UserPrefs(context).menuMusic) return
        if (runCatching { player?.isPlaying == true }.getOrDefault(false)) return
        stop()
        val gen = ++generation
        val app = context.applicationContext
        // Resolving may read a zip entry — keep it off the main thread.
        Thread({
            val theme = runCatching { resolveTheme(app) }.getOrNull()
            mainHandler.post {
                if (gen != generation || theme == null) {
                    if (theme == null) Log.w(TAG, "no menu theme available")
                    return@post
                }
                play(theme)
            }
        }, "hw-menu-music").start()
    }

    private fun play(theme: File) {
        runCatching {
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

    /** User content first (loose file, then packs via cache), else shipped. */
    private fun resolveTheme(context: Context): File? {
        val paths = GamePaths(context)
        val loose = File(paths.userDataDir, THEME_REL)
        if (loose.isFile) return loose

        val bytes = runCatching { PackContentIndex(paths.userDataDir).readEntry(THEME_REL) }.getOrNull()
        if (bytes != null) {
            val cached = File(context.cacheDir, "$CACHE_PREFIX${bytes.size}.ogg")
            runCatching {
                if (!cached.isFile || cached.length() != bytes.size.toLong()) {
                    sweepCache(context, keep = cached.name)
                    cached.writeBytes(bytes)
                }
            }
            if (cached.isFile) return cached
        } else {
            sweepCache(context, keep = null)
        }

        return File(paths.dataDir, THEME_REL).takeIf { it.isFile }
    }

    private fun sweepCache(context: Context, keep: String?) {
        runCatching {
            context.cacheDir.listFiles { f -> f.name.startsWith(CACHE_PREFIX) && f.name != keep }
                ?.forEach { it.delete() }
        }
    }

    fun stop() {
        generation++
        runCatching { player?.release() }
        player = null
    }
}
