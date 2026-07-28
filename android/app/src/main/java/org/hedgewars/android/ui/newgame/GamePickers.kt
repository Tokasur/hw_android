package org.hedgewars.android.ui.newgame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.hedgewars.android.config.MapChoice
import org.hedgewars.android.config.MapGen
import org.hedgewars.android.ui.common.DiskImage
import org.hedgewars.android.ui.common.HwChip
import org.hedgewars.android.ui.common.SelectableTile
import org.hedgewars.android.ui.theme.HwColors
import java.io.File

/** One of the four map-selection modes shown as chips. */
enum class MapKind { RANDOM, MAZE, PERLIN, NAMED }

fun MapKind.toGen(): MapGen = when (this) {
    MapKind.MAZE -> MapGen.MAZE
    MapKind.PERLIN -> MapGen.PERLIN
    else -> MapGen.REGULAR
}

/**
 * Map picker: a row of kind chips (Random / Maze / Perlin / Map), then either
 * a thumbnail strip of named maps or the generated-terrain controls.
 */
@Composable
fun MapPicker(
    dataDir: File,
    kind: MapKind,
    onKind: (MapKind) -> Unit,
    namedMaps: List<String>,
    selectedMap: String?,
    onSelectMap: (String) -> Unit,
    featureSize: Float,
    onFeatureSize: (Float) -> Unit,
    seed: String,
    onReseed: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HwChip("Random", kind == MapKind.RANDOM) { onKind(MapKind.RANDOM) }
            HwChip("Maze", kind == MapKind.MAZE) { onKind(MapKind.MAZE) }
            HwChip("Perlin", kind == MapKind.PERLIN) { onKind(MapKind.PERLIN) }
            HwChip("Map", kind == MapKind.NAMED) { onKind(MapKind.NAMED) }
        }

        if (kind == MapKind.NAMED) {
            if (namedMaps.isEmpty()) {
                Text("No named maps installed", color = HwColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(namedMaps, key = { it }) { name ->
                        MapTile(
                            preview = File(dataDir, "Maps/$name/preview.png"),
                            name = name,
                            selected = name == selectedMap,
                            onClick = { onSelectMap(name) },
                        )
                    }
                }
            }
        } else {
            // Generated terrain: seed + size, matching the desktop's random controls.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HwChip("🎲  New seed", selected = false, onClick = onReseed)
                Text(
                    seed.trim('{', '}').take(8),
                    style = MaterialTheme.typography.bodySmall,
                    color = HwColors.TextMuted,
                )
            }
            Text("Terrain size", color = HwColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            androidx.compose.material3.Slider(
                value = featureSize,
                onValueChange = onFeatureSize,
                valueRange = 12f..100f,
            )
        }
    }
}

@Composable
private fun MapTile(preview: File, name: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(140.dp),
    ) {
        SelectableTile(selected = selected, onClick = onClick, modifier = Modifier.fillMaxWidth().aspectRatio(2f)) {
            DiskImage(
                file = preview,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                placeholder = { Text("…", color = HwColors.TextMuted, modifier = Modifier.padding(8.dp)) },
            )
        }
        Text(
            name.replace('_', ' '),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) HwColors.Gold else HwColors.TextLight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
        )
    }
}

/** Theme picker: a horizontal strip of theme icons. */
@Composable
fun ThemePicker(
    dataDir: File,
    themes: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(themes, key = { it }) { theme ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(84.dp),
            ) {
                SelectableTile(
                    selected = theme == selected,
                    onClick = { onSelect(theme) },
                    modifier = Modifier.size(72.dp),
                ) {
                    DiskImage(
                        file = File(dataDir, "Themes/$theme/icon.png"),
                        modifier = Modifier.size(72.dp).padding(8.dp),
                        contentScale = ContentScale.Fit,
                        placeholder = {
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                Text(theme.take(2), color = HwColors.TextMuted)
                            }
                        },
                    )
                }
                Text(
                    theme,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (theme == selected) HwColors.Gold else HwColors.TextLight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                )
            }
        }
    }
}
