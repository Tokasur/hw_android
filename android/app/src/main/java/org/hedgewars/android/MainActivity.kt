package org.hedgewars.android

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.hedgewars.android.audio.MenuMusic
import org.hedgewars.android.engine.EngineOutcome
import org.hedgewars.android.engine.GameProcessExitInfo
import org.hedgewars.android.engine.MatchRecords
import org.hedgewars.android.game.GameLauncher
import org.hedgewars.android.game.LastLaunch
import org.hedgewars.android.ui.error.EngineErrorDialog
import org.hedgewars.android.ui.about.AboutScreen
import org.hedgewars.android.ui.about.ControlsScreen
import org.hedgewars.android.ui.dlc.DlcScreen
import org.hedgewars.android.ui.install.InstallGate
import org.hedgewars.android.ui.menu.HomeScreen
import org.hedgewars.android.ui.missions.MissionsScreen
import org.hedgewars.android.ui.newgame.NewGameMode
import org.hedgewars.android.ui.newgame.NewGameScreen
import org.hedgewars.android.ui.replays.ReplaysScreen
import org.hedgewars.android.ui.schemes.SchemeEditScreen
import org.hedgewars.android.ui.schemes.SchemesScreen
import org.hedgewars.android.ui.settings.SettingsScreen
import org.hedgewars.android.ui.stats.StatsHolder
import org.hedgewars.android.ui.stats.StatsScreen
import org.hedgewars.android.ui.weapons.WeaponSetEditScreen
import org.hedgewars.android.ui.weapons.WeaponSetsScreen
import org.hedgewars.android.ui.teams.TeamEditScreen
import org.hedgewars.android.ui.teams.TeamsScreen
import org.hedgewars.android.ui.theme.HedgewarsBackground
import org.hedgewars.android.ui.theme.HedgewarsTheme

class MainActivity : ComponentActivity() {
    // Applies the language chosen in settings on API < 33 (on API 33+ the
    // platform's per-app language already did it before we get here).
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(org.hedgewars.android.data.AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            HedgewarsTheme {
                InstallGate {
                    AppNavigation()
                }
            }
        }
    }

    // Menu music lives exactly as long as the menus are on screen: launching
    // a match (GameActivity, ":game" process) or leaving the app stops this
    // activity — and the music with it.
    override fun onStart() {
        super.onStart()
        MenuMusic.start(this)
    }

    override fun onStop() {
        // A rotation stops and recreates this activity; cutting the music
        // there restarted the track from the top on every turn of the device.
        // The player is process-global, so simply leaving it running keeps the
        // music seamless (onStart's start() is idempotent).
        if (!isChangingConfigurations) MenuMusic.stop()
        super.onStop()
    }
}

@Composable
private fun AppNavigation() {
    val nav = rememberNavController()

    // When we come back from a game, surface any engine error / native crash.
    // A hard crash right after launch gets up to two silent automatic
    // relaunches first (a rare timing-dependent native crash at engine start
    // on fast devices succeeds on retry); if the budget is spent, the dialog
    // shows — enriched with the system's exit record and native tombstone.
    val context = LocalContext.current

    // AppNavigation only composes once InstallGate is Ready, i.e. once the
    // game data (including the theme file) is fully installed. On a FRESH
    // install MainActivity.onStart fires before that point and its
    // MenuMusic.start finds no file — this one covers that first session.
    // Later duplicate calls are no-ops.
    LaunchedEffect(Unit) { MenuMusic.start(context) }

    var engineError by remember { mutableStateOf<String?>(null) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        val failure = EngineOutcome.consumeError(context)
        val retry = if (failure != null && failure.hardCrash) LastLaunch.takeRetry() else null
        when {
            retry != null -> {
                Toast.makeText(context, R.string.engine_crash_retry, Toast.LENGTH_SHORT).show()
                // The crashed engine's process usually lingers (no onDestroy
                // after a crash); relaunching into it is exactly what made
                // retries fail with "Android only supports one window".
                GameLauncher.ensureFreshGameProcess(context)
                context.startActivity(retry)
            }
            failure != null -> {
                crashReport = GameProcessExitInfo.report(context)
                engineError = failure.message
                // The dialog is the end of the line for this launch — never
                // fire a surprise relaunch on some later resume.
                LastLaunch.clear()
            }
            else -> {
                // Menu settled cleanly (no failure): nothing may relaunch.
                LastLaunch.clear()
                // A match played to the end left its statistics behind (the
                // ":game" process writes them just before it dies). Reading
                // them consumes them, so this fires exactly once per match.
                MatchRecords.consumeStats(context)?.let { report ->
                    StatsHolder.report = report
                    nav.navigate("stats")
                }
            }
        }
        if (failure != null) {
            // A failed match has nothing to show and nothing worth replaying.
            MatchRecords.sweep(context)
        }
        onPauseOrDispose { }
    }
    engineError?.let { msg ->
        EngineErrorDialog(
            message = msg,
            extraReport = crashReport,
            onDismiss = { engineError = null; crashReport = null },
        )
    }

    // One night-sky backdrop for the whole app; every screen renders on it.
    HedgewarsBackground {
        NavHost(navController = nav, startDestination = "home") {
            composable("home") { HomeScreen(nav) }
            composable("quick") { NewGameScreen(nav, NewGameMode.QUICK) }
            composable("local") { NewGameScreen(nav, NewGameMode.MULTI) }
            composable("missions") { MissionsScreen(nav) }
            composable("teams") { TeamsScreen(nav) }
            composable("teamEdit/{name}") { entry ->
                TeamEditScreen(nav, entry.arguments?.getString("name"))
            }
            composable("teamEdit") { TeamEditScreen(nav, null) }
            composable("schemes") { SchemesScreen(nav) }
            composable("schemeEdit/{name}") { entry ->
                SchemeEditScreen(nav, entry.arguments?.getString("name"))
            }
            composable("schemeEdit") { SchemeEditScreen(nav, null) }
            composable("weaponSets") { WeaponSetsScreen(nav) }
            composable("weaponSetEdit/{name}") { entry ->
                WeaponSetEditScreen(nav, entry.arguments?.getString("name"))
            }
            composable("weaponSetEdit") { WeaponSetEditScreen(nav, null) }
            composable("dlc") { DlcScreen(nav) }
            composable("stats") { StatsScreen(nav) }
            // Registered even while replays are hidden ([Features.REPLAYS]) —
            // navigation-compose turns every route into an implicit deep link,
            // so send an outside caller home instead of showing the screen.
            composable("replays") {
                if (Features.REPLAYS) {
                    ReplaysScreen(nav)
                } else {
                    LaunchedEffect(Unit) { nav.popBackStack("home", inclusive = false) }
                }
            }
            composable("settings") { SettingsScreen(nav) }
            composable("about") { AboutScreen(nav) }
            composable("controls") { ControlsScreen(nav) }
        }
    }
}
