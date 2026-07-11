package org.hedgewars.android

import android.os.Bundle
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
    val context = LocalContext.current
    var engineError by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        engineError = EngineOutcome.consumeError(context)
        onPauseOrDispose { }
    }
    engineError?.let { msg ->
        EngineErrorDialog(message = msg, onDismiss = { engineError = null })
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
