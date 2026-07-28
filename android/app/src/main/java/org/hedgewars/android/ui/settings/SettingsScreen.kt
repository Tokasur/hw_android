package org.hedgewars.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.audio.MenuMusic
import org.hedgewars.android.data.UserPrefs
import org.hedgewars.android.ui.common.DropdownPicker

@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val prefs = remember { UserPrefs(context) }

    var sound by remember { mutableStateOf(prefs.sound) }
    var music by remember { mutableStateOf(prefs.music) }
    var menuMusic by remember { mutableStateOf(prefs.menuMusic) }
    var lowQuality by remember { mutableStateOf(prefs.lowQuality) }
    var showFps by remember { mutableStateOf(prefs.showFps) }
    var gamepad by remember { mutableStateOf(prefs.gamepadBinds) }
    var uiScale by remember { mutableStateOf(prefs.uiScale) }

    // Pref value -> visible label for the in-game UI size choices.
    val uiScaleOptions = listOf(
        "auto" to stringResource(R.string.settings_ui_scale_auto),
        "1.0" to stringResource(R.string.settings_ui_scale_100),
        "1.5" to stringResource(R.string.settings_ui_scale_150),
        "2.0" to stringResource(R.string.settings_ui_scale_200),
        "2.5" to stringResource(R.string.settings_ui_scale_250),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        Text(stringResource(R.string.settings_audio), style = MaterialTheme.typography.titleMedium)
        ToggleRow(stringResource(R.string.settings_sound), sound) { sound = it; prefs.sound = it }
        ToggleRow(stringResource(R.string.settings_music), music) { music = it; prefs.music = it }
        ToggleRow(stringResource(R.string.settings_menu_music), menuMusic) {
            menuMusic = it; prefs.menuMusic = it
            if (it) MenuMusic.start(context) else MenuMusic.stop()
        }

        Text(stringResource(R.string.settings_quality), style = MaterialTheme.typography.titleMedium)
        ToggleRow(stringResource(R.string.settings_quality_low), lowQuality) {
            lowQuality = it; prefs.lowQuality = it
        }
        ToggleRow(stringResource(R.string.settings_show_fps), showFps) {
            showFps = it; prefs.showFps = it
        }

        Text(stringResource(R.string.settings_ui), style = MaterialTheme.typography.titleMedium)
        DropdownPicker(
            label = stringResource(R.string.settings_ui_scale),
            options = uiScaleOptions.map { it.second },
            selected = uiScaleOptions.firstOrNull { it.first == uiScale }?.second
                ?: uiScaleOptions.first().second,
        ) { label ->
            val value = uiScaleOptions.first { it.second == label }.first
            uiScale = value
            prefs.uiScale = value
        }

        Text(stringResource(R.string.settings_gamepad), style = MaterialTheme.typography.titleMedium)
        ToggleRow(stringResource(R.string.settings_gamepad_enabled), gamepad) {
            gamepad = it; prefs.gamepadBinds = it
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
