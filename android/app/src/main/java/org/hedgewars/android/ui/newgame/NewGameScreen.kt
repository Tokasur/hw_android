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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
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
import org.hedgewars.android.data.PackContentIndex
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
import org.hedgewars.android.ui.common.safeBack
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
    val packIndex = remember { PackContentIndex(paths.userDataDir) }
    val launcher = remember { GameLauncher(context) }

    var allTeams by remember { mutableStateOf(teamsRepo.list()) }
    val themes = remember { missions.themes().ifEmpty { listOf("Nature") } }
    val namedMaps = remember { missions.multiplayerMaps() }

    // Shared map/theme/rules state.
    var mapKind by remember { mutableStateOf(MapKind.RANDOM) }
    var selectedMap by remember { mutableStateOf(namedMaps.firstOrNull()) }
    var featureSize by remember { mutableStateOf(50f) }
    // Forts reuse e$feature_size as the gap between forts; the desktop keeps a
    // separate 1..25 slider for it, so we keep a separate value too.
    var fortDistance by remember { mutableStateOf(12f) }
    var seed by remember { mutableStateOf(GameConfig.newSeed()) }
    var theme by remember { mutableStateOf(if ("Nature" in themes) "Nature" else themes.first()) }
    var schemeName by rememberSaveable { mutableStateOf(Scheme.DEFAULT.name) }
    var weaponsName by rememberSaveable { mutableStateOf(WeaponSet.DEFAULT.name) }
    // Game style (Scripts/Multiplayer): "" = none. Unknown names (a deleted
    // pack) simply resolve to no script at launch.
    val styles = remember { missions.gameStyles() }
    var styleName by rememberSaveable { mutableStateOf("") }
    // A style's .cfg can pin the scheme/weapons (desktop rule): apply the
    // recommendation when the style changes and lock the pickers, so
    // combos like Racer + King Mode can't break the match.
    val selectedStyle = styles.firstOrNull { it.title == styleName }
    LaunchedEffect(styleName) {
        val s = selectedStyle ?: return@LaunchedEffect
        s.scheme?.let {
            schemeName = if (it == MissionsRepository.GameStyle.LOCKED) Scheme.DEFAULT.name else it
        }
        s.weapons?.let {
            weaponsName = if (it == MissionsRepository.GameStyle.LOCKED) WeaponSet.DEFAULT.name else it
        }
    }
    var hogCount by remember { mutableStateOf(4f) }

    // Quick game: the player's team, its opponent, and one AI level.
    var aiDifficulty by remember { mutableStateOf(3) }
    var playerTeam by remember {
        mutableStateOf(allTeams.firstOrNull { !it.isCpu } ?: allTeams.firstOrNull())
    }
    var cpuTeam by remember {
        mutableStateOf(allTeams.firstOrNull { it.name != playerTeam?.name })
    }
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

    // Teams, schemes and weapon sets can be created, edited or deleted while
    // we're away in their editors; reload on every resume and snap dead
    // selections back.
    var schemeNames by remember { mutableStateOf(Scheme.PRESETS.map { it.name }) }
    var weaponNames by remember { mutableStateOf(WeaponSet.PRESETS.map { it.name }) }
    LifecycleResumeEffect(Unit) {
        schemeNames = schemesRepo.all().map { it.name }
        weaponNames = weaponsRepo.all().map { it.name }
        if (schemeName !in schemeNames) schemeName = Scheme.DEFAULT.name
        if (weaponsName !in weaponNames) weaponsName = WeaponSet.DEFAULT.name
        allTeams = teamsRepo.list()
        // Pick up edits to a team already in play (hats, hogs, fort…) without
        // losing the human/CPU choice made for its slot here.
        slots.forEachIndexed { i, slot ->
            val fresh = allTeams.firstOrNull { it.name == slot.team.name } ?: return@forEachIndexed
            slots[i] = slot.copy(team = fresh.copy(difficulty = slot.team.difficulty))
        }
        // Same for the quick game's two teams, and fall back to something that
        // still exists if the chosen one was deleted meanwhile.
        playerTeam = allTeams.firstOrNull { it.name == playerTeam?.name }
            ?: allTeams.firstOrNull { !it.isCpu } ?: allTeams.firstOrNull()
        cpuTeam = allTeams.firstOrNull { it.name == cpuTeam?.name }
            ?: allTeams.firstOrNull { it.name != playerTeam?.name }
        onPauseOrDispose { }
    }

    val title = stringResource(
        if (mode == NewGameMode.QUICK) R.string.quick_game_title else R.string.local_game_title
    )

    HwScreen(title = title, onBack = { nav.safeBack() }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

            SectionHeader(stringResource(R.string.menu_teams))
            HwPanel(Modifier.fillMaxWidth()) {
                if (mode == NewGameMode.QUICK) {
                    QuickTeams(
                        playerName = playerTeam?.name ?: "Player",
                        cpuName = cpuTeam?.name ?: "Robots",
                        allTeams = allTeams,
                        // Picking the other side's team simply trades the two,
                        // as in local multiplayer.
                        onPlayerTeam = { picked ->
                            if (picked.name == cpuTeam?.name) cpuTeam = playerTeam
                            playerTeam = picked
                        },
                        onCpuTeam = { picked ->
                            if (picked.name == playerTeam?.name) playerTeam = cpuTeam
                            cpuTeam = picked
                        },
                        aiDifficulty = aiDifficulty,
                        onDifficulty = { aiDifficulty = it },
                    )
                } else {
                    MultiTeams(slots, allTeams)
                }
            }

            SectionHeader(stringResource(R.string.local_game_map))
            HwPanel(Modifier.fillMaxWidth()) {
                MapPicker(
                    dataDir = paths.dataDir,
                    packIndex = packIndex,
                    kind = mapKind, onKind = { mapKind = it },
                    namedMaps = namedMaps, selectedMap = selectedMap, onSelectMap = { selectedMap = it },
                    featureSize = featureSize, onFeatureSize = { featureSize = it },
                    fortDistance = fortDistance, onFortDistance = { fortDistance = it },
                    seed = seed, onReseed = { seed = GameConfig.newSeed() },
                )
            }

            SectionHeader(stringResource(R.string.local_game_theme))
            HwPanel(Modifier.fillMaxWidth()) {
                ThemePicker(paths.dataDir, packIndex, themes, theme) { theme = it }
            }

            SectionHeader(stringResource(R.string.local_game_scheme))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownPicker(
                    "", schemeNames, schemeName, Modifier.weight(1f),
                    enabled = selectedStyle?.scheme == null,
                ) { schemeName = it }
                HwChip("✎", selected = false, onClick = { nav.navigate("schemes") })
            }

            SectionHeader(stringResource(R.string.local_game_weapons))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DropdownPicker(
                    "", weaponNames, weaponsName, Modifier.weight(1f),
                    enabled = selectedStyle?.weapons == null,
                ) { weaponsName = it }
                HwChip("✎", selected = false, onClick = { nav.navigate("weaponSets") })
            }

            SectionHeader(stringResource(R.string.local_game_style))
            val styleNone = stringResource(R.string.local_game_style_none)
            DropdownPicker(
                "",
                listOf(styleNone) + styles.map { it.title },
                styleName.ifEmpty { styleNone },
            ) { picked -> styleName = if (picked == styleNone) "" else picked }

            SectionHeader(
                stringResource(R.string.local_game_hogs_per_team) + ": ${hogCount.toInt()}"
            )
            Slider(value = hogCount, onValueChange = { hogCount = it }, valueRange = 1f..8f, steps = 6)

            Spacer(Modifier.height(8.dp))
            val startLabel = stringResource(
                if (mode == NewGameMode.QUICK) R.string.quick_game_start else R.string.local_game_start
            )
            HwButton(startLabel, onClick = {
                val map: MapChoice = if (mapKind == MapKind.NAMED) {
                    selectedMap?.let { MapChoice.Named(it) } ?: MapChoice.Generated()
                } else {
                    MapChoice.Generated(
                        mapGen = mapKind.toGen(),
                        featureSize = if (mapKind == MapKind.FORTS) fortDistance.toInt() else featureSize.toInt(),
                    )
                }
                val teams: List<TeamSlot> = when (mode) {
                    NewGameMode.QUICK -> {
                        val player = playerTeam
                        if (player == null) {
                            Toast.makeText(context, R.string.local_game_need_one_team, Toast.LENGTH_LONG).show()
                            return@HwButton
                        }
                        val cpu = cpuTeam ?: Team.default("Robots", "Unit")
                        listOf(
                            TeamSlot(player.copy(difficulty = 0), colorIndex = 0, hogCount = hogCount.toInt()),
                            TeamSlot(cpu.copy(difficulty = aiDifficulty), colorIndex = 1, hogCount = hogCount.toInt()),
                        )
                    }
                    NewGameMode.MULTI -> {
                        if (slots.size < 2 || slots.map { it.colorIndex }.toSet().size < 2) {
                            Toast.makeText(context, R.string.local_game_need_teams, Toast.LENGTH_LONG).show()
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
                        script = styles.firstOrNull { it.title == styleName }?.script,
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
private fun QuickTeams(
    playerName: String,
    cpuName: String,
    allTeams: List<Team>,
    onPlayerTeam: (Team) -> Unit,
    onCpuTeam: (Team) -> Unit,
    aiDifficulty: Int,
    onDifficulty: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorDot(0)
            TeamSlotPicker(playerName, allTeams, onPlayerTeam, Modifier.weight(1f))
            DifficultyBadge(0)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorDot(1)
            TeamSlotPicker(cpuName, allTeams, onCpuTeam, Modifier.weight(1f))
            DifficultyBadge(aiDifficulty)
        }
        Text(
            stringResource(R.string.quick_game_difficulty),
            color = HwColors.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (level in 1..5) {
                HwChip("★".repeat(level), aiDifficulty == level) { onDifficulty(level) }
            }
        }
    }
}

/**
 * Puts [picked] on the given line.
 *
 * A team plays at most once in a match (the desktop keeps two lists, playing
 * and not playing), so picking a team that is already on another line makes the
 * two lines **trade** teams instead of refusing — with only two teams saved,
 * trading is the whole point, and hiding them would leave nothing to pick.
 * Each line keeps its own human/CPU setting: the role belongs to the seat, not
 * to the team.
 */
internal fun pickTeamInto(slots: List<TeamSlot>, slotIndex: Int, picked: Team): List<TeamSlot> {
    val here = slots[slotIndex]
    if (picked.name == here.team.name) return slots
    val there = slots.indexOfFirst { it.team.name == picked.name }
    return slots.mapIndexed { i, slot ->
        when (i) {
            slotIndex -> slot.copy(team = picked.copy(difficulty = slot.team.difficulty))
            there -> slot.copy(team = here.team.copy(difficulty = slot.team.difficulty))
            else -> slot
        }
    }
}

/**
 * The team a new participant starts on: the first one not already playing —
 * with its own human/CPU level, as the desktop keeps it. Falls back to an
 * anonymous extra opponent once every saved team is in the match.
 */
internal fun nextFreeTeam(all: List<Team>, slots: List<TeamSlot>): Team {
    val playing = slots.map { it.team.name }.toSet()
    return all.firstOrNull { it.name !in playing }
        ?: Team.default("Team ${slots.size + 1}", "Hog", difficulty = 3)
}

@Composable
private fun MultiTeams(
    slots: androidx.compose.runtime.snapshots.SnapshotStateList<TeamSlot>,
    allTeams: List<Team>,
) {
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
                TeamSlotPicker(
                    name = slot.team.name,
                    choices = allTeams,
                    modifier = Modifier.weight(1f),
                    onPick = { picked ->
                        val updated = pickTeamInto(slots, index, picked)
                        updated.forEachIndexed { i, s -> if (s != slots[i]) slots[i] = s }
                    },
                )
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
            HwChip("+ " + stringResource(R.string.local_game_add_team), selected = false, onClick = {
                val usedColors = slots.map { it.colorIndex }.toSet()
                val color = (0 until Team.COLORS.size).firstOrNull { it !in usedColors } ?: 0
                slots.add(TeamSlot(nextFreeTeam(allTeams, slots), colorIndex = color))
            })
        }
    }
}

/** The slot's team name, tappable to swap in another of the player's teams. */
@Composable
private fun TeamSlotPicker(
    name: String,
    choices: List<Team>,
    onPick: (Team) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Nothing to choose from when the player has a single team: no affordance.
    val pickable = choices.any { it.name != name }
    Box(modifier) {
        Row(
            Modifier.clickable(enabled = pickable) { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                color = HwColors.TextLight,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (pickable) {
                Text(" ▾", color = HwColors.Gold, style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { team ->
                DropdownMenuItem(
                    text = {
                        Text(
                            team.name,
                            color = if (team.name == name) HwColors.Gold else HwColors.TextLight,
                        )
                    },
                    onClick = {
                        expanded = false
                        onPick(team)
                    },
                )
            }
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
