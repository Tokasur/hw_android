package org.hedgewars.android.ui.teams

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.data.TeamsRepository

@Composable
fun TeamsScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { TeamsRepository(context) }
    val teams = remember { mutableStateOf(repo.list()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.teams_title), style = MaterialTheme.typography.headlineMedium)

        Button(
            onClick = { nav.navigate("teamEdit") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) { Text(stringResource(R.string.teams_new)) }

        LazyColumn {
            items(teams.value) { team ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { nav.navigate("teamEdit/${team.name}") },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(team.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            team.hogNames.take(4).joinToString(", ") + "…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
