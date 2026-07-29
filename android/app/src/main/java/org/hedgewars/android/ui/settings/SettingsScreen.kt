package org.hedgewars.android.ui.settings

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.audio.MenuMusic
import org.hedgewars.android.data.AppLocale
import org.hedgewars.android.data.UserPrefs
import org.hedgewars.android.ui.common.DropdownPicker
import org.hedgewars.android.ui.common.HwPanel
import org.hedgewars.android.ui.common.HwScreen
import org.hedgewars.android.ui.common.SectionHeader
import org.hedgewars.android.ui.common.ToggleRow

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
    var language by remember { mutableStateOf(prefs.language) }

    // Pref value -> visible label for the in-game UI size choices.
    val uiScaleOptions = listOf(
        "auto" to stringResource(R.string.settings_ui_scale_auto),
        "1.0" to stringResource(R.string.settings_ui_scale_100),
        "1.5" to stringResource(R.string.settings_ui_scale_150),
        "2.0" to stringResource(R.string.settings_ui_scale_200),
        "2.5" to stringResource(R.string.settings_ui_scale_250),
    )
    val languageOptions = listOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to "English",
        "fr" to "Français",
    )

    HwScreen(title = stringResource(R.string.settings_title), onBack = { nav.popBackStack() }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

            SectionHeader(stringResource(R.string.settings_audio))
            HwPanel(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow(stringResource(R.string.settings_sound), sound) {
                        sound = it; prefs.sound = it
                    }
                    ToggleRow(stringResource(R.string.settings_music), music) {
                        music = it; prefs.music = it
                    }
                    ToggleRow(stringResource(R.string.settings_menu_music), menuMusic) {
                        menuMusic = it; prefs.menuMusic = it
                        if (it) MenuMusic.start(context) else MenuMusic.stop()
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_quality))
            HwPanel(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow(stringResource(R.string.settings_quality_low), lowQuality) {
                        lowQuality = it; prefs.lowQuality = it
                    }
                    ToggleRow(stringResource(R.string.settings_show_fps), showFps) {
                        showFps = it; prefs.showFps = it
                    }
                }
            }

            SectionHeader(stringResource(R.string.settings_ui))
            HwPanel(Modifier.fillMaxWidth()) {
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
            }

            SectionHeader(stringResource(R.string.settings_gamepad))
            HwPanel(Modifier.fillMaxWidth()) {
                ToggleRow(stringResource(R.string.settings_gamepad_enabled), gamepad) {
                    gamepad = it; prefs.gamepadBinds = it
                }
            }

            SectionHeader(stringResource(R.string.settings_language))
            HwPanel(Modifier.fillMaxWidth()) {
                DropdownPicker(
                    label = stringResource(R.string.settings_language),
                    options = languageOptions.map { it.second },
                    selected = languageOptions.firstOrNull { it.first == language }?.second
                        ?: languageOptions.first().second,
                ) { label ->
                    val tag = languageOptions.first { it.second == label }.first
                    language = tag
                    AppLocale.set(context, tag)
                    // API 33+ recreates everything itself; below, do it by
                    // hand so the new language shows up immediately.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        (context as? Activity)?.recreate()
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
