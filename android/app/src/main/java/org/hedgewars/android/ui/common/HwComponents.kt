package org.hedgewars.android.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.hedgewars.android.ui.theme.HwColors

/**
 * Standard Hedgewars screen: the night-sky backdrop, a translucent top bar
 * with an optional back arrow and a title, then a scrollable content column.
 */
@Composable
fun HwScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    scroll: Boolean = true,
    content: @Composable () -> Unit,
) {
    // The night-sky backdrop is provided once, app-wide (see MainActivity); this
    // scaffold is transparent and just lays out the top bar and content on it.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HwColors.Panel)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = HwColors.Gold, fontSize = 22.sp)
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = HwColors.Gold,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        val inner = Modifier.fillMaxSize().padding(horizontal = 16.dp)
        if (scroll) {
            Column(inner.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) { content() }
        } else {
            Column(inner) { content() }
        }
    }
}

/** Translucent dark-violet panel, the standard container for grouped content. */
@Composable
fun HwPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HwColors.Panel)
    ) {
        Column(Modifier.padding(padding)) { content() }
    }
}

/** Small gold section label above a group of controls. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = HwColors.Gold,
        letterSpacing = 1.5.sp,
        modifier = modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

/**
 * The big Hedgewars menu button: a gold gradient slab for primary actions, or
 * a translucent gold-outlined slab for secondary ones.
 */
@Composable
fun HwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val shape: Shape = RoundedCornerShape(28.dp)
    val base = if (primary) {
        Modifier.background(Brush.horizontalGradient(listOf(HwColors.Amber, HwColors.Gold)))
    } else {
        Modifier
            .background(HwColors.Panel)
            .border(BorderStroke(1.5.dp, HwColors.Outline), shape)
    }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(shape)
            .then(base)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (primary) HwColors.IndigoDeep else HwColors.TextLight,
            textAlign = TextAlign.Center,
        )
    }
}

/** A pill-shaped selectable chip in the Hedgewars palette. */
@Composable
fun HwChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) HwColors.Gold else HwColors.Panel)
            .border(BorderStroke(1.dp, if (selected) HwColors.Gold else HwColors.Outline), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = when {
                !enabled -> HwColors.TextMuted
                selected -> HwColors.IndigoDeep
                else -> HwColors.TextLight
            },
        )
    }
}

/**
 * A framed, selectable tile used by the map and theme grids: a gold border and
 * slight lift when selected, a faint outline otherwise. Caller draws whatever
 * content (a thumbnail plus a caption) inside.
 */
@Composable
fun SelectableTile(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(HwColors.IndigoDeep)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) HwColors.Gold else HwColors.OutlineSoft),
                shape,
            )
            .clickable(onClick = onClick),
    ) { content() }
}

/**
 * Compact AI-difficulty indicator: a small person glyph for a human team
 * (level 0) or 1..5 stars for a CPU team.
 */
@Composable
fun DifficultyBadge(level: Int, modifier: Modifier = Modifier) {
    val label = if (level <= 0) "👤 Human" else "★".repeat(level.coerceAtMost(5))
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = if (level <= 0) HwColors.TextLight else HwColors.Gold,
        fontSize = 16.sp,
        modifier = modifier,
    )
}
