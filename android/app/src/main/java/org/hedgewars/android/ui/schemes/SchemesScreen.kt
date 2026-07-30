package org.hedgewars.android.ui.schemes

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.config.Scheme
import org.hedgewars.android.data.SchemesRepository
import org.hedgewars.android.ui.common.PresetManagerScreen
import org.hedgewars.android.ui.common.safeBack

@Composable
fun SchemesScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { SchemesRepository(context) }
    var customs by remember { mutableStateOf(repo.customs()) }
    // Reload when we come back from the editor, else the list goes stale.
    LifecycleResumeEffect(Unit) {
        customs = repo.customs()
        onPauseOrDispose { }
    }
    val copyTemplate = stringResource(R.string.common_copy_of)

    PresetManagerScreen(
        title = stringResource(R.string.schemes_title),
        newLabel = stringResource(R.string.schemes_new),
        customsHeader = stringResource(R.string.schemes_custom),
        presetNames = Scheme.PRESETS.map { it.name },
        customNames = customs.map { it.name },
        onBack = { nav.safeBack() },
        onNew = { nav.navigate("schemeEdit") },
        onOpen = { nav.navigate("schemeEdit/${Uri.encode(it)}") },
        onDuplicate = { name ->
            val copy = repo.duplicate(repo.resolve(name), copyTemplate)
            nav.navigate("schemeEdit/${Uri.encode(copy.name)}")
        },
    )
}
