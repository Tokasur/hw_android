package org.hedgewars.android.game

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.WindowManager
import org.hedgewars.android.config.ConfigSerializer
import org.hedgewars.android.config.GameConfig
import org.hedgewars.android.config.MissionConfig
import org.hedgewars.android.data.BindsWriter
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.UserPrefs
import org.hedgewars.android.engine.EngineArgs

/**
 * Turns a game setup into an engine launch.
 *
 * The whole game runs in the ":game" process (GameActivity), which also owns
 * the IPC server — so this class only prepares the user prefix, serializes the
 * config, and hands everything to GameActivity through the Intent. Nothing here
 * outlives the launch, so the menu process can be backgrounded freely.
 */
class GameLauncher(private val context: Context) {

    private val paths = GamePaths(context)
    private val prefs = UserPrefs(context)

    fun launchLocalGame(cfg: GameConfig) {
        launch(ConfigSerializer.localGame(cfg))
    }

    fun launchMission(cfg: MissionConfig) {
        val scope = cfg.campaign ?: cfg.script.substringAfterLast('/').removeSuffix(".lua")
        launch(ConfigSerializer.missionGame(cfg), campaignTeam = cfg.team.name, campaignScope = scope)
    }

    private fun launch(
        config: List<String>,
        campaignTeam: String? = null,
        campaignScope: String? = null,
    ) {
        paths.ensureUserDirs()
        BindsWriter.write(paths.settingsIni, prefs.gamepadBinds)

        // Real display size (not the inset-clipped app area): the game runs
        // fullscreen immersive, and the engine paints exactly --width/--height
        // pixels — computing from the menu's inset metrics leaves an undrawn
        // band where the status/navigation bars used to be.
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (rawW, rawH) = if (android.os.Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val p = android.graphics.Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
            p.x to p.y
        }
        val physWidth = maxOf(rawW, rawH)
        val physHeight = minOf(rawW, rawH)

        // The engine's touch UI is sized in raw pixels (100x100 arrows...);
        // at phone densities that is ~6 mm — unusable. Render at a reduced
        // resolution instead (Android upscales the surface for free): the
        // buttons and HUD grow back to the physical size they were designed
        // for. The exact buffer size is applied by ScaledSDLSurface; these
        // args just need to be close (SDL's resize event fixes the rest).
        val scale = uiScale(physHeight, prefs.uiScale)
        val width = ((physWidth / scale).toInt() / 2) * 2
        val height = ((physHeight / scale).toInt() / 2) * 2

        // The port is a placeholder: GameActivity opens the server and injects
        // the real port before the engine reads its arguments.
        val args = EngineArgs(
            ipcPort = 0,
            dataPrefix = paths.dataDir.absolutePath,
            userPrefix = paths.userDir.absolutePath,
            width = width,
            height = height,
            localeFile = engineLocaleFile(),
            sound = prefs.sound,
            music = prefs.music,
            showFps = prefs.showFps,
            lowQuality = prefs.lowQuality,
        )
        Log.i(TAG, "launching game at ${width}x$height (ui scale $scale) with ${config.size} config commands")
        // The same numbers become the surface's fixed buffer size, decided
        // here once and never changed mid-game (scale 1 = native, no fixing).
        val renderW = if (scale > 1.001f) width else 0
        val renderH = if (scale > 1.001f) height else 0
        context.startActivity(
            GameActivity.intent(
                context, args.toList(), config,
                renderW = renderW, renderH = renderH,
                campaignTeam = campaignTeam, campaignScope = campaignScope,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Engine locale file matching the app language, if shipped. */
    private fun engineLocaleFile(): String {
        val lang = context.resources.configuration.locales[0].language
        val file = "$lang.txt"
        return if (java.io.File(paths.dataDir, "Locale/$file").exists()) file else "en.txt"
    }

    companion object {
        private const val TAG = "HWGameLauncher"

        /**
         * Resolve the render-scale factor for the in-game UI.
         *
         * "auto" targets an effective landscape height of ~480 px — the WVGA
         * class of screen the engine's fixed-pixel touch UI was designed for —
         * in quarter steps: a 1080 px-tall phone gets x2.25 (buttons ~14 mm;
         * x2 was reported still a bit small), a 720 px screen x1.5, anything
         * at or below 480 px stays at x1.
         */
        fun uiScale(landscapeHeightPx: Int, pref: String): Float {
            pref.toFloatOrNull()?.let { return it.coerceIn(1f, 3f) }
            val quarters = Math.round(landscapeHeightPx / 480f * 4f)
            return (quarters / 4f).coerceIn(1f, 3f)
        }
    }
}
