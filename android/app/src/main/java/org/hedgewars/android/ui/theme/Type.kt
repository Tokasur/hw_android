package org.hedgewars.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.hedgewars.android.R

/** DejaVu Sans Bold — the same face the engine renders in-game. */
val DejaVu = FontFamily(Font(R.font.dejavusans_bold, FontWeight.Bold))

/**
 * Typography built entirely on DejaVu Sans Bold so the menu matches the
 * in-game text. Sizes stay close to Material defaults; headlines are enlarged
 * a touch because the bold face reads well at display sizes.
 */
val HwTypography = Typography().run {
    copy(
        displaySmall = displaySmall.hw(),
        headlineLarge = headlineLarge.hw(),
        headlineMedium = headlineMedium.hw(letter = 0.5.sp),
        headlineSmall = headlineSmall.hw(),
        titleLarge = titleLarge.hw(),
        titleMedium = titleMedium.hw(),
        titleSmall = titleSmall.hw(),
        bodyLarge = bodyLarge.hw(),
        bodyMedium = bodyMedium.hw(),
        bodySmall = bodySmall.hw(),
        labelLarge = labelLarge.hw(letter = 0.5.sp),
        labelMedium = labelMedium.hw(),
        labelSmall = labelSmall.hw(),
    )
}

private fun TextStyle.hw(letter: androidx.compose.ui.unit.TextUnit = this.letterSpacing) =
    copy(fontFamily = DejaVu, fontWeight = FontWeight.Bold, letterSpacing = letter)
