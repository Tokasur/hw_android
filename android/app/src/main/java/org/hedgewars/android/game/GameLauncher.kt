package org.hedgewars.android.game

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import org.hedgewars.android.config.ConfigSerializer
import org.hedgewars.android.config.GameConfig
import org.hedgewars.android.config.MissionConfig
import org.hedgewars.android.data.BindsWriter
import org.hedgewars.android.data.CampaignStore
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.UserPrefs
import org.hedgewars.android.engine.EngineArgs
import org.hedgewars.android.engine.GameConnection

/**
 * Glues a game setup to an engine run: opens the IPC server, prepares the
 * user prefix (gamepad binds, config), starts GameActivity in the :game
 * process and serves the config when the engine asks for it.
 */
class GameLauncher(private val context: Context) {

    private val paths = GamePaths(context)
    private val prefs = UserPrefs(context)

    private var connection: GameConnection? = null

    fun launchLocalGame(cfg: GameConfig, listener: GameConnection.Listener) {
        launch(listener, varStore = null) { ConfigSerializer.localGame(cfg) }
    }

    fun launchMission(cfg: MissionConfig, listener: GameConnection.Listener) {
        val store = CampaignStore(
            context,
            teamName = cfg.team.name,
            scope = cfg.campaign ?: cfg.script.substringAfterLast('/').removeSuffix(".lua"),
        )
        launch(listener, varStore = store) { ConfigSerializer.missionGame(cfg) }
    }

    private fun launch(
        listener: GameConnection.Listener,
        varStore: GameConnection.MissionVarStore?,
        config: () -> List<String>,
    ) {
        close()
        paths.ensureUserDirs()
        BindsWriter.write(paths.settingsIni, prefs.gamepadBinds)

        val conn = GameConnection(config, listener, varStore)
        connection = conn
        conn.start()

        val metrics: DisplayMetrics = context.resources.displayMetrics
        val width = maxOf(metrics.widthPixels, metrics.heightPixels)
        val height = minOf(metrics.widthPixels, metrics.heightPixels)

        val args = EngineArgs(
            ipcPort = conn.port,
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
        Log.i(TAG, "starting engine on port ${conn.port}")
        context.startActivity(
            GameActivity.intent(context, args.toList())
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun close() {
        connection?.close()
        connection = null
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
