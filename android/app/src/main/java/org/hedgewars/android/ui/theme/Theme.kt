package org.hedgewars.android.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import org.hedgewars.android.R

/**
 * The single Hedgewars-styled dark theme used throughout the frontend.
 * Colours come from [HwColors]; text is DejaVu Sans Bold ([HwTypography]).
 */
@Composable
fun HedgewarsTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = HwColors.Gold,
        onPrimary = HwColors.IndigoDeep,
        secondary = HwColors.Amber,
        onSecondary = HwColors.IndigoDeep,
        background = HwColors.Indigo,
        onBackground = HwColors.TextLight,
        surface = HwColors.PanelSolid,
        onSurface = HwColors.TextLight,
        surfaceVariant = HwColors.Panel,
        onSurfaceVariant = HwColors.TextMuted,
        outline = HwColors.Outline,
    )
    MaterialTheme(colorScheme = colors, typography = HwTypography, content = content)
}

/**
 * Full-screen Hedgewars menu backdrop: the game's night-sky background image
 * scaled to fill, over the indigo base, with a subtle darkening scrim so
 * translucent panels and text stay legible on top.
 */
@Composable
fun HedgewarsBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().background(HwColors.IndigoDeep)) {
        Image(
            painter = painterResource(R.drawable.menu_background),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to HwColors.IndigoDeep.copy(alpha = 0.55f),
                        0.5f to HwColors.Indigo.copy(alpha = 0.35f),
                        1f to HwColors.IndigoDeep.copy(alpha = 0.75f),
                    )
                )
        )
        // Default any loose text (screen titles etc.) to a light colour: without
        // a wrapping Surface, LocalContentColor would be black and vanish on the
        // dark backdrop. Cards/buttons still set their own content colours.
        CompositionLocalProvider(LocalContentColor provides HwColors.TextLight) {
            content()
        }
    }
}
