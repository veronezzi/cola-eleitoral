package com.veronezzi.colaeleitoral.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

/**
 * Material 3 type scale on the system font (no downloadable fonts: no network besides the TSE).
 * Every size and line height is in `sp`, so text follows the user's font scale (nonlinear on
 * Android 14+). The smallest styles are a little larger than the Material defaults because many
 * voters read the app outdoors, in a hurry or with low vision.
 */
internal val AppTypography = Typography(
    displayLarge = defaults.displayLarge,
    displayMedium = defaults.displayMedium,
    displaySmall = defaults.displaySmall,
    headlineLarge = defaults.headlineLarge,
    headlineMedium = defaults.headlineMedium,
    headlineSmall = defaults.headlineSmall,
    titleLarge = defaults.titleLarge,
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = defaults.titleSmall,
    bodyLarge = defaults.bodyLarge,
    bodyMedium = defaults.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = defaults.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = defaults.labelLarge,
    labelMedium = defaults.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = defaults.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
)

/** Candidate numbers: tabular figures so every digit has the same width in boxes and lists. */
internal val NumberTextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = "tnum",
)
