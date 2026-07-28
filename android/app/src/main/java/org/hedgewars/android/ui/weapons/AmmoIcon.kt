package org.hedgewars.android.ui.weapons

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hedgewars.android.config.AmmoCatalog
import org.hedgewars.android.ui.theme.HwColors

/**
 * The engine's ammo-menu sprite sheet, decoded once per screen from the
 * installed data. Null while loading or if the data is missing.
 */
@Composable
fun rememberAmmoSheet(dataDir: File): ImageBitmap? {
    val sheet by produceState<ImageBitmap?>(initialValue = null, dataDir) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.decodeFile(File(dataDir, "Graphics/AmmoMenu/Ammos_base.png").path)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    }
    return sheet
}

/** One weapon icon: 32x32 cell [index] of the sheet, scaled crisp (pixel art). */
@Composable
fun AmmoIcon(sheet: ImageBitmap?, index: Int, modifier: Modifier = Modifier) {
    val boxModifier = modifier.size(36.dp).clip(RoundedCornerShape(6.dp))
    if (sheet == null) {
        Box(boxModifier.background(HwColors.PanelSolid))
    } else {
        Image(
            painter = BitmapPainter(
                sheet,
                srcOffset = IntOffset(
                    AmmoCatalog.iconCol(index) * AmmoCatalog.CELL,
                    AmmoCatalog.iconRow(index) * AmmoCatalog.CELL,
                ),
                srcSize = IntSize(AmmoCatalog.CELL, AmmoCatalog.CELL),
                filterQuality = FilterQuality.None,
            ),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = boxModifier.background(HwColors.IndigoDeep),
        )
    }
}
