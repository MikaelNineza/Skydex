package com.skydex.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// The app is always dark, whatever the system setting, and ignores dynamic (wallpaper) color.
private val ColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = OnGold,
    primaryContainer = GoldContainer,
    onPrimaryContainer = OnGoldContainer,
    secondary = Green,
    onSecondary = OnGreen,
    secondaryContainer = GreenContainer,
    onSecondaryContainer = OnGreenContainer,
    tertiary = Blue,
    onTertiary = OnBlue,
    tertiaryContainer = BlueContainer,
    onTertiaryContainer = OnBlueContainer,
    error = Red,
    onError = OnRed,
    errorContainer = RedContainer,
    onErrorContainer = OnRedContainer,
    background = Background,
    onBackground = OnSurface,
    surface = Background,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    outline = Outline,
    outlineVariant = OutlineVariant,
)

@Composable
fun SkydexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content,
    )
}
