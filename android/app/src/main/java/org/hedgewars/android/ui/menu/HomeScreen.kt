package org.hedgewars.android.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.Features
import org.hedgewars.android.R
import org.hedgewars.android.ui.common.HwButton

/**
 * Main menu, styled after the desktop frontend: the Hedgewars title over the
 * night-sky backdrop, four big gold action buttons and three secondary ones.
 */
@Composable
fun HomeScreen(nav: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.hedgewars_title),
            contentDescription = stringResource(R.string.app_name),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .padding(bottom = 8.dp),
        )

        val col = Modifier.fillMaxWidth().widthIn(max = 460.dp)
        HwButton(stringResource(R.string.menu_quick_game), { nav.navigate("quick") }, col)
        HwButton(stringResource(R.string.menu_local_game), { nav.navigate("local") }, col)
        HwButton(stringResource(R.string.menu_missions), { nav.navigate("missions") }, col)
        HwButton(stringResource(R.string.menu_teams), { nav.navigate("teams") }, col)
        HwButton(stringResource(R.string.menu_dlc), { nav.navigate("dlc") }, col)

        if (Features.REPLAYS) {
            HwButton(stringResource(R.string.menu_replays), { nav.navigate("replays") }, col, primary = false)
        }
        HwButton(stringResource(R.string.menu_settings), { nav.navigate("settings") }, col, primary = false)
        HwButton(stringResource(R.string.settings_controls_help), { nav.navigate("controls") }, col, primary = false)
        HwButton(stringResource(R.string.menu_about), { nav.navigate("about") }, col, primary = false)
    }
}
