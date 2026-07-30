package org.hedgewars.android.ui.replays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.data.DemosRepository
import org.hedgewars.android.data.PacksRepository
import org.hedgewars.android.game.GameLauncher
import org.hedgewars.android.ui.common.HwChip
import org.hedgewars.android.ui.common.HwPanel
import org.hedgewars.android.ui.common.HwScreen
import org.hedgewars.android.ui.common.safeBack
import org.hedgewars.android.ui.theme.HwColors

/**
 * Saved replays: tap one to watch it (hands free — the engine replays the
 * recorded commands), or delete it.
 */
@Composable
fun ReplaysScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { DemosRepository(context) }
    val launcher = remember { GameLauncher(context) }
    var demos by remember { mutableStateOf(repo.list()) }
    var confirmDelete by remember { mutableStateOf<DemosRepository.Demo?>(null) }

    // Coming back from watching a replay (or from a match that saved one):
    // the list on disk may well have changed.
    LifecycleResumeEffect(Unit) {
        demos = repo.list()
        onPauseOrDispose { }
    }

    HwScreen(
        title = stringResource(R.string.replays_title),
        onBack = { nav.safeBack() },
        scroll = false,
    ) {
        if (demos.isEmpty()) {
            Text(
                stringResource(R.string.replays_empty),
                color = HwColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            return@HwScreen
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(demos, key = { it.file.absolutePath }) { demo ->
                ReplayRow(
                    demo = demo,
                    onPlay = { launcher.launchReplay(demo.file) },
                    onDelete = { confirmDelete = demo },
                )
            }
            item(key = "bottom-spacer") { Spacer(Modifier.height(16.dp)) }
        }
    }

    confirmDelete?.let { demo ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.replays_delete_confirm_title)) },
            text = { Text(stringResource(R.string.replays_delete_confirm_text, demo.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    repo.delete(demo)
                    demos = repo.list()
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReplayRow(
    demo: DemosRepository.Demo,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    HwPanel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onPlay)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    demo.displayName,
                    color = HwColors.TextLight,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    PacksRepository.humanSize(demo.sizeBytes),
                    color = HwColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            HwChip(stringResource(R.string.common_delete), selected = false, onClick = onDelete)
        }
    }
}
