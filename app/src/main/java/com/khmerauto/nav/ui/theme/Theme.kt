package com.khmerauto.nav.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GeelyNightColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF0D0B1A),
    primaryContainer = Color(0xFF1E1B35),
    onPrimaryContainer = Color(0xFF00E5FF),
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color(0xFF0D0B1A),
    background = Color(0xFF0D0B1A),
    onBackground = Color(0xFFF4F2FF),
    surface = Color(0xFF16132A),
    onSurface = Color(0xFFF4F2FF),
    surfaceVariant = Color(0xFF1E1B35),
    onSurfaceVariant = Color(0xFF8B87A8),
    outline = Color(0x1FFFFFFF),
    error = Color(0xFFFF5252)
)

private val GeelyDayColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF0D0B1A),
    primaryContainer = Color(0xFF221F38),
    onPrimaryContainer = Color(0xFF00E5FF),
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color(0xFF0D0B1A),
    background = Color(0xFF0D0B1A),
    onBackground = Color(0xFFF4F2FF),
    surface = Color(0xFF1A1730),
    onSurface = Color(0xFFF4F2FF),
    surfaceVariant = Color(0xFF221F38),
    onSurfaceVariant = Color(0xFF8B87A8),
    outline = Color(0x28FFFFFF),
    error = Color(0xFFFF5252)
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
