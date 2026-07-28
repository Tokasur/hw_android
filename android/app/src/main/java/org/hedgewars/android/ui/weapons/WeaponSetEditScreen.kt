package org.hedgewars.android.ui.weapons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.hedgewars.android.R
import org.hedgewars.android.config.AmmoCatalog
import org.hedgewars.android.config.AmmoField
import org.hedgewars.android.config.WeaponSet
import org.hedgewars.android.data.GamePaths
import org.hedgewars.android.data.NameError
import org.hedgewars.android.data.WeaponSetsRepository
import org.hedgewars.android.data.sanitizeCustomName
import org.hedgewars.android.ui.common.HwButton
import org.hedgewars.android.ui.common.HwChip
import org.hedgewars.android.ui.common.HwScreen
import org.hedgewars.android.ui.common.jsonSaver
import org.hedgewars.android.ui.theme.HwColors

private val AmmoField.labelRes: Int
    get() = when (this) {
        AmmoField.LOADOUT -> R.string.weapons_tab_loadout
        AmmoField.PROBABILITY -> R.string.weapons_tab_probability
        AmmoField.DELAY -> R.string.weapons_tab_delay
        AmmoField.CRATE -> R.string.weapons_tab_crate
    }

private val AmmoField.hintRes: Int
    get() = when (this) {
        AmmoField.LOADOUT -> R.string.weapons_hint_loadout
        AmmoField.PROBABILITY -> R.string.weapons_hint_probability
        AmmoField.DELAY -> R.string.weapons_hint_delay
        AmmoField.CRATE -> R.string.weapons_hint_crate
    }

/**
 * Weapon-set editor: the desktop's four pages as chip tabs over one weapon
 * list — 58 rows of icon · name · [−] value [+]. The screen itself must not
 * scroll (the LazyColumn does), hence scroll = false.
 */
@Composable
fun WeaponSetEditScreen(nav: NavController, setName: String?) {
    val context = LocalContext.current
    val repo = remember { WeaponSetsRepository(context) }
    val paths = remember { GamePaths(context) }
    val original = remember { setName?.let { repo.get(it) } }

    // Asked to edit something that no longer exists: just leave.
    if (setName != null && original == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }

    var ws by rememberSaveable(stateSaver = jsonSaver<WeaponSet>()) {
        mutableStateOf(original ?: WeaponSet.EMPTY)
    }
    var field by rememberSaveable { mutableStateOf(AmmoField.LOADOUT) }
    val nameError = repo.nameError(ws.name, original?.name)
    val names = stringArrayResource(R.array.ammo_names)
    val sheet = rememberAmmoSheet(paths.dataDir)

    HwScreen(
        title = stringResource(if (original == null) R.string.weapon_sets_new else R.string.weapon_sets_edit),
        onBack = { nav.popBackStack() },
        scroll = false,
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = ws.name,
                onValueChange = { ws = ws.copy(name = sanitizeCustomName(it)) },
                label = { Text(stringResource(R.string.weapons_name)) },
                isError = nameError != null,
                supportingText = {
                    when (nameError) {
                        NameError.EMPTY -> Text(stringResource(R.string.name_error_empty))
                        NameError.TAKEN -> Text(stringResource(R.string.name_error_taken))
                        null -> {}
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                AmmoField.entries.forEach { f ->
                    HwChip(stringResource(f.labelRes), selected = field == f, onClick = { field = f })
                }
            }
            Text(
                stringResource(field.hintRes),
                color = HwColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )

            LazyColumn(Modifier.weight(1f).padding(top = 6.dp)) {
                items(AmmoCatalog.VISIBLE, key = { it }) { i ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        AmmoIcon(sheet, i)
                        Text(
                            names.getOrNull(i) ?: "#$i",
                            color = HwColors.TextLight,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        )
                        HwChip("−", selected = false, onClick = {
                            ws = ws.set(field, i, ws.get(field, i) - 1)
                        })
                        val v = ws.get(field, i)
                        Text(
                            if (field == AmmoField.LOADOUT && v == 9) "∞" else v.toString(),
                            color = HwColors.Gold,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 36.dp),
                        )
                        HwChip("+", selected = false, onClick = {
                            ws = ws.set(field, i, ws.get(field, i) + 1)
                        })
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            ) {
                HwButton(
                    stringResource(R.string.common_save),
                    enabled = nameError == null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val toSave = ws.copy(name = ws.name.trim())
                        if (original != null && original.name != toSave.name) repo.delete(original.name)
                        repo.save(toSave)
                        nav.popBackStack()
                    },
                )
                if (original != null) {
                    HwButton(
                        stringResource(R.string.common_delete),
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            repo.delete(original.name)
                            nav.popBackStack()
                        },
                    )
                }
            }
        }
    }
}
