package org.hedgewars.android.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R

@Composable
fun HomeScreen(nav: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            // NOT R.mipmap.ic_launcher: on API 26+ that resolves to an
            // AdaptiveIconDrawable, which Compose's painterResource cannot
            // paint (IllegalArgumentException) — use the plain PNG instead.
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
        )
        Text(stringResource(R.string.app_name))

        MenuButton(stringResource(R.string.menu_quick_game)) { nav.navigate("quick") }
        MenuButton(stringResource(R.string.menu_local_game)) { nav.navigate("local") }
        MenuButton(stringResource(R.string.menu_missions)) { nav.navigate("missions") }
        MenuButton(stringResource(R.string.menu_teams)) { nav.navigate("teams") }

        OutlinedButton(onClick = { nav.navigate("settings") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.menu_settings))
        }
        OutlinedButton(onClick = { nav.navigate("controls") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_controls_help))
        }
        OutlinedButton(onClick = { nav.navigate("about") }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.menu_about))
        }
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}
