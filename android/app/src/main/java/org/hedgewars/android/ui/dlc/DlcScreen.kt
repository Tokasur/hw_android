package org.hedgewars.android.ui.dlc

import android.widget.Toast
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.hedgewars.android.R
import org.hedgewars.android.data.CatalogRepository
import org.hedgewars.android.data.DlcCategory
import org.hedgewars.android.data.DlcEntry
import org.hedgewars.android.data.PacksRepository
import org.hedgewars.android.ui.common.HwButton
import org.hedgewars.android.ui.common.HwChip
import org.hedgewars.android.ui.common.HwPanel
import org.hedgewars.android.ui.common.HwScreen
import org.hedgewars.android.ui.common.SectionHeader
import org.hedgewars.android.ui.common.safeBack
import org.hedgewars.android.ui.theme.HwColors

private sealed interface CatalogState {
    data object Loading : CatalogState
    data object Failed : CatalogState
    data class Ready(val categories: List<DlcCategory>) : CatalogState
}

/**
 * The community content manager: installed packs (with delete — something
 * the desktop never had) above the hedgewars.org catalog. One download at a
 * time; leaving the screen cancels it and the partial file is cleaned up.
 */
@Composable
fun DlcScreen(nav: NavController) {
    val context = LocalContext.current
    val packsRepo = remember { PacksRepository(context) }
    val catalogRepo = remember { CatalogRepository() }
    val scope = rememberCoroutineScope()

    var catalog by remember { mutableStateOf<CatalogState>(CatalogState.Loading) }
    var fetchAttempt by remember { mutableStateOf(0) }
    var installed by remember { mutableStateOf(listOf<PacksRepository.InstalledPack>()) }
    var installedNames by remember { mutableStateOf(setOf<String>()) }
    var pendingInstall by remember { mutableStateOf<DlcEntry?>(null) }
    var progressPercent by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf<PacksRepository.InstalledPack?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            packsRepo.sweepStaleParts()
            val list = packsRepo.installed()
            val names = packsRepo.installedFileNames()
            installed = list
            installedNames = names
        }
    }

    LaunchedEffect(fetchAttempt) {
        catalog = CatalogState.Loading
        catalog = try {
            CatalogState.Ready(withContext(Dispatchers.IO) { catalogRepo.fetchCatalog() })
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            CatalogState.Failed
        }
    }

    val downloadFailed = stringResource(R.string.dlc_download_failed)
    LaunchedEffect(pendingInstall) {
        val entry = pendingInstall ?: return@LaunchedEffect
        progressPercent = null
        try {
            withContext(Dispatchers.IO) {
                catalogRepo.openDownload(entry).use { dl ->
                    packsRepo.install(entry.fileName, dl.stream) { bytes ->
                        progressPercent =
                            if (dl.totalBytes > 0) (bytes * 100 / dl.totalBytes).toInt() else null
                    }
                }
                installed = packsRepo.installed()
                installedNames = packsRepo.installedFileNames()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Toast.makeText(context, downloadFailed.format(e.message ?: "?"), Toast.LENGTH_LONG).show()
        }
        pendingInstall = null
        progressPercent = null
    }

    HwScreen(
        title = stringResource(R.string.dlc_title),
        onBack = { nav.safeBack() },
        scroll = false,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "installed-header") {
                SectionHeader(stringResource(R.string.dlc_installed_header))
            }
            if (installed.isEmpty()) {
                item(key = "installed-empty") {
                    Text(
                        stringResource(R.string.dlc_installed_empty),
                        color = HwColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                items(installed, key = { "pack:${it.fileName}" }) { pack ->
                    InstalledRow(pack, onDelete = { confirmDelete = pack })
                }
                item(key = "next-match-note") {
                    Text(
                        stringResource(R.string.dlc_next_match_note),
                        color = HwColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            item(key = "catalog-header") {
                Column {
                    Spacer(Modifier.height(6.dp))
                    SectionHeader(stringResource(R.string.dlc_catalog_header))
                }
            }
            when (val c = catalog) {
                CatalogState.Loading -> item(key = "loading") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(
                            stringResource(R.string.dlc_loading),
                            color = HwColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                CatalogState.Failed -> item(key = "failed") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.dlc_error_offline),
                            color = HwColors.TextLight,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        HwButton(stringResource(R.string.data_retry), onClick = { fetchAttempt++ })
                    }
                }
                is CatalogState.Ready -> {
                    for (category in c.categories) {
                        item(key = "cat:${category.title}") {
                            SectionHeader(
                                category.title.ifEmpty { stringResource(R.string.dlc_all_downloads) },
                            )
                        }
                        items(category.entries, key = { it.href }) { entry ->
                            CatalogRow(
                                entry = entry,
                                installed = entry.fileName.lowercase() in installedNames,
                                downloading = pendingInstall == entry,
                                percent = progressPercent,
                                busy = pendingInstall != null,
                                onInstall = { pendingInstall = entry },
                            )
                        }
                    }
                }
            }
            item(key = "bottom-spacer") { Spacer(Modifier.height(16.dp)) }
        }
    }

    confirmDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.dlc_delete_confirm_title)) },
            text = { Text(stringResource(R.string.dlc_delete_confirm_text, pack.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch(Dispatchers.IO) {
                        packsRepo.delete(pack.fileName)
                        installed = packsRepo.installed()
                        installedNames = packsRepo.installedFileNames()
                    }
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
private fun InstalledRow(pack: PacksRepository.InstalledPack, onDelete: () -> Unit) {
    HwPanel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    pack.displayName,
                    color = HwColors.TextLight,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    PacksRepository.humanSize(pack.sizeBytes),
                    color = HwColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            HwChip(stringResource(R.string.common_delete), selected = false, onClick = onDelete)
        }
    }
}

@Composable
private fun CatalogRow(
    entry: DlcEntry,
    installed: Boolean,
    downloading: Boolean,
    percent: Int?,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    HwPanel(Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    entry.name,
                    color = HwColors.TextLight,
                    style = MaterialTheme.typography.titleMedium,
                )
                entry.description?.let {
                    Text(
                        it,
                        color = HwColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val caption = listOfNotNull(entry.author, entry.size).joinToString(" · ")
                if (caption.isNotEmpty()) {
                    Text(
                        caption,
                        color = HwColors.TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            when {
                installed -> HwChip(
                    stringResource(R.string.dlc_installed_chip),
                    selected = true,
                    enabled = false,
                ) {}
                downloading -> HwChip(
                    percent?.let { "$it %" } ?: "…",
                    selected = false,
                    enabled = false,
                ) {}
                else -> HwChip(
                    stringResource(R.string.dlc_install),
                    selected = false,
                    enabled = !busy,
                    onClick = onInstall,
                )
            }
        }
    }
}
