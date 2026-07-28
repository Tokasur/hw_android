package org.hedgewars.android.ui.weapons

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
import org.hedgewars.android.config.WeaponSet
import org.hedgewars.android.data.WeaponSetsRepository
import org.hedgewars.android.ui.common.PresetManagerScreen

@Composable
fun WeaponSetsScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { WeaponSetsRepository(context) }
    var customs by remember { mutableStateOf(repo.customs()) }
    // Reload when we come back from the editor, else the list goes stale.
    LifecycleResumeEffect(Unit) {
        customs = repo.customs()
        onPauseOrDispose { }
    }
    val copyTemplate = stringResource(R.string.common_copy_of)

    PresetManagerScreen(
        title = stringResource(R.string.weapon_sets_title),
        newLabel = stringResource(R.string.weapon_sets_new),
        customsHeader = stringResource(R.string.weapon_sets_custom),
        presetNames = WeaponSet.PRESETS.map { it.name },
        customNames = customs.map { it.name },
        onBack = { nav.popBackStack() },
        onNew = { nav.navigate("weaponSetEdit") },
        onOpen = { nav.navigate("weaponSetEdit/${Uri.encode(it)}") },
        onDuplicate = { name ->
            val copy = repo.duplicate(repo.resolve(name), copyTemplate)
            nav.navigate("weaponSetEdit/${Uri.encode(copy.name)}")
        },
    )
}
