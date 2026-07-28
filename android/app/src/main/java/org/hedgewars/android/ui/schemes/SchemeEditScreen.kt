package org.hedgewars.android.ui.schemes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.config.Scheme
import org.hedgewars.android.config.SchemeRanges
import org.hedgewars.android.data.NameError
import org.hedgewars.android.data.SchemesRepository
import org.hedgewars.android.data.sanitizeCustomName
import org.hedgewars.android.ui.common.DropdownPicker
import org.hedgewars.android.ui.common.HwButton
import org.hedgewars.android.ui.common.HwPanel
import org.hedgewars.android.ui.common.HwScreen
import org.hedgewars.android.ui.common.NumberStepperRow
import org.hedgewars.android.ui.common.SectionHeader
import org.hedgewars.android.ui.common.ToggleRow
import org.hedgewars.android.ui.common.jsonSaver

/** One boolean rule of the scheme: its label and its field accessors. */
private class Mod(
    val label: Int,
    val get: (Scheme) -> Boolean,
    val set: (Scheme, Boolean) -> Scheme,
)

/** In [Scheme] field order (which is also the desktop checkbox order). */
private val MODS = listOf(
    Mod(R.string.scheme_mod_divide_teams, { it.divideTeams }, { s, v -> s.copy(divideTeams = v) }),
    Mod(R.string.scheme_mod_solid_land, { it.solidLand }, { s, v -> s.copy(solidLand = v) }),
    Mod(R.string.scheme_mod_border, { it.border }, { s, v -> s.copy(border = v) }),
    Mod(R.string.scheme_mod_low_gravity, { it.lowGravity }, { s, v -> s.copy(lowGravity = v) }),
    Mod(R.string.scheme_mod_laser_sight, { it.laserSight }, { s, v -> s.copy(laserSight = v) }),
    Mod(R.string.scheme_mod_invulnerable, { it.invulnerable }, { s, v -> s.copy(invulnerable = v) }),
    Mod(R.string.scheme_mod_reset_health, { it.resetHealth }, { s, v -> s.copy(resetHealth = v) }),
    Mod(R.string.scheme_mod_vampirism, { it.vampirism }, { s, v -> s.copy(vampirism = v) }),
    Mod(R.string.scheme_mod_karma, { it.karma }, { s, v -> s.copy(karma = v) }),
    Mod(R.string.scheme_mod_artillery, { it.artillery }, { s, v -> s.copy(artillery = v) }),
    Mod(R.string.scheme_mod_random_order, { it.randomOrder }, { s, v -> s.copy(randomOrder = v) }),
    Mod(R.string.scheme_mod_king, { it.king }, { s, v -> s.copy(king = v) }),
    Mod(R.string.scheme_mod_place_hogs, { it.placeHogs }, { s, v -> s.copy(placeHogs = v) }),
    Mod(R.string.scheme_mod_shared_ammo, { it.sharedAmmo }, { s, v -> s.copy(sharedAmmo = v) }),
    Mod(R.string.scheme_mod_disable_girders, { it.disableGirders }, { s, v -> s.copy(disableGirders = v) }),
    Mod(R.string.scheme_mod_disable_land_objects, { it.disableLandObjects }, { s, v -> s.copy(disableLandObjects = v) }),
    Mod(R.string.scheme_mod_ai_survival, { it.aiSurvival }, { s, v -> s.copy(aiSurvival = v) }),
    Mod(R.string.scheme_mod_infinite_attacks, { it.infiniteAttacks }, { s, v -> s.copy(infiniteAttacks = v) }),
    Mod(R.string.scheme_mod_reset_weapons, { it.resetWeapons }, { s, v -> s.copy(resetWeapons = v) }),
    Mod(R.string.scheme_mod_per_hog_ammo, { it.perHogAmmo }, { s, v -> s.copy(perHogAmmo = v) }),
    Mod(R.string.scheme_mod_no_wind, { it.noWind }, { s, v -> s.copy(noWind = v) }),
    Mod(R.string.scheme_mod_more_wind, { it.moreWind }, { s, v -> s.copy(moreWind = v) }),
    Mod(R.string.scheme_mod_tag_team, { it.tagTeam }, { s, v -> s.copy(tagTeam = v) }),
    Mod(R.string.scheme_mod_bottom_border, { it.bottomBorder }, { s, v -> s.copy(bottomBorder = v) }),
    Mod(R.string.scheme_mod_switch_hog, { it.switchHog }, { s, v -> s.copy(switchHog = v) }),
)

/** One numeric setting: its label, range, magic-value labels and accessors. */
private class Num(
    val label: Int,
    val range: IntRange,
    val get: (Scheme) -> Int,
    val set: (Scheme, Int) -> Scheme,
    val specials: Map<Int, Int> = emptyMap(), // value -> label resource
)

/** In [Scheme] field order (also the desktop basic-settings order). */
private val NUMS = listOf(
    Num(R.string.scheme_num_damage, SchemeRanges.damagePercent, { it.damagePercent }, { s, v -> s.copy(damagePercent = v) }),
    Num(R.string.scheme_num_turn_time, SchemeRanges.turnTimeSec, { it.turnTimeSec }, { s, v -> s.copy(turnTimeSec = v) }),
    Num(R.string.scheme_num_init_health, SchemeRanges.initHealth, { it.initHealth }, { s, v -> s.copy(initHealth = v) }),
    Num(R.string.scheme_num_sd_turns, SchemeRanges.suddenDeathTurns, { it.suddenDeathTurns }, { s, v -> s.copy(suddenDeathTurns = v) }),
    Num(
        R.string.scheme_num_case_freq, SchemeRanges.caseFreq, { it.caseFreq }, { s, v -> s.copy(caseFreq = v) },
        specials = mapOf(0 to R.string.scheme_never),
    ),
    Num(
        R.string.scheme_num_mines_time, SchemeRanges.minesTimeSec, { it.minesTimeSec }, { s, v -> s.copy(minesTimeSec = v) },
        specials = mapOf(-1 to R.string.scheme_random),
    ),
    Num(R.string.scheme_num_mines_num, SchemeRanges.minesNum, { it.minesNum }, { s, v -> s.copy(minesNum = v) }),
    Num(R.string.scheme_num_dud_pct, SchemeRanges.mineDudPercent, { it.mineDudPercent }, { s, v -> s.copy(mineDudPercent = v) }),
    Num(R.string.scheme_num_explosives, SchemeRanges.explosives, { it.explosives }, { s, v -> s.copy(explosives = v) }),
    Num(R.string.scheme_num_air_mines, SchemeRanges.airMines, { it.airMines }, { s, v -> s.copy(airMines = v) }),
    Num(R.string.scheme_num_sentries, SchemeRanges.sentries, { it.sentries }, { s, v -> s.copy(sentries = v) }),
    Num(R.string.scheme_num_health_prob, SchemeRanges.healthCaseProb, { it.healthCaseProb }, { s, v -> s.copy(healthCaseProb = v) }),
    Num(R.string.scheme_num_health_amount, SchemeRanges.healthCaseAmount, { it.healthCaseAmount }, { s, v -> s.copy(healthCaseAmount = v) }),
    Num(R.string.scheme_num_water_rise, SchemeRanges.waterRise, { it.waterRise }, { s, v -> s.copy(waterRise = v) }),
    Num(R.string.scheme_num_health_dec, SchemeRanges.healthDecrease, { it.healthDecrease }, { s, v -> s.copy(healthDecrease = v) }),
    Num(R.string.scheme_num_rope_pct, SchemeRanges.ropePercent, { it.ropePercent }, { s, v -> s.copy(ropePercent = v) }),
    Num(R.string.scheme_num_get_away, SchemeRanges.getAwayTime, { it.getAwayTime }, { s, v -> s.copy(getAwayTime = v) }),
)

@Composable
fun SchemeEditScreen(nav: NavController, schemeName: String?) {
    val context = LocalContext.current
    val repo = remember { SchemesRepository(context) }
    val original = remember { schemeName?.let { repo.get(it) } }

    // Asked to edit something that no longer exists: just leave.
    if (schemeName != null && original == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    var scheme by rememberSaveable(stateSaver = jsonSaver<Scheme>()) {
        mutableStateOf(original ?: Scheme.DEFAULT.copy(name = ""))
    }
    val nameError = repo.nameError(scheme.name, original?.name)

    val edgeNames = listOf(
        stringResource(R.string.scheme_edge_none),
        stringResource(R.string.scheme_edge_wrap),
        stringResource(R.string.scheme_edge_bounce),
        stringResource(R.string.scheme_edge_sea),
    )

    HwScreen(
        title = stringResource(if (original == null) R.string.schemes_new else R.string.schemes_edit),
        onBack = { nav.popBackStack() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = scheme.name,
                onValueChange = { scheme = scheme.copy(name = sanitizeCustomName(it)) },
                label = { Text(stringResource(R.string.scheme_name)) },
                isError = nameError != null,
                supportingText = {
                    when (nameError) {
                        NameError.EMPTY -> Text(stringResource(R.string.name_error_empty))
                        NameError.TAKEN -> Text(stringResource(R.string.name_error_taken))
                        null -> {}
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader(stringResource(R.string.scheme_basic))
            HwPanel(Modifier.fillMaxWidth()) {
                NUMS.forEach { n ->
                    NumberStepperRow(
                        label = stringResource(n.label),
                        value = n.get(scheme),
                        range = n.range,
                        specialLabels = n.specials.mapValues { stringResource(it.value) },
                    ) { v -> scheme = n.set(scheme, v) }
                }
                Spacer(Modifier.height(8.dp))
                DropdownPicker(
                    stringResource(R.string.scheme_world_edge),
                    edgeNames,
                    edgeNames[scheme.worldEdge.coerceIn(SchemeRanges.worldEdge)],
                ) { scheme = scheme.copy(worldEdge = edgeNames.indexOf(it)) }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = scheme.scriptParam,
                    onValueChange = { scheme = scheme.copy(scriptParam = it.take(SchemeRanges.SCRIPT_PARAM_MAX)) },
                    label = { Text(stringResource(R.string.scheme_script_param)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader(stringResource(R.string.scheme_modifiers))
            HwPanel(Modifier.fillMaxWidth()) {
                MODS.forEach { m ->
                    ToggleRow(stringResource(m.label), m.get(scheme)) { v -> scheme = m.set(scheme, v) }
                }
            }

            Spacer(Modifier.height(4.dp))
            HwButton(
                stringResource(R.string.common_save),
                enabled = nameError == null,
                onClick = {
                    val toSave = scheme.copy(name = scheme.name.trim())
                    if (original != null && original.name != toSave.name) repo.delete(original.name)
                    repo.save(toSave)
                    nav.popBackStack()
                },
            )
            if (original != null) {
                HwButton(
                    stringResource(R.string.common_delete),
                    primary = false,
                    onClick = {
                        repo.delete(original.name)
                        nav.popBackStack()
                    },
                )
            }
        }
    }
}
