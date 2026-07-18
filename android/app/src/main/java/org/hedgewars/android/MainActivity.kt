package org.hedgewars.android

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.hedgewars.android.engine.EngineOutcome
import org.hedgewars.android.engine.GameProcessExitInfo
import org.hedgewars.android.game.LastLaunch
import org.hedgewars.android.ui.error.EngineErrorDialog
import org.hedgewars.android.ui.about.AboutScreen
import org.hedgewars.android.ui.about.ControlsScreen
import org.hedgewars.android.ui.install.InstallGate
import org.hedgewars.android.ui.menu.HomeScreen
import org.hedgewars.android.ui.missions.MissionsScreen
import org.hedgewars.android.ui.newgame.NewGameMode
import org.hedgewars.android.ui.newgame.NewGameScreen
import org.hedgewars.android.ui.settings.SettingsScreen
import org.hedgewars.android.ui.teams.TeamEditScreen
import org.hedgewars.android.ui.teams.TeamsScreen
import org.hedgewars.android.ui.theme.HedgewarsBackground
import org.hedgewars.android.ui.theme.HedgewarsTheme

class MainActivity : ComponentActivity() {
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
    var engineError by remember { mutableStateOf<String?>(null) }
    var crashReport by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        val failure = EngineOutcome.consumeError(context)
        val retry = if (failure != null && failure.hardCrash) LastLaunch.takeRetry() else null
        when {
            retry != null -> {
                Toast.makeText(context, R.string.engine_crash_retry, Toast.LENGTH_SHORT).show()
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
            }
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
            composable("settings") { SettingsScreen(nav) }
            composable("about") { AboutScreen(nav) }
            composable("controls") { ControlsScreen(nav) }
        }
    }
}
