package com.khmerauto.nav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GeelyNightColorScheme = darkColorScheme(
    primary = GeelyAccentCyan,
    onPrimary = GeelyNavyDark,
    primaryContainer = GeelyCardDark,
    onPrimaryContainer = GeelyAccentCyan,
    secondary = GeelyEmeraldGreen,
    onSecondary = GeelyNavyDark,
    background = GeelyNavyDark,
    onBackground = GeelyTextPrimary,
    surface = GeelySurfaceDark,
    onSurface = GeelyTextPrimary,
    surfaceVariant = GeelyCardDark,
    onSurfaceVariant = GeelyTextSecondary,
    outline = GeelyCardBorder,
    error = GeelyCoralRed
)

private val GeelyDayColorScheme = lightColorScheme(
    primary = GeelyDayAccent,
    onPrimary = Color.White,
    primaryContainer = GeelyDayCard,
    onPrimaryContainer = GeelyDayAccent,
    secondary = GeelyEmeraldGreen,
    onSecondary = Color.White,
    background = GeelyDayBackground,
    onBackground = GeelyDayTextPrimary,
    surface = GeelyDaySurface,
    onSurface = GeelyDayTextPrimary,
    surfaceVariant = GeelyDayCard,
    onSurfaceVariant = GeelyDayTextSecondary,
    outline = GeelyDayCardBorder,
    error = GeelyCoralRed
)

@Composable
fun KMAutoNavTheme(
    isDaytime: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDaytime) GeelyDayColorScheme else GeelyNightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AutomotiveTypography,
        content = content
    )
}
