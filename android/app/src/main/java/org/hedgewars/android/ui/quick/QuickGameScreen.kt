package org.hedgewars.android.ui.quick

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import org.hedgewars.android.config.GameConfig
import org.hedgewars.android.config.TeamSlot
import org.hedgewars.android.data.TeamsRepository
import org.hedgewars.android.game.GameLauncher

/**
 * One-tap solo game: the player's first team against a CPU team.
 */
@Composable
fun QuickGameScreen(nav: NavController) {
    val context = LocalContext.current
    val teamsRepo = remember { TeamsRepository(context) }
    val launcher = remember { GameLauncher(context) }
    var difficulty by remember { mutableStateOf(3) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.quick_game_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.quick_game_difficulty))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (level in 1..5) {
                FilterChip(
                    selected = difficulty == level,
                    onClick = { difficulty = level },
                    label = { Text(difficultyLabel(level)) },
                )
            }
        }

        Button(
            onClick = {
                val teams = teamsRepo.list()
                val player = teams.firstOrNull { !it.isCpu } ?: teams.firstOrNull()
                if (player == null) {
                    Toast.makeText(context, R.string.local_game_need_teams, Toast.LENGTH_LONG).show()
                    return@Button
                }
                val cpu = teams.firstOrNull { it.name != player.name }
                    ?: org.hedgewars.android.config.Team.default("Robots", "Unit", difficulty)
                val cfg = GameConfig(
                    teams = listOf(
                        TeamSlot(player.copy(difficulty = 0), colorIndex = 0),
                        TeamSlot(cpu.copy(difficulty = difficulty), colorIndex = 1),
                    ),
                )
                launcher.launchLocalGame(cfg)
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.quick_game_start))
        }
    }
}

@Composable
private fun difficultyLabel(level: Int): String = stringResource(
    when (level) {
        1 -> R.string.difficulty_1
        2 -> R.string.difficulty_2
        3 -> R.string.difficulty_3
        4 -> R.string.difficulty_4
        else -> R.string.difficulty_5
    }
)
