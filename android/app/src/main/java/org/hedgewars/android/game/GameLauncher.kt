package org.hedgewars.android.game

import android.content.Context
import android.content.Intent
import android.util.DisplayMetrics
import android.util.Log
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

        val metrics: DisplayMetrics = context.resources.displayMetrics
        val width = maxOf(metrics.widthPixels, metrics.heightPixels)
        val height = minOf(metrics.widthPixels, metrics.heightPixels)

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
        Log.i(TAG, "launching game with ${config.size} config commands")
        context.startActivity(
            GameActivity.intent(context, args.toList(), config, campaignTeam, campaignScope)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
    }
}
