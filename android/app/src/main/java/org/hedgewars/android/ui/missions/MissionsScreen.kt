package org.hedgewars.android.ui.missions

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.config.MissionConfig
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.MissionsRepository
import org.hedgewars.android.data.TeamsRepository
import org.hedgewars.android.engine.GameConnection
import org.hedgewars.android.game.GameLauncher

@Composable
fun MissionsScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { MissionsRepository(GamePaths(context)) }
    val teamsRepo = remember { TeamsRepository(context) }
    val launcher = remember { GameLauncher(context) }

    var tab by remember { mutableStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.missions_training),
        stringResource(R.string.missions_challenge),
        stringResource(R.string.missions_scenario),
        stringResource(R.string.missions_campaign),
    )

    fun play(script: String, campaign: String? = null) {
        val team = teamsRepo.list().firstOrNull { !it.isCpu } ?: teamsRepo.list().firstOrNull()
        if (team == null) {
            Toast.makeText(context, R.string.local_game_need_teams, Toast.LENGTH_LONG).show()
            return
        }
        launcher.launchMission(
            MissionConfig(team = team, script = script, campaign = campaign),
            object : GameConnection.Listener {},
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.missions_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp),
        )
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
            }
        }
        when (tab) {
            0 -> MissionList(repo.training()) { play(it.script) }
            1 -> MissionList(repo.challenges()) { play(it.script) }
            2 -> MissionList(repo.scenarios()) { play(it.script) }
            3 -> CampaignList(repo) { mission, campaign -> play(mission.script, campaign) }
        }
    }
}

@Composable
private fun MissionList(
    missions: List<MissionsRepository.Mission>,
    onPlay: (MissionsRepository.Mission) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(missions) { mission ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onPlay(mission) },
            ) {
                Text(mission.title, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun CampaignList(
    repo: MissionsRepository,
    onPlay: (MissionsRepository.Mission, String) -> Unit,
) {
    val campaigns = remember { repo.campaigns() }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        campaigns.forEach { campaign ->
            item {
                Text(
                    campaign.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(campaign.missions) { mission ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onPlay(mission, campaign.name) },
                ) {
                    Text(mission.title, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
