package org.hedgewars.android.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.ui.common.HwPanel
import org.hedgewars.android.ui.common.HwScreen
import org.hedgewars.android.ui.common.SectionHeader
import org.hedgewars.android.ui.common.safeBack
import org.hedgewars.android.ui.theme.HwColors

/**
 * Controls reference, both touch and gamepad. Rows read "what you do" ->
 * "what happens", so the jump moves (the ones players actually miss) are
 * visible at a glance instead of buried in a paragraph.
 */
@Composable
fun ControlsScreen(nav: NavController) {
    HwScreen(title = stringResource(R.string.controls_title), onBack = { nav.safeBack() }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

            SectionHeader(stringResource(R.string.controls_touch_title))
            HwPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlRow(R.string.controls_touch_move_in, R.string.controls_touch_move_out)
                    ControlRow(R.string.controls_touch_aim_in, R.string.controls_touch_aim_out)
                    ControlRow(R.string.controls_touch_fire_in, R.string.controls_touch_fire_out)
                    ControlRow(R.string.controls_touch_ammo_in, R.string.controls_touch_ammo_out)
                    ControlRow(R.string.controls_touch_target_in, R.string.controls_touch_target_out)
                    ControlRow(R.string.controls_touch_camera_in, R.string.controls_touch_camera_out)
                    ControlRow(R.string.controls_touch_pause_in, R.string.controls_touch_pause_out)
                }
            }

            SectionHeader(stringResource(R.string.controls_jump_title))
            HwPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.controls_jump_intro),
                        color = HwColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    ControlRow(R.string.controls_jump_short_in, R.string.controls_jump_short_out)
                    ControlRow(R.string.controls_jump_double_in, R.string.controls_jump_double_out)
                    ControlRow(R.string.controls_jump_long_in, R.string.controls_jump_long_out)
                }
            }

            SectionHeader(stringResource(R.string.controls_gamepad_title))
            HwPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlRow(R.string.controls_pad_move_in, R.string.controls_pad_move_out)
                    ControlRow(R.string.controls_pad_precise_in, R.string.controls_pad_precise_out)
                    ControlRow(R.string.controls_pad_camera_in, R.string.controls_pad_camera_out)
                    ControlRow(R.string.controls_pad_fire_in, R.string.controls_pad_fire_out)
                    ControlRow(R.string.controls_pad_jump_in, R.string.controls_pad_jump_out)
                    ControlRow(R.string.controls_pad_ljump_in, R.string.controls_pad_ljump_out)
                    ControlRow(R.string.controls_pad_ammo_in, R.string.controls_pad_ammo_out)
                    ControlRow(R.string.controls_pad_pause_in, R.string.controls_pad_pause_out)
                    ControlRow(R.string.controls_pad_quit_in, R.string.controls_pad_quit_out)
                }
            }

            SectionHeader(stringResource(R.string.controls_ammo_menu_title))
            HwPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlRow(R.string.controls_menu_browse_in, R.string.controls_menu_browse_out)
                    ControlRow(R.string.controls_menu_pick_in, R.string.controls_menu_pick_out)
                    ControlRow(R.string.controls_menu_close_in, R.string.controls_menu_close_out)
                }
            }

            SectionHeader(stringResource(R.string.controls_weapon_title))
            HwPanel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ControlRow(R.string.controls_weapon_timer_in, R.string.controls_weapon_timer_out)
                    ControlRow(R.string.controls_weapon_bounce_in, R.string.controls_weapon_bounce_out)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One "input -> effect" line; stacks naturally on narrow screens. */
@Composable
private fun ControlRow(input: Int, effect: Int) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            stringResource(input),
            color = HwColors.Gold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = 108.dp).weight(0.42f).padding(end = 10.dp),
        )
        Text(
            stringResource(effect),
            color = HwColors.TextLight,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.58f),
        )
    }
}
