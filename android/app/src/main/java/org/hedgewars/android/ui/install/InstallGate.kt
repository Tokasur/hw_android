package org.hedgewars.android.ui.install

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hedgewars.android.R
import org.hedgewars.android.data.DataInstaller
import org.hedgewars.android.data.GamePaths

/**
 * Blocks the UI until the game data is copied from assets to filesDir
 * (first launch and after each app update).
 */
@Composable
fun InstallGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val installer = remember { DataInstaller(context, GamePaths(context)) }

    var state by remember {
        mutableStateOf<DataInstaller.State>(
            if (installer.isInstalled()) DataInstaller.State.Ready
            else DataInstaller.State.NotInstalled
        )
    }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        if (state is DataInstaller.State.Ready) return@LaunchedEffect
        state = DataInstaller.State.Installing(0, 1)
        withContext(Dispatchers.IO) {
            try {
                installer.install { done, total ->
                    state = DataInstaller.State.Installing(done, total)
                }
                state = DataInstaller.State.Ready
            } catch (e: Exception) {
                state = DataInstaller.State.Failed(e.message ?: "unknown error")
            }
        }
    }

    when (val s = state) {
        is DataInstaller.State.Ready -> content()
        is DataInstaller.State.Failed -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.data_install_failed), style = MaterialTheme.typography.titleLarge)
            Text(s.error, modifier = Modifier.padding(vertical = 16.dp))
            Button(onClick = { attempt++ }) { Text(stringResource(R.string.data_retry)) }
        }
        else -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.data_installing), style = MaterialTheme.typography.titleLarge)
            val progress = (s as? DataInstaller.State.Installing)
                ?.let { if (it.filesTotal > 0) it.filesDone.toFloat() / it.filesTotal else 0f }
                ?: 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.padding(vertical = 24.dp),
            )
            Text(
                stringResource(R.string.data_installing_detail),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
