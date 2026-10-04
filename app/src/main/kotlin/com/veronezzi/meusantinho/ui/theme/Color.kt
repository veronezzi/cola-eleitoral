package com.veronezzi.meusantinho.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Fallback palette, used when dynamic color is unavailable (Android 11 or older) or turned off.
 *
 * Neutrality: slate blue-gray with a warm-gray accent. It avoids the colors most associated with
 * Brazilian parties and movements (red; green with yellow; orange). Every text/background pair
 * meets WCAG AA (at least 4.5:1; most pairs above 6:1) and outlines reach 3:1 against surfaces.
 * Error colors are the Material defaults and are used only for app errors, never for candidates.
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF3A5568),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E4F2),
    onPrimaryContainer = Color(0xFF0B1D29),
    inversePrimary = Color(0xFFA2CBE4),
    secondary = Color(0xFF4F5F6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E3EE),
    onSecondaryContainer = Color(0xFF0E1D27),
    tertiary = Color(0xFF5F5A52),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE7E1D8),
    onTertiaryContainer = Color(0xFF1D1B16),
    background = Color(0xFFF8F9FB),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF8F9FB),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF40484E),
    surfaceTint = Color(0xFF3A5568),
    inverseSurface = Color(0xFF2E3133),
    inverseOnSurface = Color(0xFFEFF1F3),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF70787E),
    outlineVariant = Color(0xFFC0C7CD),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF8F9FB),
    surfaceDim = Color(0xFFD8DADD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F4F6),
    surfaceContainer = Color(0xFFECEEF0),
    surfaceContainerHigh = Color(0xFFE6E8EA),
    surfaceContainerHighest = Color(0xFFE1E3E5),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFA2CBE4),
    onPrimary = Color(0xFF06344A),
    primaryContainer = Color(0xFF213D50),
    onPrimaryContainer = Color(0xFFD2E4F2),
    inversePrimary = Color(0xFF3A5568),
    secondary = Color(0xFFB7C8D4),
    onSecondary = Color(0xFF22323C),
    secondaryContainer = Color(0xFF384853),
    onSecondaryContainer = Color(0xFFD6E3EE),
    tertiary = Color(0xFFCBC5BC),
    onTertiary = Color(0xFF33302A),
    tertiaryContainer = Color(0xFF4A4640),
    onTertiaryContainer = Color(0xFFE7E1D8),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE1E3E5),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE1E3E5),
    surfaceVariant = Color(0xFF40484E),
    onSurfaceVariant = Color(0xFFC0C7CD),
    surfaceTint = Color(0xFFA2CBE4),
    inverseSurface = Color(0xFFE1E3E5),
    inverseOnSurface = Color(0xFF2E3133),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8A9297),
    outlineVariant = Color(0xFF40484E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF37393C),
    surfaceDim = Color(0xFF111416),
    surfaceContainerLowest = Color(0xFF0C0F11),
    surfaceContainerLow = Color(0xFF191C1E),
    surfaceContainer = Color(0xFF1D2022),
    surfaceContainerHigh = Color(0xFF272A2D),
    surfaceContainerHighest = Color(0xFF323538),
)
