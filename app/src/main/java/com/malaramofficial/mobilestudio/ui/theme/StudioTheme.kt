package com.malaramofficial.mobilestudio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Broadcast Studio OLED Palette
val StudioObsidian = Color(0xFF0C0D11)
val StudioSurface = Color(0xFF14161D)
val StudioSurfaceElevated = Color(0xFF1E212B)
val StudioBorder = Color(0xFF2C303E)

val StudioRed = Color(0xFFFF2A55)
val StudioRedDim = Color(0xFF88122A)
val StudioCyan = Color(0xFF00E5FF)
val StudioGreenLive = Color(0xFF00E676)
val StudioAmberWarn = Color(0xFFFFB300)

val StudioTextPrimary = Color(0xFFF2F4F8)
val StudioTextSecondary = Color(0xFF9CA2B4)
val StudioTextDisabled = Color(0xFF555B6D)

private val StudioColorScheme = darkColorScheme(
    primary = StudioRed,
    onPrimary = Color.White,
    primaryContainer = StudioRedDim,
    onPrimaryContainer = Color.White,
    secondary = StudioCyan,
    onSecondary = Color.Black,
    background = StudioObsidian,
    onBackground = StudioTextPrimary,
    surface = StudioSurface,
    onSurface = StudioTextPrimary,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = StudioTextSecondary,
    outline = StudioBorder
)

@Composable
fun MalaramStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        content = content
    )
}
