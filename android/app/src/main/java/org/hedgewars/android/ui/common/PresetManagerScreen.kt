package org.hedgewars.android.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.hedgewars.android.R
import org.hedgewars.android.ui.theme.HwColors

/**
 * Shared manager screen for schemes and weapon sets: a "new" button, the
 * read-only presets (duplicate-to-customize, like the desktop), then the
 * user's own entries (tap to edit, or duplicate).
 */
@Composable
fun PresetManagerScreen(
    title: String,
    newLabel: String,
    customsHeader: String,
    presetNames: List<String>,
    customNames: List<String>,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onDuplicate: (String) -> Unit,
) {
    HwScreen(title = title, onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HwButton(newLabel, onClick = onNew)
            Spacer(Modifier.height(2.dp))

            SectionHeader(stringResource(R.string.presets_header))
            presetNames.forEach { name ->
                ManagerRow(name, onOpen = null, onDuplicate = { onDuplicate(name) })
            }

            if (customNames.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                SectionHeader(customsHeader)
                customNames.forEach { name ->
                    ManagerRow(name, onOpen = { onOpen(name) }, onDuplicate = { onDuplicate(name) })
                }
            }
        }
    }
}

@Composable
private fun ManagerRow(name: String, onOpen: (() -> Unit)?, onDuplicate: () -> Unit) {
    HwPanel(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                name,
                color = HwColors.TextLight,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            HwChip(stringResource(R.string.common_duplicate), selected = false, onClick = onDuplicate)
        }
    }
}
