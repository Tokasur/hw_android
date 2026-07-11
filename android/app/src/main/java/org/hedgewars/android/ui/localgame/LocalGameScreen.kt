package org.hedgewars.android.ui.localgame

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.config.GameConfig
import org.hedgewars.android.config.MapChoice
import org.hedgewars.android.config.Scheme
import org.hedgewars.android.config.Team
import org.hedgewars.android.config.TeamSlot
import org.hedgewars.android.config.WeaponSet
import org.hedgewars.android.data.MissionsRepository
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.TeamsRepository
import org.hedgewars.android.engine.GameConnection
import org.hedgewars.android.game.GameLauncher
import org.hedgewars.android.ui.common.DropdownPicker

/**
 * Local multiplayer (hotseat) setup: 2-8 teams, human or CPU each, map,
 * theme, scheme and weapon set.
 */
@Composable
fun LocalGameScreen(nav: NavController) {
    val context = LocalContext.current
    val teamsRepo = remember { TeamsRepository(context) }
    val repo = remember { MissionsRepository(GamePaths(context)) }
    val launcher = remember { GameLauncher(context) }

    val allTeams = remember { teamsRepo.list() }
    val slots = remember {
        mutableStateListOf<TeamSlot>().apply {
            allTeams.take(2).forEachIndexed { i, t ->
                add(TeamSlot(t, colorIndex = i))
            }
        }
    }
    var hogCount by remember { mutableStateOf(4f) }
    var schemeName by remember { mutableStateOf(Scheme.DEFAULT.name) }
    var weaponsName by remember { mutableStateOf(WeaponSet.DEFAULT.name) }
    var theme by remember { mutableStateOf("Nature") }
    val randomLabel = stringResource(R.string.local_game_map_random)
    var mapName by remember { mutableStateOf(randomLabel) }

    val themes = remember { repo.themes().ifEmpty { listOf("Nature") } }
    val maps = remember { listOf(randomLabel) + repo.multiplayerMaps() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.local_game_title), style = MaterialTheme.typography.headlineMedium)

        slots.forEachIndexed { index, slot ->
            TeamSlotCard(
                slot = slot,
                onCycleColor = {
                    slots[index] = slot.copy(colorIndex = (slot.colorIndex + 1) % Team.COLORS.size)
                },
                onCycleDifficulty = {
                    val d = (slot.team.difficulty + 1) % 6
                    slots[index] = slot.copy(team = slot.team.copy(difficulty = d))
                },
                onRemove = { slots.removeAt(index) },
            )
        }

        if (slots.size < 8) {
            OutlinedButton(
                onClick = {
                    val used = slots.map { it.team.name }.toSet()
                    val next = allTeams.firstOrNull { it.name !in used }
                        ?: Team.default("Team ${slots.size + 1}", "Hog")
                    val usedColors = slots.map { it.colorIndex }.toSet()
                    val color = (0 until Team.COLORS.size).firstOrNull { it !in usedColors } ?: 0
                    slots.add(TeamSlot(next, colorIndex = color, hogCount = hogCount.toInt()))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.local_game_add_team)) }
        }

        Text(stringResource(R.string.local_game_hogs_per_team) + ": ${hogCount.toInt()}")
        Slider(value = hogCount, onValueChange = { hogCount = it }, valueRange = 1f..8f, steps = 6)

        DropdownPicker(stringResource(R.string.local_game_map), maps, mapName) { mapName = it }
        DropdownPicker(stringResource(R.string.local_game_theme), themes, theme) { theme = it }
        DropdownPicker(
            stringResource(R.string.local_game_scheme),
            Scheme.PRESETS.map { it.name }, schemeName,
        ) { schemeName = it }
        DropdownPicker(
            stringResource(R.string.local_game_weapons),
            WeaponSet.PRESETS.map { it.name }, weaponsName,
        ) { weaponsName = it }

        Button(
            onClick = {
                val distinctColors = slots.map { it.colorIndex }.toSet()
                if (slots.size < 2 || distinctColors.size < 2) {
                    Toast.makeText(context, R.string.local_game_need_teams, Toast.LENGTH_LONG).show()
                    return@Button
                }
                val cfg = GameConfig(
                    teams = slots.map { it.copy(hogCount = hogCount.toInt()) },
                    scheme = Scheme.PRESETS.first { it.name == schemeName },
                    weapons = WeaponSet.PRESETS.first { it.name == weaponsName },
                    map = if (mapName == randomLabel) MapChoice.Generated()
                          else MapChoice.Named(mapName),
                    theme = theme,
                )
                launcher.launchLocalGame(cfg, object : GameConnection.Listener {})
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.local_game_start)) }
    }
}

@Composable
private fun TeamSlotCard(
    slot: TeamSlot,
    onCycleColor: () -> Unit,
    onCycleDifficulty: () -> Unit,
    onRemove: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(onClick = onCycleColor) {
                Spacer(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF000000 or Team.COLORS[slot.colorIndex].toLong())),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(slot.team.name, style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = onCycleDifficulty) {
                Text(difficultyLabel(slot.team.difficulty))
            }
            OutlinedButton(onClick = onRemove) { Text("✕") }
        }
    }
}

@Composable
private fun difficultyLabel(difficulty: Int): String = stringResource(
    when (difficulty) {
        0 -> R.string.difficulty_0
        1 -> R.string.difficulty_1
        2 -> R.string.difficulty_2
        3 -> R.string.difficulty_3
        4 -> R.string.difficulty_4
        else -> R.string.difficulty_5
    }
)
