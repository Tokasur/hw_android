package org.hedgewars.android.ui.newgame

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.config.GameConfig
import org.hedgewars.android.config.MapChoice
import org.hedgewars.android.config.Scheme
import org.hedgewars.android.config.Team
import org.hedgewars.android.config.TeamSlot
import org.hedgewars.android.config.WeaponSet
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.MissionsRepository
import org.hedgewars.android.data.SchemesRepository
import org.hedgewars.android.data.TeamsRepository
import org.hedgewars.android.data.WeaponSetsRepository
import org.hedgewars.android.game.GameLauncher
import org.hedgewars.android.ui.common.DifficultyBadge
import org.hedgewars.android.ui.common.DropdownPicker
import org.hedgewars.android.ui.common.HwButton
import org.hedgewars.android.ui.common.HwChip
import org.hedgewars.android.ui.common.HwPanel
import org.hedgewars.android.ui.common.HwScreen
import org.hedgewars.android.ui.common.SectionHeader
import org.hedgewars.android.ui.theme.HwColors

enum class NewGameMode { QUICK, MULTI }

/**
 * Unified "new game" setup shared by Quick game and Local multiplayer.
 *
 * Both build the same [GameConfig]; the only difference is team management —
 * Quick game pits the player against a single CPU (difficulty picker only),
 * while Local multiplayer manages 2-8 human/CPU teams. Map, theme, scheme and
 * weapons are configured identically in both.
 */
@Composable
fun NewGameScreen(nav: NavController, mode: NewGameMode) {
    val context = LocalContext.current
    val paths = remember { GamePaths(context) }
    val teamsRepo = remember { TeamsRepository(context) }
    val schemesRepo = remember { SchemesRepository(context) }
    val weaponsRepo = remember { WeaponSetsRepository(context) }
    val missions = remember { MissionsRepository(paths) }
    val launcher = remember { GameLauncher(context) }

    val allTeams = remember { teamsRepo.list() }
    val themes = remember { missions.themes().ifEmpty { listOf("Nature") } }
    val namedMaps = remember { missions.multiplayerMaps() }

    // Shared map/theme/rules state.
    var mapKind by remember { mutableStateOf(MapKind.RANDOM) }
    var selectedMap by remember { mutableStateOf(namedMaps.firstOrNull()) }
    var featureSize by remember { mutableStateOf(50f) }
    var seed by remember { mutableStateOf(GameConfig.newSeed()) }
    var theme by remember { mutableStateOf(if ("Nature" in themes) "Nature" else themes.first()) }
    var schemeName by rememberSaveable { mutableStateOf(Scheme.DEFAULT.name) }
    var weaponsName by rememberSaveable { mutableStateOf(WeaponSet.DEFAULT.name) }
    var hogCount by remember { mutableStateOf(4f) }

    // Custom schemes/weapon sets can appear or vanish while we're away in
    // their editors; reload on every resume and snap dead selections back.
    var schemeNames by remember { mutableStateOf(Scheme.PRESETS.map { it.name }) }
    var weaponNames by remember { mutableStateOf(WeaponSet.PRESETS.map { it.name }) }
    LifecycleResumeEffect(Unit) {
        schemeNames = schemesRepo.all().map { it.name }
        weaponNames = weaponsRepo.all().map { it.name }
        if (schemeName !in schemeNames) schemeName = Scheme.DEFAULT.name
        if (weaponsName !in weaponNames) weaponsName = WeaponSet.DEFAULT.name
        onPauseOrDispose { }
    }

    // Quick game: single AI difficulty. Multiplayer: a live list of slots.
    var aiDifficulty by remember { mutableStateOf(3) }
    val slots = remember {
        mutableStateListOf<TeamSlot>().apply {
            if (mode == NewGameMode.MULTI) {
                allTeams.take(2).forEachIndexed { i, t -> add(TeamSlot(t, colorIndex = i)) }
                while (size < 2) {
                    val i = size
                    add(TeamSlot(Team.default("Team ${i + 1}", "Hog", difficulty = if (i == 0) 0 else 3), colorIndex = i))
                }
            }
        }
    }

    val title = if (mode == NewGameMode.QUICK) "Quick game" else "Local multiplayer"

    HwScreen(title = title, onBack = { nav.popBackStack() }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

            SectionHeader("Teams")
            HwPanel(Modifier.fillMaxWidth()) {
                if (mode == NewGameMode.QUICK) {
                    QuickTeams(
                        playerName = allTeams.firstOrNull { !it.isCpu }?.name ?: "Player",
                        aiDifficulty = aiDifficulty,
                        onDifficulty = { aiDifficulty = it },
                    )
                } else {
                    MultiTeams(slots)
                }
            }

            SectionHeader("Map")
            HwPanel(Modifier.fillMaxWidth()) {
                MapPicker(
                    dataDir = paths.dataDir,
                    kind = mapKind, onKind = { mapKind = it },
                    namedMaps = namedMaps, selectedMap = selectedMap, onSelectMap = { selectedMap = it },
                    featureSize = featureSize, onFeatureSize = { featureSize = it },
                    seed = seed, onReseed = { seed = GameConfig.newSeed() },
                )
            }

            SectionHeader("Theme")
            HwPanel(Modifier.fillMaxWidth()) {
                ThemePicker(paths.dataDir, themes, theme) { theme = it }
            }

            SectionHeader(stringResource(R.string.local_game_scheme))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownPicker("", schemeNames, schemeName, Modifier.weight(1f)) { schemeName = it }
                HwChip("✎", selected = false, onClick = { nav.navigate("schemes") })
            }

            SectionHeader(stringResource(R.string.local_game_weapons))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownPicker("", weaponNames, weaponsName, Modifier.weight(1f)) { weaponsName = it }
                HwChip("✎", selected = false, onClick = { nav.navigate("weaponSets") })
            }

            SectionHeader("Hedgehogs per team: ${hogCount.toInt()}")
            Slider(value = hogCount, onValueChange = { hogCount = it }, valueRange = 1f..8f, steps = 6)

            Spacer(Modifier.height(8.dp))
            HwButton(if (mode == NewGameMode.QUICK) "Fight!" else "Start", onClick = {
                val map: MapChoice = if (mapKind == MapKind.NAMED) {
                    selectedMap?.let { MapChoice.Named(it) } ?: MapChoice.Generated()
                } else {
                    MapChoice.Generated(mapGen = mapKind.toGen(), featureSize = featureSize.toInt())
                }
                val teams: List<TeamSlot> = when (mode) {
                    NewGameMode.QUICK -> {
                        val player = allTeams.firstOrNull { !it.isCpu } ?: allTeams.firstOrNull()
                        if (player == null) {
                            Toast.makeText(context, "Create a team first", Toast.LENGTH_LONG).show()
                            return@HwButton
                        }
                        val cpu = allTeams.firstOrNull { it.name != player.name }
                            ?: Team.default("Robots", "Unit")
                        listOf(
                            TeamSlot(player.copy(difficulty = 0), colorIndex = 0, hogCount = hogCount.toInt()),
                            TeamSlot(cpu.copy(difficulty = aiDifficulty), colorIndex = 1, hogCount = hogCount.toInt()),
                        )
                    }
                    NewGameMode.MULTI -> {
                        if (slots.size < 2 || slots.map { it.colorIndex }.toSet().size < 2) {
                            Toast.makeText(context, "Need at least two teams with different colors", Toast.LENGTH_LONG).show()
                            return@HwButton
                        }
                        slots.map { it.copy(hogCount = hogCount.toInt()) }
                    }
                }
                launcher.launchLocalGame(
                    GameConfig(
                        teams = teams,
                        scheme = schemesRepo.resolve(schemeName),
                        weapons = weaponsRepo.resolve(weaponsName),
                        map = map,
                        theme = theme,
                        seed = seed,
                    )
                )
            })
        }
    }
}

@Composable
private fun QuickTeams(playerName: String, aiDifficulty: Int, onDifficulty: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(0)
            Text("  $playerName", color = HwColors.TextLight, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            DifficultyBadge(0)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(1)
            Text("  Robots (CPU)", color = HwColors.TextLight, style = MaterialTheme.typography.titleMedium)
        }
        Text("AI difficulty", color = HwColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (level in 1..5) {
                HwChip("★".repeat(level), aiDifficulty == level) { onDifficulty(level) }
            }
        }
    }
}

@Composable
private fun MultiTeams(slots: androidx.compose.runtime.snapshots.SnapshotStateList<TeamSlot>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        slots.forEachIndexed { index, slot ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.clip(CircleShape).clickable {
                    slots[index] = slot.copy(colorIndex = (slot.colorIndex + 1) % Team.COLORS.size)
                }) { ColorDot(slot.colorIndex) }
                Text(slot.team.name, color = HwColors.TextLight,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                HwChip(
                    if (slot.team.difficulty == 0) "👤" else "★".repeat(slot.team.difficulty),
                    selected = false,
                    onClick = {
                        val d = (slot.team.difficulty + 1) % 6
                        slots[index] = slot.copy(team = slot.team.copy(difficulty = d))
                    },
                )
                if (slots.size > 2) {
                    HwChip("✕", selected = false, onClick = { slots.removeAt(index) })
                }
            }
        }
        if (slots.size < 8) {
            HwChip("+ Add team", selected = false, onClick = {
                val usedColors = slots.map { it.colorIndex }.toSet()
                val color = (0 until Team.COLORS.size).firstOrNull { it !in usedColors } ?: 0
                slots.add(TeamSlot(Team.default("Team ${slots.size + 1}", "Hog", difficulty = 3), colorIndex = color))
            })
        }
    }
}

@Composable
private fun ColorDot(colorIndex: Int) {
    Spacer(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color(0xFF000000 or Team.COLORS[colorIndex % Team.COLORS.size].toLong()))
    )
}
