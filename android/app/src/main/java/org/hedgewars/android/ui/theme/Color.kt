package org.hedgewars.android.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Hedgewars palette, taken from the desktop frontend (QTfrontend/res/css/qt.css):
 * an indigo night-sky background, translucent dark-violet panels and a gold
 * accent. The port uses one dark theme everywhere so it always looks like the
 * game, regardless of the system light/dark setting.
 */
object HwColors {
    val Indigo = Color(0xFF141250)        // page background (matches the game menu sky)
    val IndigoDeep = Color(0xFF0B0A2E)    // darker base behind the tiled background
    val Panel = Color(0xE00D0544)         // rgba(13,5,68,0.88) — cards / panels
    val PanelSolid = Color(0xFF160A4A)
    val Gold = Color(0xFFFFCC00)          // primary accent (selected, buttons)
    val GoldDim = Color(0xFFB97400)
    val Amber = Color(0xFFF5A623)
    val TextLight = Color(0xFFF2F0FF)
    val TextMuted = Color(0xFFB9B4E0)
    val Outline = Color(0x55FFCC00)       // faint gold hairline
    val OutlineSoft = Color(0x33FFFFFF)
}
