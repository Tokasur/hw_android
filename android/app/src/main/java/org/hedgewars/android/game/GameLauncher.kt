package org.hedgewars.android.game

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import org.hedgewars.android.config.ConfigSerializer
import org.hedgewars.android.config.FortPicker
import org.hedgewars.android.config.GameConfig
import org.hedgewars.android.config.MissionConfig
import org.hedgewars.android.config.TeamSlot
import org.hedgewars.android.data.BindsWriter
import org.hedgewars.android.data.DemosRepository
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.MissionsRepository
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
        // Remembered BEFORE the forts are resolved, so "play again" re-rolls
        // them along with the terrain.
        lastLocalConfig = cfg
        launch(ConfigSerializer.localGame(cfg.copy(teams = resolveForts(cfg.teams))))
    }

    fun launchMission(cfg: MissionConfig) {
        val scope = cfg.campaign ?: cfg.script.substringAfterLast('/').removeSuffix(".lua")
        val team = cfg.team.copy(fort = FortPicker.resolve(cfg.team.fort, installedForts()))
        launch(
            ConfigSerializer.missionGame(cfg.copy(team = team)),
            campaignTeam = cfg.team.name,
            campaignScope = scope,
        )
    }

    /**
     * Gives every team that has no fort of its own a real one for this match
     * (see [FortPicker]). The engine only ever draws the fort of a clan's
     * first team, and a name with no image is fatal, so this has to happen
     * before the config is serialized.
     */
    private fun resolveForts(teams: List<TeamSlot>): List<TeamSlot> =
        FortPicker.resolveSlots(teams, installedForts())

    private fun installedForts(): List<String> = MissionsRepository(paths).forts()

    /**
     * Plays back a recorded match: the same engine launch, except the demo's
     * bytes are what the engine gets when it asks for a config (the recording
     * already starts with the config it was made with).
     */
    fun launchReplay(demo: java.io.File) {
        launch(
            emptyList(),
            replayPath = demo.absolutePath,
            // Replay it in the language it was recorded in, whatever the app is
            // set to now: a Lua script folds its localization file into the
            // state checksum, so the wrong locale desynchronizes the replay.
            forcedLocale = DemosRepository(context).recordedLocale(DemosRepository.Demo(demo)),
        )
    }

    private fun launch(
        config: List<String>,
        campaignTeam: String? = null,
        campaignScope: String? = null,
        replayPath: String? = null,
        forcedLocale: String? = null,
    ) {
        ensureFreshGameProcess(context)
        // Whatever the previous match left behind is stale from here on: the
        // stats screen must never show the results of the game before this one.
        org.hedgewars.android.engine.MatchRecords.sweep(context)
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
            localeFile = forcedLocale ?: EngineArgs.localeFileFor(context),
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
        val intent = GameActivity.intent(
            context, args.toList(), config,
            renderW = renderW, renderH = renderH,
            campaignTeam = campaignTeam, campaignScope = campaignScope,
            replayPath = replayPath,
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Remembered so a crash-at-start can be relaunched automatically.
        LastLaunch.record(intent)
        context.startActivity(intent)
    }

    companion object {
        private const val TAG = "HWGameLauncher"

        /**
         * The last local match set up in this (menu) process, so the stats
         * screen can offer "Play again". Deliberately in memory only: it is a
         * convenience for the session, not saved state.
         */
        @Volatile
        var lastLocalConfig: GameConfig? = null
            private set

        /**
         * The ":game" process must be truly fresh before a match starts: SDL
         * keeps process-global window state, and launching into a process
         * where the previous SDL window is still tearing down makes
         * SDL_CreateWindow fail ("Android only supports one window") and
         * takes the engine down with it — the historical random crash at
         * match start on fast devices. A crashed engine usually leaves its
         * process lingering (onDestroy never ran), so kill any leftover
         * (same-uid kill is permitted) and wait until the record is gone.
         */
        fun ensureFreshGameProcess(context: Context) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            fun stale() = am.runningAppProcesses.orEmpty().filter { it.processName.endsWith(":game") }
            val found = stale()
            if (found.isEmpty()) return
            found.forEach {
                Log.w(TAG, "killing stale :game process pid=${it.pid}")
                android.os.Process.killProcess(it.pid)
            }
            val deadline = SystemClock.elapsedRealtime() + 1_000
            while (stale().isNotEmpty() && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50)
            }
        }

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
