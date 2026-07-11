package org.hedgewars.android.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.io.File
import org.hedgewars.android.R
import org.hedgewars.android.config.Team
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.TeamsRepository
import org.hedgewars.android.ui.common.DropdownPicker

@Composable
fun TeamEditScreen(nav: NavController, teamName: String?) {
    val context = LocalContext.current
    val repo = remember { TeamsRepository(context) }
    val paths = remember { GamePaths(context) }
    val original = remember { teamName?.let { repo.get(it) } }

    var name by remember { mutableStateOf(original?.name ?: "") }
    val hogs = remember {
        (original?.hogNames ?: (1..Team.MAX_HOGS).map { "Hog $it" }).toMutableStateList()
    }
    var grave by remember { mutableStateOf(original?.grave ?: "Statue") }
    var fort by remember { mutableStateOf(original?.fort ?: "Plane") }
    var voicepack by remember { mutableStateOf(original?.voicepack ?: "Default") }
    var flag by remember { mutableStateOf(original?.flag ?: "hedgewars") }
    var hat by remember { mutableStateOf(original?.hat ?: "NoHat") }

    val graves = remember { dataNames(paths, "Graphics/Graves", ".png") }
    val forts = remember {
        File(paths.dataDir, "Forts").listFiles { f -> f.name.endsWith("L.png") }
            ?.map { it.name.removeSuffix("L.png") }?.distinct()?.sorted() ?: listOf("Plane")
    }
    val voices = remember {
        File(paths.dataDir, "Sounds/voices").listFiles { f -> f.isDirectory }
            ?.map { it.name }?.sorted() ?: listOf("Default")
    }
    val flags = remember { dataNames(paths, "Graphics/Flags", ".png") }
    val hats = remember { dataNames(paths, "Graphics/Hats", ".png") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(if (original == null) R.string.teams_new else R.string.teams_edit),
            style = MaterialTheme.typography.headlineMedium,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.teams_name)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.teams_hog_names), style = MaterialTheme.typography.titleMedium)
        for (i in hogs.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hogs[i],
                    onValueChange = { hogs[i] = it },
                    modifier = Modifier.weight(1f),
                )
                if (i + 1 < hogs.size) {
                    OutlinedTextField(
                        value = hogs[i + 1],
                        onValueChange = { hogs[i + 1] = it },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        DropdownPicker("Hat", hats, hat) { hat = it }
        DropdownPicker("Grave", graves, grave) { grave = it }
        DropdownPicker("Fort", forts, fort) { fort = it }
        DropdownPicker("Voice", voices, voicepack) { voicepack = it }
        DropdownPicker("Flag", flags, flag) { flag = it }

        Button(
            onClick = {
                if (name.isBlank()) return@Button
                if (original != null && original.name != name) repo.delete(original.name)
                repo.save(
                    Team(
                        name = name.trim(),
                        hogNames = hogs.map { it.ifBlank { "Hog" } },
                        grave = grave, fort = fort, voicepack = voicepack,
                        flag = flag, hat = hat,
                    )
                )
                nav.popBackStack()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.teams_save)) }

        if (original != null) {
            OutlinedButton(
                onClick = {
                    repo.delete(original.name)
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.teams_delete)) }
        }
    }
}

private fun dataNames(paths: GamePaths, rel: String, suffix: String): List<String> =
    File(paths.dataDir, rel).listFiles { f -> f.name.endsWith(suffix) }
        ?.map { it.name.removeSuffix(suffix) }?.sorted()
        ?: emptyList()
